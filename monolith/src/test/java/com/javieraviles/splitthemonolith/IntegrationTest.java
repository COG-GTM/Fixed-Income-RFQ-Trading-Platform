package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
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
import org.springframework.test.web.servlet.ResultMatcher;

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
		rfq.setSide(Side.SELL);
		rfq.setExecutionPrice(new BigDecimal("999000.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient credit")));
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

	private static final String HUGE_NEGATIVE_EXPONENT = "1e-500000000";

	@Test
	public void whenPatchBondWithHugeExponentAmount_thenReturnBadRequestImmediately() throws Exception {
		final long bondId = createBond("US912828EF56", new BigDecimal("1000000.00"));
		patchAmount("/bonds/" + bondId, "\"" + HUGE_NEGATIVE_EXPONENT + "\"", "DEDUCT", status().isBadRequest());
		patchAmount("/bonds/" + bondId, "\"" + HUGE_NEGATIVE_EXPONENT + "\"", "ADD", status().isBadRequest());
		patchAmount("/bonds/" + bondId, "\"1e+500000000\"", "ADD", status().isBadRequest());
		mvc.perform(get("/bonds/" + bondId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional", is(1000000.00)));
	}

	@Test
	public void whenPatchCounterpartyWithHugeExponentAmount_thenReturnBadRequestImmediately() throws Exception {
		final long cpId = createCounterparty("549300EXPONENT00001", new BigDecimal("1000000.00"));
		patchAmount("/counterparties/" + cpId, "\"" + HUGE_NEGATIVE_EXPONENT + "\"", "DEDUCT", status().isBadRequest());
		patchAmount("/counterparties/" + cpId, HUGE_NEGATIVE_EXPONENT, "ADD", status().isBadRequest());
	}

	@Test
	public void whenPatchBondWithInvalidAmount_thenReturnBadRequest() throws Exception {
		final long bondId = createBond("US912828GH78", new BigDecimal("1000000.00"));
		patchAmount("/bonds/" + bondId, "\"-5.00\"", "DEDUCT", status().isBadRequest());
		patchAmount("/bonds/" + bondId, "\"0\"", "DEDUCT", status().isBadRequest());
		patchAmount("/bonds/" + bondId, "\"1.234\"", "DEDUCT", status().isBadRequest());
		patchAmount("/bonds/" + bondId, "\"123456789012345678\"", "ADD", status().isBadRequest());
		patchAmount("/bonds/" + bondId, "null", "ADD", status().isBadRequest());
		patchAmount("/bonds/" + bondId, "\"1.00\"", "FOO", status().isBadRequest());
		mvc.perform(get("/bonds/" + bondId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional", is(1000000.00)));
	}

	@Test
	public void whenPatchBondAndCounterpartyWithValidAmount_thenReturnOk() throws Exception {
		final long bondId = createBond("US912828IJ90", new BigDecimal("1000000.00"));
		patchAmount("/bonds/" + bondId, "\"100.50\"", "DEDUCT", status().isOk());
		patchAmount("/bonds/" + bondId, "0.50", "ADD", status().isOk());
		mvc.perform(get("/bonds/" + bondId)).andExpect(jsonPath("$.availableNotional", is(999900.00)));

		final long cpId = createCounterparty("549300VALIDAMOUNT01", new BigDecimal("1000.00"));
		patchAmount("/counterparties/" + cpId, "\"250.25\"", "DEDUCT", status().isOk());
		mvc.perform(get("/counterparties/" + cpId)).andExpect(jsonPath("$.availableCredit", is(749.75)));
	}

	@Test
	public void whenExecuteRfqWithHugeExponentAmounts_thenReturnBadRequestImmediately() throws Exception {
		final long cpId = createCounterparty("549300EXPONENT00002", new BigDecimal("1000000.00"));
		final long bondId = createBond("US912828KL12", new BigDecimal("1000000.00"));
		final String template = "{\"counterpartyId\":%d,\"bondId\":%d,\"side\":\"BUY\","
				+ "\"notionalAmount\":%s,\"executionPrice\":%s}";
		for (final String body : new String[] {
				String.format(template, cpId, bondId, HUGE_NEGATIVE_EXPONENT, "100.00"),
				String.format(template, cpId, bondId, "100.00", HUGE_NEGATIVE_EXPONENT),
				String.format(template, cpId, bondId, "-1", "100.00"),
				String.format(template, cpId, bondId, "100.00", "null") }) {
			assertTimeoutPreemptively(Duration.ofSeconds(5), () -> mvc.perform(post("/rfqs").content(body)
					.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest()));
		}
	}

	@Test
	public void whenCreateBondWithHugeExponentNotional_thenReturnBadRequestImmediately() throws Exception {
		final String body = "{\"isin\":\"US912828MN34\",\"issuer\":\"US Treasury\",\"couponRate\":2.5,"
				+ "\"maturityDate\":\"2031-01-01\",\"availableNotional\":" + HUGE_NEGATIVE_EXPONENT + "}";
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> mvc.perform(post("/bonds").content(body)
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest()));
	}

	private void patchAmount(final String path, final String amountJson, final String operation,
			final ResultMatcher expected) {
		final String body = "{\"amount\":" + amountJson + ",\"operation\":\"" + operation + "\"}";
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> mvc.perform(patch(path).content(body)
				.contentType(MediaType.APPLICATION_JSON)).andExpect(expected));
	}

	private long createBond(final String isin, final BigDecimal notional) throws Exception {
		final Bond bond = new Bond(isin, "US Treasury", new BigDecimal("2.5000"), LocalDate.of(2031, 8, 15), notional);
		return extractId(mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn());
	}

	private long createCounterparty(final String lei, final BigDecimal creditLimit) throws Exception {
		final Counterparty cp = new Counterparty("Validation Fund " + lei, lei, creditLimit);
		return extractId(mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn());
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
