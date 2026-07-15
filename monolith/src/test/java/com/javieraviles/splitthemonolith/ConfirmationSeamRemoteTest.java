package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javieraviles.splitthemonolith.entity.Counterparty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

/**
 * Confirmation seam with the feature flag ON: credit-add must call the remote
 * confirmation-service, and a confirmation-service outage must NOT fail the
 * credit-add (graceful degradation via the ResilientConfirmationPort fallback).
 */
@SpringBootTest(properties = {
		"use.confirmation.service=true",
		"confirmationms.url=http://localhost:8070/",
		"spring.datasource.url=jdbc:h2:mem:seam-remote-db;DB_CLOSE_DELAY=-1" })
@AutoConfigureMockMvc
public class ConfirmationSeamRemoteTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RestTemplate restTemplate;

	private MockRestServiceServer confirmationServer;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@BeforeEach
	public void setUp() {
		confirmationServer = MockRestServiceServer.createServer(restTemplate);
	}

	@Test
	public void givenFlagOn_whenAddCredit_thenRemoteConfirmationServiceIsCalled() throws Exception {
		confirmationServer.expect(requestTo("http://localhost:8070/confirmations/"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.CREATED));

		final long id = createCounterparty("Remote Path Fund", "549300REMOTEPATH01", "1000000.00");

		mvc.perform(patch("/counterparties/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":\"250000.00\",\"operation\":\"ADD\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(1250000.00)));

		confirmationServer.verify();
	}

	@Test
	public void givenFlagOn_whenConfirmationServiceDown_thenCreditAddStillSucceeds() throws Exception {
		confirmationServer.expect(requestTo("http://localhost:8070/confirmations/"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withException(new IOException("connection refused")));

		final long id = createCounterparty("Resilient Fund", "549300RESILIENT001", "1000000.00");

		mvc.perform(patch("/counterparties/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":\"250000.00\",\"operation\":\"ADD\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(1250000.00)));

		confirmationServer.verify();
	}

	@Test
	public void givenFlagOn_whenConfirmationServiceReturns5xx_thenCreditAddStillSucceeds() throws Exception {
		confirmationServer.expect(requestTo("http://localhost:8070/confirmations/"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		final long id = createCounterparty("Degraded Fund", "549300DEGRADED0001", "1000000.00");

		mvc.perform(patch("/counterparties/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":\"250000.00\",\"operation\":\"ADD\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(1250000.00)));

		confirmationServer.verify();
	}

	private long createCounterparty(final String name, final String lei, final String creditLimit) throws Exception {
		final Counterparty cp = new Counterparty(name, lei, new BigDecimal(creditLimit));
		final MvcResult result = mvc.perform(post("/counterparties").content(MAPPER.writeValueAsString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		final JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
		return node.get("id").asLong();
	}

}
