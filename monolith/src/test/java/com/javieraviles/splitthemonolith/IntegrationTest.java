package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Side;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
public class IntegrationTest {

	@Autowired
	private MockMvc mvc;

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JavaTimeModule());

	@Test
	public void givenOneCounterparty_whenGetCounterparties_thenReturnJsonArray() throws Exception {
		mvc.perform(get("/counterparties").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name", is("Acme Asset Management")));
	}

	@Test
	public void givenOneBond_whenGetBonds_thenReturnJsonArray() throws Exception {
		mvc.perform(get("/bonds").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].isin", is("US912828YK15")));
	}

	@Test
	public void whenExecuteRfq_thenReturnCreated() throws Exception {
		final Counterparty cp = new Counterparty("Fidelity Investments",
				"549300FIDELITY00001", new BigDecimal("20000000.00"));
		final Bond bond = new Bond("US912828ZT09", "US Treasury",
				new BigDecimal("3.1250"), LocalDate.of(2032, 5, 15),
				new BigDecimal("50000000.00"));

		MvcResult resultCp = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("998750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("EXECUTED")));
	}

	@Test
	public void whenExecuteRfq_withInsufficientNotional_thenReturnBadRequest() throws Exception {
		final Counterparty cp = new Counterparty("BlackRock Fund Advisors",
				"549300BLACKROCK0001", new BigDecimal("30000000.00"));
		final Bond bond = new Bond("US912828AB12", "US Treasury",
				new BigDecimal("2.5000"), LocalDate.of(2031, 8, 15),
				new BigDecimal("2000000.00"));

		MvcResult resultCp = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal("5000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("4993750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient notional")));
	}

	@Test
	public void whenExecuteRfq_withInsufficientCredit_thenReturnBadRequest() throws Exception {
		final Counterparty cp = new Counterparty("Small Fund LLC",
				"549300SMALLFUND001", new BigDecimal("500000.00"));

		final Bond bond = new Bond("US912828CD34", "US Treasury",
				new BigDecimal("3.0000"), LocalDate.of(2033, 2, 15),
				new BigDecimal("50000000.00"));

		MvcResult resultCp = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("999000.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient credit")));
	}

	@Test
	public void whenExecuteSellRfq_thenReturnNotionalToInventoryAndReleaseCredit() throws Exception {
		final Map<Entity, Long> ids = createCounterpartyAndBond(
				new Counterparty("Northbridge Capital", "549300NORTHBRIDGE1",
						new BigDecimal("10000000.00")),
				new Bond("US912828EF56", "US Treasury", new BigDecimal("2.8750"),
						LocalDate.of(2030, 6, 30), new BigDecimal("5000000.00")));

		executeRfq(ids, new BigDecimal("1000000.00"), Side.BUY, new BigDecimal("2000000.00"))
				.andExpect(status().isCreated());

		executeRfq(ids, new BigDecimal("1000000.00"), Side.SELL, new BigDecimal("2000000.00"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.side", is("SELL")));

		assertAmount("5000000.00", availableNotional(ids.get(Entity.BOND)));
		assertAmount("10000000.00", availableCredit(ids.get(Entity.COUNTERPARTY)));
	}

	@Test
	public void whenExecuteSellRfq_thenCreditIsNotReleasedAboveTheApprovedLimit() throws Exception {
		final Map<Entity, Long> ids = createCounterpartyAndBond(
				new Counterparty("Harbour Point Advisors", "549300HARBOURPOINT1",
						new BigDecimal("1000000.00")),
				new Bond("US912828GH78", "US Treasury", new BigDecimal("3.5000"),
						LocalDate.of(2034, 1, 31), new BigDecimal("1000000.00")));

		executeRfq(ids, new BigDecimal("2000000.00"), Side.SELL, new BigDecimal("1980000.00"))
				.andExpect(status().isCreated());

		assertAmount("3000000.00", availableNotional(ids.get(Entity.BOND)));
		assertAmount("1000000.00", availableCredit(ids.get(Entity.COUNTERPARTY)));
	}

	@Test
	public void whenExecuteRfq_withNegativeNotional_thenRejectAndLeaveInventoryUntouched() throws Exception {
		final Map<Entity, Long> ids = createCounterpartyAndBond(
				new Counterparty("Cedar Lane Partners", "549300CEDARLANE0001",
						new BigDecimal("5000000.00")),
				new Bond("US912828IJ90", "US Treasury", new BigDecimal("2.1250"),
						LocalDate.of(2029, 9, 30), new BigDecimal("1000000.00")));

		executeRfq(ids, new BigDecimal("-1000000.00"), Side.BUY, new BigDecimal("990000.00"))
				.andExpect(status().isBadRequest());

		assertAmount("1000000.00", availableNotional(ids.get(Entity.BOND)));
		assertAmount("5000000.00", availableCredit(ids.get(Entity.COUNTERPARTY)));
	}

	@Test
	public void whenExecuteRfq_withNegativeExecutionPrice_thenRejectAndLeaveCreditUntouched() throws Exception {
		final Map<Entity, Long> ids = createCounterpartyAndBond(
				new Counterparty("Westfall Income Fund", "549300WESTFALL0001",
						new BigDecimal("5000000.00")),
				new Bond("US912828KL12", "US Treasury", new BigDecimal("4.0000"),
						LocalDate.of(2035, 3, 31), new BigDecimal("9000000.00")));

		executeRfq(ids, new BigDecimal("1000000.00"), Side.BUY, new BigDecimal("-990000.00"))
				.andExpect(status().isBadRequest());

		assertAmount("9000000.00", availableNotional(ids.get(Entity.BOND)));
		assertAmount("5000000.00", availableCredit(ids.get(Entity.COUNTERPARTY)));
	}

	@Test
	public void whenExecuteRfq_withSubCentSettlementAmount_thenCreditMatchesRecordedTrade() throws Exception {
		final Map<Entity, Long> ids = createCounterpartyAndBond(
				new Counterparty("Ravenswood Bond Fund", "549300RAVENSWOOD01",
						new BigDecimal("20000000.00")),
				new Bond("US912828MN34", "US Treasury", new BigDecimal("3.2500"),
						LocalDate.of(2033, 7, 31), new BigDecimal("50000000.00")));

		final MvcResult result = executeRfq(ids, new BigDecimal("1000000.00"), Side.BUY,
				new BigDecimal("1000000.005")).andExpect(status().isCreated()).andReturn();

		assertAmount("1000000.01", decimalField(result, "executionPrice"));
		assertAmount("18999999.99", availableCredit(ids.get(Entity.COUNTERPARTY)));
	}

	@Test
	public void whenExecuteSellRfq_withoutApprovedCreditLimit_thenCreditIsNotGrantedByTheSell() throws Exception {
		final MvcResult resultCp = mvc.perform(post("/counterparties")
				.content("{\"name\":\"Eastgate Credit Fund\",\"lei\":\"549300EASTGATE0001\","
						+ "\"availableCredit\":100.00}")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		final MvcResult resultBond = mvc.perform(post("/bonds")
				.content(asJsonString(new Bond("US912828OP56", "US Treasury", new BigDecimal("1.7500"),
						LocalDate.of(2031, 5, 31), new BigDecimal("1000000.00"))))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final Map<Entity, Long> ids = new EnumMap<>(Entity.class);
		ids.put(Entity.COUNTERPARTY, extractId(resultCp));
		ids.put(Entity.BOND, extractId(resultBond));

		executeRfq(ids, new BigDecimal("500000.00"), Side.SELL, new BigDecimal("50.00"))
				.andExpect(status().isCreated());

		assertAmount("1500000.00", availableNotional(ids.get(Entity.BOND)));
		assertAmount("100.00", availableCredit(ids.get(Entity.COUNTERPARTY)));
	}

	private enum Entity {
		COUNTERPARTY, BOND
	}

	private Map<Entity, Long> createCounterpartyAndBond(final Counterparty cp, final Bond bond) throws Exception {
		final MvcResult resultCp = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		final MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final Map<Entity, Long> ids = new EnumMap<>(Entity.class);
		ids.put(Entity.COUNTERPARTY, extractId(resultCp));
		ids.put(Entity.BOND, extractId(resultBond));
		return ids;
	}

	private ResultActions executeRfq(final Map<Entity, Long> ids, final BigDecimal notionalAmount,
			final Side side, final BigDecimal executionPrice) throws Exception {
		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(ids.get(Entity.COUNTERPARTY));
		rfq.setBondId(ids.get(Entity.BOND));
		rfq.setNotionalAmount(notionalAmount);
		rfq.setSide(side);
		rfq.setExecutionPrice(executionPrice);
		return mvc.perform(post("/rfqs").content(asJsonString(rfq))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON));
	}

	private BigDecimal availableCredit(final long counterpartyId) throws Exception {
		return decimalField(mvc.perform(get("/counterparties/" + counterpartyId)
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn(),
				"availableCredit");
	}

	private BigDecimal availableNotional(final long bondId) throws Exception {
		return decimalField(mvc.perform(get("/bonds/" + bondId)
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn(),
				"availableNotional");
	}

	private static BigDecimal decimalField(final MvcResult result, final String field) throws Exception {
		final JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
		return node.get(field).decimalValue();
	}

	private static void assertAmount(final String expected, final BigDecimal actual) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual),
				() -> "expected " + expected + " but was " + actual);
	}

	@Test
	public void whenExecuteRfq_withNonExistentCounterparty_thenReturnNotFound() throws Exception {
		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(9999L);
		rfq.setBondId(1L);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("998750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
	}

	@Test
	public void whenExecuteRfq_withNonExistentBond_thenReturnNotFound() throws Exception {
		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(1L);
		rfq.setBondId(9999L);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("998750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
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
