package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.javieraviles.splitthemonolith.entity.Side;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

/**
 * Exercises the RFQ saga with the credit-service toggle switched on: credit is
 * reserved by the remote service instead of the local counterparty entity.
 */
@SpringBootTest(properties = { "use.credit.service=true", "creditms.url=http://credit-service:8090/",
		"spring.datasource.url=jdbc:h2:mem:credit-toggle-db" })
@AutoConfigureMockMvc
public class CreditServiceToggleIntegrationTest {

	private static final String CREDIT_CHECK_URL = "http://credit-service:8090/credit-checks";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RestTemplate restTemplate;

	private MockRestServiceServer creditService;

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	@BeforeEach
	public void setUp() {
		creditService = MockRestServiceServer.bindTo(restTemplate).build();
	}

	@Test
	public void whenExecuteRfq_thenCreditIsReservedByCreditService() throws Exception {
		final long cpId = createCounterparty("Fidelity Investments", "549300FIDELITY00001", "20000000.00");
		final long bondId = createBond("US912828ZT09", "50000000.00");

		creditService.expect(requestTo(CREDIT_CHECK_URL)).andExpect(method(HttpMethod.POST))
				.andExpect(MockRestRequestMatchers.jsonPath("$.lei").value("549300FIDELITY00001"))
				.andExpect(MockRestRequestMatchers.jsonPath("$.amount").value(998750.00))
				.andRespond(withSuccess("{\"lei\":\"549300FIDELITY00001\",\"approved\":true,"
						+ "\"reservedAmount\":998750.00,\"availableCredit\":19001250.00}",
						MediaType.APPLICATION_JSON));

		mvc.perform(post("/rfqs").content(asJsonString(rfq(cpId, bondId, "1000000.00", "998750.00")))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated());

		creditService.verify();

		mvc.perform(get("/counterparties/" + cpId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(19001250.00)));
	}

	@Test
	public void whenCreditServiceRejects_thenBadRequestAndNotionalRolledBack() throws Exception {
		final long cpId = createCounterparty("Small Fund LLC", "549300SMALLFUND001", "500000.00");
		final long bondId = createBond("US912828CD34", "50000000.00");

		creditService.expect(requestTo(CREDIT_CHECK_URL))
				.andRespond(withStatus(HttpStatus.CONFLICT));

		mvc.perform(post("/rfqs").content(asJsonString(rfq(cpId, bondId, "1000000.00", "999000.00")))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient credit")));

		creditService.verify();

		mvc.perform(get("/bonds/" + bondId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional", is(50000000.00)));
	}

	@Test
	public void whenCreditServiceHasNoAccountForLei_thenNotFound() throws Exception {
		final long cpId = createCounterparty("Unknown Fund", "549300UNKNOWN00001", "500000.00");
		final long bondId = createBond("US912828EF56", "50000000.00");

		creditService.expect(requestTo(CREDIT_CHECK_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		mvc.perform(post("/rfqs").content(asJsonString(rfq(cpId, bondId, "1000000.00", "999000.00")))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());

		creditService.verify();
	}

	private long createCounterparty(final String name, final String lei, final String creditLimit) throws Exception {
		final MvcResult result = mvc
				.perform(post("/counterparties").content(asJsonString(new Counterparty(name, lei,
						new BigDecimal(creditLimit)))).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return extractId(result);
	}

	private long createBond(final String isin, final String availableNotional) throws Exception {
		final MvcResult result = mvc.perform(post("/bonds")
				.content(asJsonString(new Bond(isin, "US Treasury", new BigDecimal("3.0000"),
						LocalDate.of(2033, 2, 15), new BigDecimal(availableNotional))))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();
		return extractId(result);
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

	private static String asJsonString(final Object obj) {
		try {
			return MAPPER.writeValueAsString(obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
