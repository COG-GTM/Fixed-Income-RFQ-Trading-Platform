package com.javieraviles.splitthemonolith.restclient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

class TradeConfirmationMicroserviceClientTest {

	private TradeConfirmationMicroserviceClient client;
	private MockRestServiceServer server;

	@BeforeEach
	void setUp() {
		final RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		client = new TradeConfirmationMicroserviceClient();
		ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
		ReflectionTestUtils.setField(client, "confirmationMsBaseUri", "http://localhost:8070/");
	}

	@Test
	void sendConfirmation_postsSerializedJsonToConfirmationsEndpoint() {
		server.expect(requestTo("http://localhost:8070/confirmations/"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().json(
						"{\"counterpartyName\":\"Acme Asset Management\",\"creditAmount\":250000.00}", true))
				.andRespond(withSuccess());

		client.sendConfirmation(new TradeConfirmationDto("Acme Asset Management", new BigDecimal("250000.00")));

		server.verify();
	}
}
