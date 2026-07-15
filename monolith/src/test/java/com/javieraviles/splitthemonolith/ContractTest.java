package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.javieraviles.splitthemonolith.entity.Side;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contract tests that pin the current request/response JSON shapes of every
 * capability's REST API (Counterparty, Bond, RFQ, and the PATCH credit/notional
 * operations). These are intentionally self-contained: they do NOT depend on any
 * OpenAPI spec file. If a future refactor changes the wire contract of an
 * endpoint (renames a field, drops a field, changes a type or status code) one
 * of these assertions will fail.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ContractTest {

	@Autowired
	private MockMvc mvc;

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	// ---------------------------------------------------------------------
	// Counterparty capability
	// ---------------------------------------------------------------------

	@Test
	public void counterpartyCreate_pinsResponseContract() throws Exception {
		final Counterparty cp = new Counterparty("Vanguard Group", "549300VANGUARD0001",
				new BigDecimal("10000000.00"));

		final MvcResult result = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.name", is("Vanguard Group")))
				.andExpect(jsonPath("$.lei", is("549300VANGUARD0001")))
				.andExpect(jsonPath("$.creditLimit").exists())
				.andExpect(jsonPath("$.availableCredit").exists())
				.andReturn();
		// availableCredit defaults to creditLimit on create (see @PrePersist)
		assertDecimalEquals("10000000.00", result, "creditLimit");
		assertDecimalEquals("10000000.00", result, "availableCredit");
	}

	@Test
	public void counterpartyGetOne_pinsResponseContract() throws Exception {
		final long id = createCounterparty("PIMCO", "549300PIMCO0000001", "7500000.00");

		mvc.perform(get("/counterparties/" + id).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id", is((int) id)))
				.andExpect(jsonPath("$.name", is("PIMCO")))
				.andExpect(jsonPath("$.lei", is("549300PIMCO0000001")))
				.andExpect(jsonPath("$.creditLimit").exists())
				.andExpect(jsonPath("$.availableCredit").exists())
				.andExpect(jsonPath("$", hasKey("id")))
				.andExpect(jsonPath("$", hasKey("name")))
				.andExpect(jsonPath("$", hasKey("lei")))
				.andExpect(jsonPath("$", hasKey("creditLimit")))
				.andExpect(jsonPath("$", hasKey("availableCredit")));
	}

	@Test
	public void counterpartyGetAll_pinsResponseContract() throws Exception {
		createCounterparty("State Street", "549300STATEST00001", "6000000.00");

		mvc.perform(get("/counterparties").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$[0].id").exists())
				.andExpect(jsonPath("$[0].name").exists())
				.andExpect(jsonPath("$[0].lei").exists())
				.andExpect(jsonPath("$[0].creditLimit").exists())
				.andExpect(jsonPath("$[0].availableCredit").exists());
	}

	@Test
	public void counterpartyPatchCredit_pinsAddAndDeductContract() throws Exception {
		final long id = createCounterparty("Wellington", "549300WELLINGT0001", "1000000.00");

		final MvcResult added = mvc.perform(patch("/counterparties/" + id)
				.content(operationBody("500000.00", "ADD"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id", is((int) id)))
				.andExpect(jsonPath("$.availableCredit").exists())
				.andReturn();
		assertDecimalEquals("1500000.00", added, "availableCredit");

		final MvcResult deducted = mvc.perform(patch("/counterparties/" + id)
				.content(operationBody("200000.00", "DEDUCT"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit").exists())
				.andReturn();
		assertDecimalEquals("1300000.00", deducted, "availableCredit");
	}

	@Test
	public void counterpartyPatchCredit_withInvalidOperation_pinsBadRequestContract() throws Exception {
		final long id = createCounterparty("Loomis", "549300LOOMIS000001", "1000000.00");

		mvc.perform(patch("/counterparties/" + id)
				.content(operationBody("100000.00", "MULTIPLY"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
	}

	// ---------------------------------------------------------------------
	// Bond capability
	// ---------------------------------------------------------------------

	@Test
	public void bondCreate_pinsResponseContract() throws Exception {
		final Bond bond = new Bond("US912828MC01", "US Treasury", new BigDecimal("2.7500"),
				LocalDate.of(2030, 11, 15), new BigDecimal("100000000.00"));

		mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.isin", is("US912828MC01")))
				.andExpect(jsonPath("$.issuer", is("US Treasury")))
				.andExpect(jsonPath("$.couponRate").exists())
				.andExpect(jsonPath("$.maturityDate", is("2030-11-15")))
				.andExpect(jsonPath("$.availableNotional").exists());
	}

	@Test
	public void bondGetOne_pinsResponseContract() throws Exception {
		final long id = createBond("US912828MD02", "US Treasury", "3.0000", "2031-06-30", "80000000.00");

		mvc.perform(get("/bonds/" + id).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id", is((int) id)))
				.andExpect(jsonPath("$.isin", is("US912828MD02")))
				.andExpect(jsonPath("$.issuer", is("US Treasury")))
				.andExpect(jsonPath("$.maturityDate", is("2031-06-30")))
				.andExpect(jsonPath("$", hasKey("id")))
				.andExpect(jsonPath("$", hasKey("isin")))
				.andExpect(jsonPath("$", hasKey("issuer")))
				.andExpect(jsonPath("$", hasKey("couponRate")))
				.andExpect(jsonPath("$", hasKey("maturityDate")))
				.andExpect(jsonPath("$", hasKey("availableNotional")));
	}

	@Test
	public void bondGetAll_pinsResponseContract() throws Exception {
		createBond("US912828ME03", "US Treasury", "2.2500", "2029-09-30", "40000000.00");

		mvc.perform(get("/bonds").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$[0].id").exists())
				.andExpect(jsonPath("$[0].isin").exists())
				.andExpect(jsonPath("$[0].issuer").exists())
				.andExpect(jsonPath("$[0].couponRate").exists())
				.andExpect(jsonPath("$[0].maturityDate").exists())
				.andExpect(jsonPath("$[0].availableNotional").exists());
	}

	@Test
	public void bondPatchNotional_pinsAddAndDeductContract() throws Exception {
		final long id = createBond("US912828MF04", "US Treasury", "2.5000", "2032-03-31", "10000000.00");

		final MvcResult added = mvc.perform(patch("/bonds/" + id)
				.content(operationBody("2500000.00", "ADD"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id", is((int) id)))
				.andExpect(jsonPath("$.availableNotional").exists())
				.andReturn();
		assertDecimalEquals("12500000.00", added, "availableNotional");

		final MvcResult deducted = mvc.perform(patch("/bonds/" + id)
				.content(operationBody("1000000.00", "DEDUCT"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional").exists())
				.andReturn();
		assertDecimalEquals("11500000.00", deducted, "availableNotional");
	}

	@Test
	public void bondPatchNotional_withInvalidOperation_pinsBadRequestContract() throws Exception {
		final long id = createBond("US912828MG05", "US Treasury", "2.5000", "2032-03-31", "10000000.00");

		mvc.perform(patch("/bonds/" + id)
				.content(operationBody("1000000.00", "SUBTRACT"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
	}

	// ---------------------------------------------------------------------
	// RFQ capability
	// ---------------------------------------------------------------------

	@Test
	public void rfqExecute_pinsResponseContract() throws Exception {
		final long cpId = createCounterparty("Fidelity RFQ", "549300FIDRFQ000001", "20000000.00");
		final long bondId = createBond("US912828MH06", "US Treasury", "3.1250", "2032-05-15", "50000000.00");

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("998750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.counterpartyId", is((int) cpId)))
				.andExpect(jsonPath("$.bondId", is((int) bondId)))
				.andExpect(jsonPath("$.notionalAmount").exists())
				.andExpect(jsonPath("$.side", is("BUY")))
				.andExpect(jsonPath("$.status", is("EXECUTED")))
				.andExpect(jsonPath("$.executionPrice").exists())
				.andExpect(jsonPath("$.createdAt").exists());
	}

	@Test
	public void rfqGetOne_pinsResponseContract() throws Exception {
		final long cpId = createCounterparty("Nuveen RFQ", "549300NUVEENRFQ001", "20000000.00");
		final long bondId = createBond("US912828MI07", "US Treasury", "3.1250", "2032-05-15", "50000000.00");
		final long rfqId = executeRfq(cpId, bondId, "1000000.00", Side.SELL, "998750.00");

		mvc.perform(get("/rfqs/" + rfqId).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id", is((int) rfqId)))
				.andExpect(jsonPath("$.counterpartyId", is((int) cpId)))
				.andExpect(jsonPath("$.bondId", is((int) bondId)))
				.andExpect(jsonPath("$.side", is("SELL")))
				.andExpect(jsonPath("$.status", is("EXECUTED")))
				.andExpect(jsonPath("$", hasKey("id")))
				.andExpect(jsonPath("$", hasKey("counterpartyId")))
				.andExpect(jsonPath("$", hasKey("bondId")))
				.andExpect(jsonPath("$", hasKey("notionalAmount")))
				.andExpect(jsonPath("$", hasKey("side")))
				.andExpect(jsonPath("$", hasKey("status")))
				.andExpect(jsonPath("$", hasKey("executionPrice")))
				.andExpect(jsonPath("$", hasKey("createdAt")));
	}

	@Test
	public void rfqGetAll_pinsResponseContract() throws Exception {
		final long cpId = createCounterparty("Schroders RFQ", "549300SCHRODRFQ01", "20000000.00");
		final long bondId = createBond("US912828MJ08", "US Treasury", "3.1250", "2032-05-15", "50000000.00");
		executeRfq(cpId, bondId, "1000000.00", Side.BUY, "998750.00");

		mvc.perform(get("/rfqs").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$[0].id").exists())
				.andExpect(jsonPath("$[0].counterpartyId").exists())
				.andExpect(jsonPath("$[0].bondId").exists())
				.andExpect(jsonPath("$[0].notionalAmount").exists())
				.andExpect(jsonPath("$[0].side").exists())
				.andExpect(jsonPath("$[0].status").exists())
				.andExpect(jsonPath("$[0].executionPrice").exists())
				.andExpect(jsonPath("$[0].createdAt").exists());
	}

	// ---------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------

	private long createCounterparty(final String name, final String lei, final String creditLimit) throws Exception {
		final Counterparty cp = new Counterparty(name, lei, new BigDecimal(creditLimit));
		final MvcResult result = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return extractId(result);
	}

	private long createBond(final String isin, final String issuer, final String couponRate,
			final String maturityDate, final String availableNotional) throws Exception {
		final Bond bond = new Bond(isin, issuer, new BigDecimal(couponRate), LocalDate.parse(maturityDate),
				new BigDecimal(availableNotional));
		final MvcResult result = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return extractId(result);
	}

	private long executeRfq(final long cpId, final long bondId, final String notional, final Side side,
			final String executionPrice) throws Exception {
		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal(notional));
		rfq.setSide(side);
		rfq.setExecutionPrice(new BigDecimal(executionPrice));
		final MvcResult result = mvc.perform(post("/rfqs").content(asJsonString(rfq))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return extractId(result);
	}

	private static String operationBody(final String amount, final String operation) throws Exception {
		return MAPPER.createObjectNode().put("amount", amount).put("operation", operation).toString();
	}

	private static void assertDecimalEquals(final String expected, final MvcResult result, final String field)
			throws Exception {
		final JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
		final BigDecimal actual = node.get(field).decimalValue();
		if (new BigDecimal(expected).compareTo(actual) != 0) {
			throw new AssertionError("Expected " + field + "=" + expected + " but was " + actual);
		}
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
