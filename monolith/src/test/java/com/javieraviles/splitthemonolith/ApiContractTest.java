package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
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

/**
 * Phase 0 API contract tests (see MICROSERVICES_DECOMPOSITION_STRATEGY.md, "Phase 0 — Baseline &
 * guardrails"). Pins the public REST surface of {@code /bonds}, {@code /counterparties} and
 * {@code /rfqs} so responses stay stable as internals are strangled into microservices. These are
 * intentionally additive to {@link IntegrationTest} and follow the same {@code @SpringBootTest +
 * MockMvc + jsonPath} conventions; behavior already covered there (RFQ status codes, missing
 * bond/counterparty) is not duplicated.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ApiContractTest {

	@Autowired
	private MockMvc mvc;

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

	// ---------------------------------------------------------------------------------------------
	// Bond contract
	// ---------------------------------------------------------------------------------------------

	@Test
	public void getBonds_pinsSeedBondShape() throws Exception {
		mvc.perform(get("/bonds").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").isNumber())
				.andExpect(jsonPath("$[0].isin", is("US912828YK15")))
				.andExpect(jsonPath("$[0].issuer", is("US Treasury")))
				.andExpect(jsonPath("$[0].couponRate").exists())
				.andExpect(jsonPath("$[0].maturityDate", is("2030-11-15")))
				.andExpect(jsonPath("$[0].availableNotional").exists());

		final JsonNode seed = getJson("/bonds").get(0);
		assertBigDecimalEquals("2.7500", seed.get("couponRate"));
		assertBigDecimalEquals("100000000.00", seed.get("availableNotional"));
	}

	@Test
	public void postGetPutBond_roundTripsFields() throws Exception {
		final Bond bond = new Bond("US000000BND1", "US Treasury",
				new BigDecimal("1.5000"), LocalDate.of(2035, 6, 30),
				new BigDecimal("7000000.00"));

		final MvcResult created = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.isin", is("US000000BND1")))
				.andExpect(jsonPath("$.issuer", is("US Treasury")))
				.andExpect(jsonPath("$.maturityDate", is("2035-06-30")))
				.andReturn();
		final long id = extractId(created);

		mvc.perform(get("/bonds/" + id).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is((int) id)))
				.andExpect(jsonPath("$.isin", is("US000000BND1")));

		final Bond update = new Bond("US000000BND1", "US Treasury (updated)",
				new BigDecimal("1.7500"), LocalDate.of(2036, 1, 15),
				new BigDecimal("8000000.00"));
		mvc.perform(put("/bonds/" + id).content(asJsonString(update))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.issuer", is("US Treasury (updated)")))
				.andExpect(jsonPath("$.maturityDate", is("2036-01-15")));
		assertBigDecimalEquals("8000000.00", getJson("/bonds/" + id).get("availableNotional"));
	}

	@Test
	public void patchBond_addAndDeductNotional() throws Exception {
		final long id = createBond(new Bond("US000000BND2", "US Treasury",
				new BigDecimal("2.0000"), LocalDate.of(2034, 3, 31),
				new BigDecimal("1000000.00")));

		mvc.perform(patch("/bonds/" + id).content(asJsonString(op("500000.00", "ADD")))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
		assertBigDecimalEquals("1500000.00", getJson("/bonds/" + id).get("availableNotional"));

		mvc.perform(patch("/bonds/" + id).content(asJsonString(op("200000.00", "DEDUCT")))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
		assertBigDecimalEquals("1300000.00", getJson("/bonds/" + id).get("availableNotional"));

		mvc.perform(patch("/bonds/" + id).content(asJsonString(op("1.00", "NONSENSE")))
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("wrong operation"));
	}

	@Test
	public void deleteBond_thenNotFound() throws Exception {
		final long id = createBond(new Bond("US000000BND3", "US Treasury",
				new BigDecimal("2.2500"), LocalDate.of(2037, 9, 30),
				new BigDecimal("500000.00")));

		mvc.perform(delete("/bonds/" + id)).andExpect(status().isOk());
		mvc.perform(get("/bonds/" + id).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	public void getBond_nonExistent_returnsNotFound() throws Exception {
		mvc.perform(get("/bonds/999999").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------------------------------------
	// Counterparty contract
	// ---------------------------------------------------------------------------------------------

	@Test
	public void getCounterparties_pinsSeedCounterpartyShape() throws Exception {
		mvc.perform(get("/counterparties").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").isNumber())
				.andExpect(jsonPath("$[0].name", is("Acme Asset Management")))
				.andExpect(jsonPath("$[0].lei", is("549300EXAMPLE12345678")))
				.andExpect(jsonPath("$[0].creditLimit").exists())
				.andExpect(jsonPath("$[0].availableCredit").exists());

		final JsonNode seed = getJson("/counterparties").get(0);
		assertBigDecimalEquals("50000000.00", seed.get("creditLimit"));
		assertBigDecimalEquals("50000000.00", seed.get("availableCredit"));
	}

	@Test
	public void postCounterparty_defaultsAvailableCreditToLimit() throws Exception {
		final Counterparty cp = new Counterparty("PIMCO Funds",
				"549300PIMCO00000001", new BigDecimal("12000000.00"));

		final MvcResult created = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name", is("PIMCO Funds")))
				.andExpect(jsonPath("$.lei", is("549300PIMCO00000001")))
				.andReturn();
		final long id = extractId(created);

		final JsonNode node = getJson("/counterparties/" + id);
		assertBigDecimalEquals("12000000.00", node.get("creditLimit"));
		assertBigDecimalEquals("12000000.00", node.get("availableCredit"));
	}

	@Test
	public void putCounterparty_updatesFields() throws Exception {
		final long id = createCounterparty(new Counterparty("Vanguard Group",
				"549300VANGUARD00001", new BigDecimal("9000000.00")));

		final Counterparty update = new Counterparty("Vanguard Group Intl",
				"549300VANGUARD00002", new BigDecimal("11000000.00"));
		mvc.perform(put("/counterparties/" + id).content(asJsonString(update))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Vanguard Group Intl")))
				.andExpect(jsonPath("$.lei", is("549300VANGUARD00002")));
		final JsonNode updated = getJson("/counterparties/" + id);
		assertBigDecimalEquals("11000000.00", updated.get("creditLimit"));
		// PUT is a full replace: availableCredit is overwritten from the request body, not preserved.
		assertBigDecimalEquals("11000000.00", updated.get("availableCredit"));
	}

	@Test
	public void patchCounterparty_addAndDeductCredit() throws Exception {
		final long id = createCounterparty(new Counterparty("State Street Global",
				"549300STATESTREET01", new BigDecimal("4000000.00")));

		mvc.perform(patch("/counterparties/" + id).content(asJsonString(op("1000000.00", "ADD")))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
		assertBigDecimalEquals("5000000.00", getJson("/counterparties/" + id).get("availableCredit"));

		mvc.perform(patch("/counterparties/" + id).content(asJsonString(op("2000000.00", "DEDUCT")))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
		assertBigDecimalEquals("3000000.00", getJson("/counterparties/" + id).get("availableCredit"));

		mvc.perform(patch("/counterparties/" + id).content(asJsonString(op("1.00", "NONSENSE")))
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("wrong operation"));
	}

	@Test
	public void deleteCounterparty_thenNotFound() throws Exception {
		final long id = createCounterparty(new Counterparty("Wellington Mgmt",
				"549300WELLINGTON001", new BigDecimal("2000000.00")));

		mvc.perform(delete("/counterparties/" + id)).andExpect(status().isOk());
		mvc.perform(get("/counterparties/" + id).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	public void getCounterparty_nonExistent_returnsNotFound() throws Exception {
		mvc.perform(get("/counterparties/999999").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------------------------------------
	// RFQ contract — ID-based DTO, execution happy path and atomicity of failed executions
	// ---------------------------------------------------------------------------------------------

	@Test
	public void getRfqs_isIdBasedNotNestedObjectGraph() throws Exception {
		mvc.perform(get("/rfqs").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				// RfqDto exposes scalar IDs ...
				.andExpect(jsonPath("$[0].counterpartyId").isNumber())
				.andExpect(jsonPath("$[0].bondId").isNumber())
				// ... and must NOT leak the nested Bond/Counterparty object graph.
				.andExpect(jsonPath("$[0].counterparty").doesNotExist())
				.andExpect(jsonPath("$[0].bond").doesNotExist())
				.andExpect(jsonPath("$[0].notionalAmount").exists())
				.andExpect(jsonPath("$[0].side").exists())
				.andExpect(jsonPath("$[0].status").exists())
				.andExpect(jsonPath("$[0].executionPrice").exists())
				.andExpect(jsonPath("$[0].createdAt").exists());
	}

	@Test
	public void getSeedRfq_pinsShapeAndValues() throws Exception {
		// IDs come from a shared Hibernate sequence, so derive the seed IDs rather than hardcoding.
		final long seedCpId = getJson("/counterparties").get(0).get("id").asLong();
		final long seedBondId = getJson("/bonds").get(0).get("id").asLong();

		final JsonNode seed = getJson("/rfqs").get(0);
		assertEquals(seedCpId, seed.get("counterpartyId").asLong(), "seed RFQ counterpartyId");
		assertEquals(seedBondId, seed.get("bondId").asLong(), "seed RFQ bondId");
		assertEquals("BUY", seed.get("side").asText());
		assertEquals("EXECUTED", seed.get("status").asText());
		assertFalse(seed.has("counterparty"), "RFQ DTO must not nest the Counterparty object graph");
		assertFalse(seed.has("bond"), "RFQ DTO must not nest the Bond object graph");
		assertBigDecimalEquals("5000000.00", seed.get("notionalAmount"));
		assertBigDecimalEquals("4987500.00", seed.get("executionPrice"));
	}

	@Test
	public void postRfq_happyPath_returnsCreatedAndDeductsInventoryAndCredit() throws Exception {
		final long cpId = createCounterparty(new Counterparty("JPMorgan AM",
				"549300JPMORGAN00001", new BigDecimal("20000000.00")));
		final long bondId = createBond(new Bond("US111111RFQ1", "US Treasury",
				new BigDecimal("2.5000"), LocalDate.of(2031, 4, 30),
				new BigDecimal("50000000.00")));

		final BigDecimal notional = new BigDecimal("1000000.00");
		final BigDecimal price = new BigDecimal("998750.00");

		mvc.perform(post("/rfqs").content(asJsonString(rfq(cpId, bondId, notional, Side.BUY, price)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.status", is("EXECUTED")))
				.andExpect(jsonPath("$.counterpartyId", is((int) cpId)))
				.andExpect(jsonPath("$.bondId", is((int) bondId)))
				.andExpect(jsonPath("$.counterparty").doesNotExist())
				.andExpect(jsonPath("$.bond").doesNotExist());

		// available notional reduced by traded notional; available credit reduced by settlement price
		assertBigDecimalEquals("49000000.00", getJson("/bonds/" + bondId).get("availableNotional"));
		assertBigDecimalEquals("19001250.00", getJson("/counterparties/" + cpId).get("availableCredit"));
	}

	@Test
	public void postRfq_insufficientNotional_isAtomic() throws Exception {
		final long cpId = createCounterparty(new Counterparty("Insufficient Notional CP",
				"549300INSUFNOTION01", new BigDecimal("30000000.00")));
		final long bondId = createBond(new Bond("US222222RFQ2", "US Treasury",
				new BigDecimal("2.5000"), LocalDate.of(2031, 8, 15),
				new BigDecimal("2000000.00")));

		final BigDecimal notionalBefore = getJson("/bonds/" + bondId).get("availableNotional").decimalValue();
		final BigDecimal creditBefore = getJson("/counterparties/" + cpId).get("availableCredit").decimalValue();

		mvc.perform(post("/rfqs")
				.content(asJsonString(rfq(cpId, bondId, new BigDecimal("5000000.00"),
						Side.BUY, new BigDecimal("4993750.00"))))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient notional")));

		// atomicity: a failed execution must not mutate bond notional or counterparty credit
		assertEquals(0, notionalBefore.compareTo(getJson("/bonds/" + bondId).get("availableNotional").decimalValue()),
				"bond notional changed after failed RFQ");
		assertEquals(0, creditBefore.compareTo(getJson("/counterparties/" + cpId).get("availableCredit").decimalValue()),
				"counterparty credit changed after failed RFQ");
	}

	@Test
	public void postRfq_insufficientCredit_isAtomic() throws Exception {
		final long cpId = createCounterparty(new Counterparty("Insufficient Credit CP",
				"549300INSUFCREDIT01", new BigDecimal("500000.00")));
		final long bondId = createBond(new Bond("US333333RFQ3", "US Treasury",
				new BigDecimal("3.0000"), LocalDate.of(2033, 2, 15),
				new BigDecimal("50000000.00")));

		final BigDecimal notionalBefore = getJson("/bonds/" + bondId).get("availableNotional").decimalValue();
		final BigDecimal creditBefore = getJson("/counterparties/" + cpId).get("availableCredit").decimalValue();

		mvc.perform(post("/rfqs")
				.content(asJsonString(rfq(cpId, bondId, new BigDecimal("1000000.00"),
						Side.BUY, new BigDecimal("999000.00"))))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient credit")));

		// atomicity: notional was sufficient and would have been deducted, but the failed credit
		// step must roll the whole transaction back, leaving both aggregates untouched.
		assertEquals(0, notionalBefore.compareTo(getJson("/bonds/" + bondId).get("availableNotional").decimalValue()),
				"bond notional changed after failed RFQ");
		assertEquals(0, creditBefore.compareTo(getJson("/counterparties/" + cpId).get("availableCredit").decimalValue()),
				"counterparty credit changed after failed RFQ");
	}

	@Test
	public void deleteRfq_thenNotFound() throws Exception {
		final long cpId = createCounterparty(new Counterparty("Delete RFQ CP",
				"549300DELETERFQ001", new BigDecimal("20000000.00")));
		final long bondId = createBond(new Bond("US444444RFQ4", "US Treasury",
				new BigDecimal("2.5000"), LocalDate.of(2031, 4, 30),
				new BigDecimal("50000000.00")));

		final MvcResult created = mvc.perform(post("/rfqs")
				.content(asJsonString(rfq(cpId, bondId, new BigDecimal("1000000.00"),
						Side.BUY, new BigDecimal("998750.00"))))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		final long rfqId = extractId(created);

		mvc.perform(get("/rfqs/" + rfqId).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
		mvc.perform(delete("/rfqs/" + rfqId)).andExpect(status().isOk());
		mvc.perform(get("/rfqs/" + rfqId).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	public void getRfq_nonExistent_returnsNotFound() throws Exception {
		mvc.perform(get("/rfqs/999999").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------------------------------------
	// helpers
	// ---------------------------------------------------------------------------------------------

	private long createBond(final Bond bond) throws Exception {
		return extractId(mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn());
	}

	private long createCounterparty(final Counterparty cp) throws Exception {
		return extractId(mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn());
	}

	private JsonNode getJson(final String url) throws Exception {
		final MvcResult result = mvc.perform(get(url).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk()).andReturn();
		return MAPPER.readTree(result.getResponse().getContentAsString());
	}

	private static RfqDto rfq(final long cpId, final long bondId, final BigDecimal notional,
			final Side side, final BigDecimal price) {
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(cpId);
		dto.setBondId(bondId);
		dto.setNotionalAmount(notional);
		dto.setSide(side);
		dto.setExecutionPrice(price);
		return dto;
	}

	private static Map<String, String> op(final String amount, final String operation) {
		final Map<String, String> map = new HashMap<>();
		map.put("amount", amount);
		map.put("operation", operation);
		return map;
	}

	private static void assertBigDecimalEquals(final String expected, final JsonNode actual) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()),
				"expected " + expected + " but was " + actual.asText());
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
