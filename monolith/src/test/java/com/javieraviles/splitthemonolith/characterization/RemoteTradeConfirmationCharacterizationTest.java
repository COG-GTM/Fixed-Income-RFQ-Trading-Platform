package com.javieraviles.splitthemonolith.characterization;

import static com.javieraviles.splitthemonolith.characterization.CharacterizationSupport.bodyOf;
import static com.javieraviles.splitthemonolith.characterization.CharacterizationSupport.createCounterparty;
import static com.javieraviles.splitthemonolith.characterization.CharacterizationSupport.creditPatch;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

/**
 * Characterization tests for the {@code use.confirmation.service=true} branch:
 * trade confirmations are POSTed to the confirmation microservice, and a
 * failure of that call propagates and aborts the credit update.
 */
@SpringBootTest(properties = { "use.confirmation.service=true",
		"confirmationms.url=http://confirmation-ms.test/",
		"spring.datasource.generate-unique-name=true" })
@AutoConfigureMockMvc
public class RemoteTradeConfirmationCharacterizationTest {

	private static final String CONFIRMATIONS_URI = "http://confirmation-ms.test/confirmations/";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RestTemplate restTemplate;

	private MockRestServiceServer confirmationMs;

	@BeforeEach
	public void interceptOutboundCalls() {
		confirmationMs = MockRestServiceServer.bindTo(restTemplate).build();
	}

	@Test
	@DisplayName("Adding credit POSTs the confirmation payload to the confirmation microservice")
	public void addingCredit_postsConfirmationToMicroservice() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Remote Confirm Fund",
				"549300REMOTECONF0001", "5000000.00");

		confirmationMs.expect(requestTo(CONFIRMATIONS_URI))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.counterpartyName").value("Remote Confirm Fund"))
				.andExpect(content().string(containsString("\"creditAmount\":250000.00")))
				.andRespond(withSuccess("", MediaType.TEXT_PLAIN));

		mvc.perform(patch("/counterparties/" + counterpartyId).content(creditPatch("ADD", "250000.00"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());

		confirmationMs.verify();
		assertAvailableCredit(counterpartyId, "5250000.00");
	}

	@Test
	@DisplayName("Deducting credit calls no microservice")
	public void deductingCredit_callsNoMicroservice() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Remote Deduct Fund",
				"549300REMOTEDEDUCT01", "5000000.00");

		mvc.perform(patch("/counterparties/" + counterpartyId).content(creditPatch("DEDUCT", "250000.00"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());

		confirmationMs.verify();
		assertAvailableCredit(counterpartyId, "4750000.00");
	}

	@Test
	@DisplayName("A failing confirmation call aborts the credit update: the added credit is not persisted")
	public void failingConfirmationCall_abortsTheCreditUpdate() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "Remote Failure Fund",
				"549300REMOTEFAIL0001", "5000000.00");

		confirmationMs.expect(requestTo(CONFIRMATIONS_URI)).andRespond(withServerError());

		assertThrows(Exception.class, () -> mvc.perform(patch("/counterparties/" + counterpartyId)
				.content(creditPatch("ADD", "250000.00"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)));

		confirmationMs.verify();
		assertAvailableCredit(counterpartyId, "5000000.00");
	}

	private void assertAvailableCredit(final long counterpartyId, final String expected) throws Exception {
		final BigDecimal actual = bodyOf(mvc.perform(get("/counterparties/" + counterpartyId))
				.andExpect(status().isOk()).andReturn()).get("availableCredit").decimalValue();
		assertEquals(0, new BigDecimal(expected).compareTo(actual),
				() -> "availableCredit expected " + expected + " but was " + actual);
	}
}
