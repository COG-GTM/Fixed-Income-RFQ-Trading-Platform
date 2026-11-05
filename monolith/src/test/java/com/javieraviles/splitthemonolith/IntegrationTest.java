package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
		"rfq.security.users[0].username=ops", "rfq.security.users[0].password={noop}ops-test-pw",
		"rfq.security.users[0].role=OPERATOR",
		"rfq.security.users[1].username=acme", "rfq.security.users[1].password={noop}acme-test-pw",
		"rfq.security.users[1].role=COUNTERPARTY", "rfq.security.users[1].counterparty-id=1" })
@AutoConfigureMockMvc
public class IntegrationTest {

	@Autowired
	private MockMvc mvc;

	private static final RequestPostProcessor OPERATOR = httpBasic("ops", "ops-test-pw");
	private static final RequestPostProcessor ACME = httpBasic("acme", "acme-test-pw");
	private static final long ACME_ID = 1L;

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JavaTimeModule());

	@Test
	public void givenOneCounterparty_whenGetCounterparties_thenReturnJsonArray() throws Exception {
		mvc.perform(get("/counterparties").contentType(MediaType.APPLICATION_JSON).with(OPERATOR)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name", is("Acme Asset Management")));
	}

	@Test
	public void givenOneBond_whenGetBonds_thenReturnJsonArray() throws Exception {
		mvc.perform(get("/bonds").contentType(MediaType.APPLICATION_JSON).with(OPERATOR)).andExpect(status().isOk())
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
				.contentType(MediaType.APPLICATION_JSON).with(OPERATOR).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).with(OPERATOR).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("998750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON).with(OPERATOR)
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
				.contentType(MediaType.APPLICATION_JSON).with(OPERATOR).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).with(OPERATOR).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal("5000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("4993750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON).with(OPERATOR)
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
				.contentType(MediaType.APPLICATION_JSON).with(OPERATOR).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).with(OPERATOR).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.SELL);
		rfq.setExecutionPrice(new BigDecimal("999000.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON).with(OPERATOR)
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

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON).with(OPERATOR)
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

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON).with(OPERATOR)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
	}

	@Test
	public void whenNoCredentials_thenEveryEndpointReturnsUnauthorized() throws Exception {
		final MockHttpServletRequestBuilder[] requests = { get("/counterparties"), post("/counterparties"),
				get("/counterparties/1"), put("/counterparties/1"), patch("/counterparties/1"),
				delete("/counterparties/1"), get("/bonds"), post("/bonds"), get("/bonds/2"), put("/bonds/2"),
				patch("/bonds/2"), delete("/bonds/2"), get("/rfqs"), post("/rfqs"), get("/rfqs/3"),
				delete("/rfqs/3") };
		for (final MockHttpServletRequestBuilder request : requests) {
			mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	public void whenWrongPassword_thenUnauthorized() throws Exception {
		mvc.perform(get("/bonds").with(httpBasic("ops", "wrong"))).andExpect(status().isUnauthorized());
	}

	@Test
	public void whenCounterpartyUserCallsOperatorEndpoints_thenForbidden() throws Exception {
		final MockHttpServletRequestBuilder[] requests = { get("/counterparties"), post("/counterparties"),
				put("/counterparties/" + ACME_ID), patch("/counterparties/" + ACME_ID),
				delete("/counterparties/" + ACME_ID), post("/bonds"), put("/bonds/2"), patch("/bonds/2"),
				delete("/bonds/2"), delete("/rfqs/3") };
		for (final MockHttpServletRequestBuilder request : requests) {
			mvc.perform(request.with(ACME).contentType(MediaType.APPLICATION_JSON)
					.content("{\"amount\":\"1000000\",\"operation\":\"ADD\"}"))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	public void whenCounterpartyUserReadsOtherCounterparty_thenForbidden() throws Exception {
		final long otherId = createCounterparty("Other Fund", "549300OTHERFUND0001", "1000000.00");
		mvc.perform(get("/counterparties/" + otherId).with(ACME)).andExpect(status().isForbidden());
		mvc.perform(get("/counterparties/" + ACME_ID).with(ACME)).andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Acme Asset Management")));
	}

	@Test
	public void whenCounterpartyUserSendsHead_thenSameAccessAsGet() throws Exception {
		final long otherId = createCounterparty("Other Fund", "549300OTHERFUND0002", "1000000.00");
		mvc.perform(head("/bonds").with(ACME)).andExpect(status().isOk());
		mvc.perform(head("/rfqs").with(ACME)).andExpect(status().isOk());
		mvc.perform(head("/counterparties/" + ACME_ID).with(ACME)).andExpect(status().isOk());
		mvc.perform(head("/counterparties/" + otherId).with(ACME)).andExpect(status().isForbidden());
		mvc.perform(head("/counterparties").with(ACME)).andExpect(status().isForbidden());
		mvc.perform(head("/bonds")).andExpect(status().isUnauthorized());
	}

	@Test
	public void whenCounterpartyUserExecutesRfqForOtherCounterparty_thenForbidden() throws Exception {
		final long otherId = createCounterparty("Victim Fund", "549300VICTIMFUND001", "10000000.00");
		final long bondId = createBond("US912828EF56", "100000000.00");

		final RfqDto rfq = rfq(otherId, bondId, "1000000.00", "998750.00");
		mvc.perform(post("/rfqs").with(ACME).content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden());
	}

	@Test
	public void whenCounterpartyUserExecutesRfq_thenCounterpartyTakenFromPrincipal() throws Exception {
		final long bondId = createBond("US912828GH78", "100000000.00");

		final RfqDto rfq = rfq(0L, bondId, "1000000.00", "998750.00");
		final MvcResult result = mvc.perform(post("/rfqs").with(ACME).content(asJsonString(rfq))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.counterpartyId", is((int) ACME_ID))).andReturn();

		mvc.perform(get("/rfqs").with(ACME)).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].counterpartyId", everyItem(is((int) ACME_ID))));
		mvc.perform(get("/rfqs/" + extractId(result)).with(ACME)).andExpect(status().isOk());
	}

	@Test
	public void whenCounterpartyUserReadsOtherCounterpartysRfq_thenNotFound() throws Exception {
		final long otherId = createCounterparty("Hidden Fund", "549300HIDDENFUND001", "10000000.00");
		final long bondId = createBond("US912828IJ90", "100000000.00");
		final MvcResult result = mvc.perform(post("/rfqs").with(OPERATOR)
				.content(asJsonString(rfq(otherId, bondId, "1000000.00", "998750.00")))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

		mvc.perform(get("/rfqs/" + extractId(result)).with(ACME)).andExpect(status().isNotFound());
	}

	@Test
	public void whenExecutionPriceOutsideBand_thenBadRequest() throws Exception {
		final long cpId = createCounterparty("Price Test Fund", "549300PRICETEST0001", "100000000.00");
		final long bondId = createBond("US912828KL12", "100000000.00");

		mvc.perform(post("/rfqs").with(OPERATOR).content(asJsonString(rfq(cpId, bondId, "50000000.00", "0.01")))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Invalid execution price")));
		mvc.perform(post("/rfqs").with(OPERATOR).content(asJsonString(rfq(cpId, bondId, "-1000000.00", "998750.00")))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
	}

	private long createCounterparty(final String name, final String lei, final String creditLimit) throws Exception {
		final Counterparty cp = new Counterparty(name, lei, new BigDecimal(creditLimit));
		return extractId(mvc.perform(post("/counterparties").with(OPERATOR).content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn());
	}

	private long createBond(final String isin, final String notional) throws Exception {
		final Bond bond = new Bond(isin, "US Treasury", new BigDecimal("3.0000"), LocalDate.of(2034, 5, 15),
				new BigDecimal(notional));
		return extractId(mvc.perform(post("/bonds").with(OPERATOR).content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn());
	}

	private static RfqDto rfq(final long cpId, final long bondId, final String notional, final String price) {
		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal(notional));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal(price));
		return rfq;
	}

	private static long extractId(final MvcResult result) throws Exception {
		final JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
		return node.get("id").asLong();
	}

	private static RequestPostProcessor httpBasic(final String username, final String password) {
		final String token = Base64.getEncoder()
				.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + token);
			return request;
		};
	}

	private static String asJsonString(final Object obj) {
		try {
			return MAPPER.writeValueAsString(obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
