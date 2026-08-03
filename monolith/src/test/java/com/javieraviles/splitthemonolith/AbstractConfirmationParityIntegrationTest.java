package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Behaviour the platform must exhibit identically whether trade confirmations are
 * handled in-process or by the extracted confirmation-service. Both subclasses run
 * this same suite with opposite values of the {@code use.confirmation.service} toggle.
 */
@AutoConfigureMockMvc
abstract class AbstractConfirmationParityIntegrationTest {

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	@Autowired
	protected MockMvc mvc;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Autowired
	private RfqRepository rfqRepository;

	/** Arms the confirmation channel with the payload the monolith is expected to deliver. */
	protected abstract void expectConfirmation(String counterpartyName, BigDecimal creditAmount);

	/** Asserts the armed confirmation was delivered. */
	protected abstract void verifyConfirmationSent();

	/** Asserts no confirmation was delivered. */
	protected abstract void verifyNoConfirmationSent();

	@Test
	void executeRfq_deductsNotionalAndCreditAtomically() throws Exception {
		final long cpId = createCounterparty("Fidelity Investments", "549300FIDELITY00001", "20000000.00");
		final long bondId = createBond("US912828ZT09", "50000000.00");

		mvc.perform(post("/rfqs").content(asJsonString(rfqDto(cpId, bondId, "1000000.00", Side.BUY, "998750.00")))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("EXECUTED")));

		assertAvailableCredit(cpId, "19001250.00");
		assertAvailableNotional(bondId, "49000000.00");
		verifyNoConfirmationSent();
	}

	@Test
	void executeRfq_withInsufficientNotional_rejectsAndLeavesBalancesUntouched() throws Exception {
		final long cpId = createCounterparty("BlackRock Fund Advisors", "549300BLACKROCK0001", "30000000.00");
		final long bondId = createBond("US912828AB12", "2000000.00");

		mvc.perform(post("/rfqs").content(asJsonString(rfqDto(cpId, bondId, "5000000.00", Side.BUY, "4993750.00")))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient notional")));

		assertAvailableCredit(cpId, "30000000.00");
		assertAvailableNotional(bondId, "2000000.00");
	}

	@Test
	void executeRfq_withInsufficientCredit_rollsBackTheNotionalDeduction() throws Exception {
		final long cpId = createCounterparty("Small Fund LLC", "549300SMALLFUND001", "500000.00");
		final long bondId = createBond("US912828CD34", "50000000.00");

		mvc.perform(post("/rfqs").content(asJsonString(rfqDto(cpId, bondId, "1000000.00", Side.SELL, "999000.00")))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient credit")));

		assertAvailableCredit(cpId, "500000.00");
		assertAvailableNotional(bondId, "50000000.00");
	}

	@Test
	void executeRfq_withUnknownCounterpartyOrBond_returnsNotFound() throws Exception {
		mvc.perform(post("/rfqs").content(asJsonString(rfqDto(9999L, 1L, "1000000.00", Side.BUY, "998750.00")))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());

		mvc.perform(post("/rfqs").content(asJsonString(rfqDto(1L, 9999L, "1000000.00", Side.BUY, "998750.00")))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void rfqLifecycleStatuses_areExposedUnchanged() throws Exception {
		final Counterparty counterparty = counterpartyRepository
				.save(new Counterparty("Lifecycle Capital", "549300LIFECYCLE001", new BigDecimal("10000000.00")));
		final Bond bond = bondRepository.save(new Bond("US912828LC99", "US Treasury", new BigDecimal("2.0000"),
				LocalDate.of(2035, 6, 30), new BigDecimal("10000000.00")));

		for (final RfqStatus rfqStatus : RfqStatus.values()) {
			final Rfq rfq = rfqRepository.save(new Rfq(counterparty, bond, new BigDecimal("100000.00"),
					Side.BUY, rfqStatus, new BigDecimal("99500.00")));

			mvc.perform(get("/rfqs/" + rfq.getId()).contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status", is(rfqStatus.name())));
		}
	}

	@Test
	void addCredit_sendsTradeConfirmation() throws Exception {
		final long cpId = createCounterparty("Confirmation Partners", "549300CONFIRM00001", "1000000.00");
		expectConfirmation("Confirmation Partners", new BigDecimal("250000.00"));

		mvc.perform(patch("/counterparties/" + cpId).content("{\"amount\":\"250000.00\",\"operation\":\"ADD\"}")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(1250000.00)));

		assertAvailableCredit(cpId, "1250000.00");
		verifyConfirmationSent();
	}

	@Test
	void deductCredit_sendsNoTradeConfirmation() throws Exception {
		final long cpId = createCounterparty("Deduction Partners", "549300DEDUCT000001", "1000000.00");

		mvc.perform(patch("/counterparties/" + cpId).content("{\"amount\":\"250000.00\",\"operation\":\"DEDUCT\"}")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(750000.00)));

		assertAvailableCredit(cpId, "750000.00");
		verifyNoConfirmationSent();
	}

	@Test
	void patchCounterparty_withUnknownOperation_returnsBadRequest() throws Exception {
		final long cpId = createCounterparty("Typo Partners", "549300TYPO00000001", "1000000.00");

		mvc.perform(patch("/counterparties/" + cpId).content("{\"amount\":\"250000.00\",\"operation\":\"CREDIT\"}")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		assertAvailableCredit(cpId, "1000000.00");
		verifyNoConfirmationSent();
	}

	private long createCounterparty(final String name, final String lei, final String creditLimit) throws Exception {
		final MvcResult result = mvc
				.perform(post("/counterparties").content(asJsonString(new Counterparty(name, lei, new BigDecimal(creditLimit))))
						.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return extractId(result);
	}

	private long createBond(final String isin, final String availableNotional) throws Exception {
		final Bond bond = new Bond(isin, "US Treasury", new BigDecimal("3.1250"),
				LocalDate.of(2032, 5, 15), new BigDecimal(availableNotional));
		final MvcResult result = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return extractId(result);
	}

	private void assertAvailableCredit(final long counterpartyId, final String expected) {
		assertEquals(0, new BigDecimal(expected).compareTo(
				counterpartyRepository.findById(counterpartyId).orElseThrow().getAvailableCredit()));
	}

	private void assertAvailableNotional(final long bondId, final String expected) {
		assertEquals(0, new BigDecimal(expected).compareTo(
				bondRepository.findById(bondId).orElseThrow().getAvailableNotional()));
	}

	private static RfqDto rfqDto(final long counterpartyId, final long bondId, final String notionalAmount,
			final Side side, final String executionPrice) {
		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(counterpartyId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal(notionalAmount));
		rfq.setSide(side);
		rfq.setExecutionPrice(new BigDecimal(executionPrice));
		return rfq;
	}

	private static long extractId(final MvcResult result) throws Exception {
		final JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
		return node.get("id").asLong();
	}

	private static String asJsonString(final Object obj) {
		try {
			return MAPPER.writeValueAsString(obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
