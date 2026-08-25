package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javieraviles.splitthemonolith.dto.CounterpartyDto;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Side;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Covers the RFQ execution saga now that bond notional (monolith) and
 * counterparty credit (credit service) live in two different datastores, so
 * there is no transaction spanning both.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class RfqExecutionSagaIntegrationTest {

	@Autowired
	private MockMvc mvc;

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JavaTimeModule());

	private static final CreditServiceStub CREDIT_SERVICE = new CreditServiceStub();

	@BeforeAll
	public static void startCreditService() throws IOException {
		CREDIT_SERVICE.start(CreditServiceStub.PORT);
	}

	@AfterAll
	public static void stopCreditService() {
		CREDIT_SERVICE.stop();
	}

	@Test
	public void whenExecuteRfq_thenNotionalAndRemoteCreditAreBothDeducted() throws Exception {
		final CounterpartyDto cp = CREDIT_SERVICE.addCounterparty("PIMCO Funds", "549300PIMCO00000001",
				new BigDecimal("20000000.00"));
		final long bondId = createBond("US912828SG01", new BigDecimal("50000000.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq(cp.getId(), bondId, new BigDecimal("1000000.00"),
				new BigDecimal("998750.00")))).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("EXECUTED")))
				.andExpect(jsonPath("$.counterpartyId", is((int) cp.getId())));

		assertAmount(new BigDecimal("19001250.00"), CREDIT_SERVICE.getCounterparty(cp.getId()).getAvailableCredit());
		assertEquals("RESERVED", CREDIT_SERVICE.latestReservation().getStatus());
	}

	@Test
	public void whenExecuteRfq_withInsufficientCredit_thenNoNotionalIsDeducted() throws Exception {
		final CounterpartyDto cp = CREDIT_SERVICE.addCounterparty("Tiny Fund LLP", "549300TINYFUND0001",
				new BigDecimal("500000.00"));
		final long bondId = createBond("US912828SG02", new BigDecimal("50000000.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq(cp.getId(), bondId, new BigDecimal("1000000.00"),
				new BigDecimal("999000.00")))).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient credit")));

		assertAmount(new BigDecimal("500000.00"), CREDIT_SERVICE.getCounterparty(cp.getId()).getAvailableCredit());
		assertAmount(new BigDecimal("50000000.00"), availableNotional(bondId));
	}

	/**
	 * The credit reservation is granted remotely and only afterwards does the
	 * local notional deduction fail: without compensation the counterparty would
	 * be left paying for a trade that never happened.
	 */
	@Test
	public void whenNotionalDeductionFails_thenCreditReservationIsReleased() throws Exception {
		final CounterpartyDto cp = CREDIT_SERVICE.addCounterparty("Wellington Management", "549300WELLINGTON001",
				new BigDecimal("30000000.00"));
		final long bondId = createBond("US912828SG03", new BigDecimal("2000000.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq(cp.getId(), bondId, new BigDecimal("5000000.00"),
				new BigDecimal("4993750.00")))).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient notional")));

		assertAmount(new BigDecimal("30000000.00"), CREDIT_SERVICE.getCounterparty(cp.getId()).getAvailableCredit());
		assertEquals("RELEASED", CREDIT_SERVICE.latestReservation().getStatus());
		assertAmount(new BigDecimal("2000000.00"), availableNotional(bondId));
	}

	private RfqDto rfq(final long counterpartyId, final long bondId, final BigDecimal notionalAmount,
			final BigDecimal executionPrice) {
		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(counterpartyId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(notionalAmount);
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(executionPrice);
		return rfq;
	}

	private long createBond(final String isin, final BigDecimal availableNotional) throws Exception {
		final Bond bond = new Bond(isin, "US Treasury", new BigDecimal("3.1250"), LocalDate.of(2032, 5, 15),
				availableNotional);
		final MvcResult result = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return MAPPER.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	private BigDecimal availableNotional(final long bondId) throws Exception {
		final MvcResult result = mvc.perform(get("/bonds/" + bondId).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk()).andReturn();
		final JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
		return node.get("availableNotional").decimalValue();
	}

	private static void assertAmount(final BigDecimal expected, final BigDecimal actual) {
		assertEquals(0, expected.compareTo(actual), "expected " + expected + " but was " + actual);
	}

	private static String asJsonString(final Object obj) {
		try {
			return MAPPER.writeValueAsString(obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
