package com.javieraviles.splitthemonolith.confirmation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

@Component
@ConditionalOnProperty(value = "use.confirmation.service", havingValue = "true")
public class RemoteTradeConfirmationSender implements TradeConfirmationSender {

	@Value(value = "${confirmation.service.url}")
	private String confirmationServiceBaseUri;

	@Autowired
	private RestTemplate restTemplate;

	@Override
	public void sendTradeConfirmation(final TradeConfirmationDto confirmation) {
		final HttpEntity<TradeConfirmationDto> requestEntity = new HttpEntity<>(confirmation, jsonHeaders());
		restTemplate.exchange(confirmationServiceBaseUri + "confirmations/", HttpMethod.POST, requestEntity,
				String.class);
	}

	private HttpHeaders jsonHeaders() {
		final HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
