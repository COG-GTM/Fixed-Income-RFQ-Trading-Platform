package com.javieraviles.splitthemonolith.confirmation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * Adapter towards the extracted confirmation microservice. Selected when
 * {@code use.confirmation.service} is {@code true}.
 */
@Component
public class RemoteTradeConfirmationAdapter implements TradeConfirmationPort {

	@Value(value = "${confirmationms.url}")
	private String confirmationMsBaseUri;

	@Autowired
	private RestTemplate restTemplate;

	@Override
	public void sendConfirmation(final TradeConfirmationDto confirmation) {
		HttpEntity<TradeConfirmationDto> requestEntity = new HttpEntity<>(confirmation, getJsonHeaders());
		restTemplate.exchange(confirmationMsBaseUri + "confirmations/", HttpMethod.POST, requestEntity, String.class);
	}

	private HttpHeaders getJsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
