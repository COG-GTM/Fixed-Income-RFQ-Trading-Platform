package com.javieraviles.splitthemonolith.characterization;

import static com.javieraviles.splitthemonolith.characterization.CharacterizationSupport.asJson;
import static com.javieraviles.splitthemonolith.characterization.CharacterizationSupport.bodyOf;
import static com.javieraviles.splitthemonolith.characterization.CharacterizationSupport.createBond;
import static com.javieraviles.splitthemonolith.characterization.CharacterizationSupport.createCounterparty;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Characterization ("golden master") tests for the RFQ execution saga.
 *
 * They document the behavior of {@code POST /rfqs} as it exists today —
 * including its quirks — so that the trade confirmation extraction, and any
 * later strangler step, can be validated as behavior preserving. Nothing here
 * asserts what the system <em>should</em> do; it asserts what it does.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class RfqExecutionSagaCharacterizationTest {

	@Autowired
	private MockMvc mvc;

	@Test
	@DisplayName("PENDING is never persisted: the saga moves an RFQ straight to EXECUTED in one call")
	public void executeRfq_movesPendingStraightToExecuted() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Pin Executed Fund",
				"549300PINEXECUTED001", "20000000.00");
		final long bondId = createBond(mvc, "US912828PN01", "50000000.00");

		final JsonNode created = bodyOf(mvc
				.perform(post("/rfqs").content(asJson(rfq(counterpartyId, bondId, "1000000.00",
						Side.BUY, "998750.00", RfqStatus.PENDING)))
						.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", org.hamcrest.Matchers.is("EXECUTED")))
				.andReturn());

		assertTrue(created.get("id").asLong() > 0, "an id is assigned on execution");
		assertNotNull(created.get("createdAt"), "createdAt is stamped by @PrePersist");
		assertEquals("BUY", created.get("side").asText());

		mvc.perform(get("/rfqs/" + created.get("id").asLong()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", org.hamcrest.Matchers.is("EXECUTED")));
	}

	@Test
	@DisplayName("A QUOTED status supplied by the client is ignored; the saga always writes EXECUTED")
	public void executeRfq_ignoresClientSuppliedStatus() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Pin Quoted Fund",
				"549300PINQUOTED00001", "20000000.00");
		final long bondId = createBond(mvc, "US912828QT02", "50000000.00");

		mvc.perform(post("/rfqs").content(asJson(rfq(counterpartyId, bondId, "1000000.00",
				Side.BUY, "998750.00", RfqStatus.QUOTED)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", org.hamcrest.Matchers.is("EXECUTED")));
	}

	@Test
	@DisplayName("Execution deducts notional from the bond and the execution price from the counterparty credit")
	public void executeRfq_adjustsNotionalAndCredit() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Pin Adjustment Fund",
				"549300PINADJUST00001", "20000000.00");
		final long bondId = createBond(mvc, "US912828AJ03", "50000000.00");

		mvc.perform(post("/rfqs").content(asJson(rfq(counterpartyId, bondId, "1000000.00",
				Side.BUY, "998750.00", null)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated());

		assertAvailableNotional(bondId, "49000000.00");
		assertAvailableCredit(counterpartyId, "19001250.00");
		assertCreditLimit(counterpartyId, "20000000.00");
	}

	@Test
	@DisplayName("SELL is treated exactly like BUY: inventory and credit are both deducted")
	public void executeRfq_sellSideDeductsLikeBuySide() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Pin Sell Side Fund",
				"549300PINSELLSIDE001", "20000000.00");
		final long bondId = createBond(mvc, "US912828SL04", "50000000.00");

		mvc.perform(post("/rfqs").content(asJson(rfq(counterpartyId, bondId, "2000000.00",
				Side.SELL, "1997500.00", null)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.side", org.hamcrest.Matchers.is("SELL")));

		assertAvailableNotional(bondId, "48000000.00");
		assertAvailableCredit(counterpartyId, "18002500.00");
	}

	@Test
	@DisplayName("Credit-limit enforcement: an execution price above available credit is rejected with 400")
	public void executeRfq_enforcesCreditLimit() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Pin Credit Limit Fund",
				"549300PINCREDIT00001", "500000.00");
		final long bondId = createBond(mvc, "US912828CL05", "50000000.00");
		final int rfqCountBefore = rfqCount();

		mvc.perform(post("/rfqs").content(asJson(rfq(counterpartyId, bondId, "1000000.00",
				Side.BUY, "999000.00", null)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient credit")));

		assertEquals(rfqCountBefore, rfqCount(), "no REJECTED RFQ is persisted for a failed execution");
		assertAvailableNotional(bondId, "50000000.00");
		assertAvailableCredit(counterpartyId, "500000.00");
	}

	@Test
	@DisplayName("Credit-limit enforcement is inclusive: spending exactly the available credit succeeds")
	public void executeRfq_allowsSpendingExactlyTheAvailableCredit() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Pin Exact Credit Fund",
				"549300PINEXACT000001", "1000000.00");
		final long bondId = createBond(mvc, "US912828EX06", "1000000.00");

		mvc.perform(post("/rfqs").content(asJson(rfq(counterpartyId, bondId, "1000000.00",
				Side.BUY, "1000000.00", null)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", org.hamcrest.Matchers.is("EXECUTED")));

		assertAvailableNotional(bondId, "0.00");
		assertAvailableCredit(counterpartyId, "0.00");
	}

	@Test
	@DisplayName("Insufficient bond notional is rejected with 400 and leaves credit untouched")
	public void executeRfq_enforcesAvailableNotional() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Pin Notional Fund",
				"549300PINNOTIONAL001", "30000000.00");
		final long bondId = createBond(mvc, "US912828NT07", "2000000.00");
		final int rfqCountBefore = rfqCount();

		mvc.perform(post("/rfqs").content(asJson(rfq(counterpartyId, bondId, "5000000.00",
				Side.BUY, "4993750.00", null)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient notional")));

		assertEquals(rfqCountBefore, rfqCount(), "no RFQ is persisted when the bond lacks notional");
		assertAvailableNotional(bondId, "2000000.00");
		assertAvailableCredit(counterpartyId, "30000000.00");
	}

	@Test
	@DisplayName("The whole saga rolls back: a credit failure undoes the notional already deducted")
	public void executeRfq_rollsBackNotionalWhenCreditFails() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Pin Rollback Fund",
				"549300PINROLLBACK001", "1000000.00");
		final long bondId = createBond(mvc, "US912828RB08", "50000000.00");

		mvc.perform(post("/rfqs").content(asJson(rfq(counterpartyId, bondId, "10000000.00",
				Side.BUY, "9987500.00", null)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		assertAvailableNotional(bondId, "50000000.00");
		assertAvailableCredit(counterpartyId, "1000000.00");
	}

	@Test
	@DisplayName("Unknown bond or counterparty yields 404 and persists nothing")
	public void executeRfq_returnsNotFoundForUnknownReferences() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Pin Missing Ref Fund",
				"549300PINMISSING0001", "20000000.00");
		final long bondId = createBond(mvc, "US912828MR09", "50000000.00");
		final int rfqCountBefore = rfqCount();

		mvc.perform(post("/rfqs").content(asJson(rfq(counterpartyId, 999999L, "1000000.00",
				Side.BUY, "998750.00", null)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());

		mvc.perform(post("/rfqs").content(asJson(rfq(999999L, bondId, "1000000.00",
				Side.BUY, "998750.00", null)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());

		assertEquals(rfqCountBefore, rfqCount());
		assertAvailableNotional(bondId, "50000000.00");
		assertAvailableCredit(counterpartyId, "20000000.00");
	}

	private static RfqDto rfq(final long counterpartyId, final long bondId, final String notionalAmount,
			final Side side, final String executionPrice, final RfqStatus status) {
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(counterpartyId);
		dto.setBondId(bondId);
		dto.setNotionalAmount(new BigDecimal(notionalAmount));
		dto.setSide(side);
		dto.setExecutionPrice(new BigDecimal(executionPrice));
		dto.setStatus(status);
		return dto;
	}

	private int rfqCount() throws Exception {
		return bodyOf(mvc.perform(get("/rfqs")).andExpect(status().isOk()).andReturn()).size();
	}

	private void assertAvailableNotional(final long bondId, final String expected) throws Exception {
		final JsonNode bond = bodyOf(mvc.perform(get("/bonds/" + bondId))
				.andExpect(status().isOk()).andReturn());
		assertAmount(expected, bond.get("availableNotional").decimalValue(), "availableNotional");
	}

	private void assertAvailableCredit(final long counterpartyId, final String expected) throws Exception {
		assertAmount(expected, counterparty(counterpartyId).get("availableCredit").decimalValue(),
				"availableCredit");
	}

	private void assertCreditLimit(final long counterpartyId, final String expected) throws Exception {
		assertAmount(expected, counterparty(counterpartyId).get("creditLimit").decimalValue(), "creditLimit");
	}

	private JsonNode counterparty(final long counterpartyId) throws Exception {
		return bodyOf(mvc.perform(get("/counterparties/" + counterpartyId))
				.andExpect(status().isOk()).andReturn());
	}

	private static void assertAmount(final String expected, final BigDecimal actual, final String field) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual),
				() -> field + " expected " + expected + " but was " + actual);
	}
}
