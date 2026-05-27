package com.javieraviles.splitthemonolith.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * REST-based implementation that posts confirmations to the external
 * confirmation microservice.
 */
@Component
public class RestTradeConfirmationClient implements TradeConfirmationClient {

	private static final Logger logger = LoggerFactory.getLogger(RestTradeConfirmationClient.class);

	@Value(value = "${confirmationms.url}")
	private String confirmationMsBaseUri;

	private final RestTemplate restTemplate;

	public RestTradeConfirmationClient(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	@Override
	public void sendConfirmation(final TradeConfirmationDto confirmation) {
		logger.info("Sending trade confirmation via REST: counterparty {} amount {}",
				confirmation.getCounterpartyName(), confirmation.getCreditAmount());
		HttpEntity<TradeConfirmationDto> requestEntity = new HttpEntity<>(confirmation, jsonHeaders());
		restTemplate.exchange(confirmationMsBaseUri + "confirmations/", HttpMethod.POST, requestEntity, String.class);
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
