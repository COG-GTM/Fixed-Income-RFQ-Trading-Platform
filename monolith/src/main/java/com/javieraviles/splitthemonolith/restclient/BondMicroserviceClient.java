package com.javieraviles.splitthemonolith.restclient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class BondMicroserviceClient {

	@Value(value = "${bondms.url}")
	private String bondMsBaseUri;

	@Autowired
	private RestTemplate restTemplate;

	public void deductNotional(final long bondId, final BigDecimal amount) {
		final Map<String, String> notionalUpdate = new HashMap<>();
		notionalUpdate.put("amount", amount.toPlainString());
		notionalUpdate.put("operation", "DEDUCT");
		HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(notionalUpdate, getJsonHeaders());
		restTemplate.exchange(bondMsBaseUri + "bonds/" + bondId, HttpMethod.PATCH, requestEntity, String.class);
	}

	private HttpHeaders getJsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

}
