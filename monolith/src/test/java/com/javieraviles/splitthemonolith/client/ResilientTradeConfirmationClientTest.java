package com.javieraviles.splitthemonolith.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

@SpringBootTest
@TestPropertySource(properties = {
		"use.confirmation.service=true",
		"spring.datasource.generate-unique-name=true"
})
public class ResilientTradeConfirmationClientTest {

	@MockBean
	private RestTemplate restTemplate;

	@Autowired
	private TradeConfirmationClient tradeConfirmationClient;

	@Autowired
	private FallbackTradeConfirmationClient fallbackClient;

	@Test
	public void whenRestServiceAvailable_thenUsesRestClient() {
		final TradeConfirmationDto dto = new TradeConfirmationDto("Acme", new BigDecimal("1000000"));

		tradeConfirmationClient.sendConfirmation(dto);

		verify(restTemplate).exchange(
				anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
	}

	@Test
	public void whenRestServiceUnavailable_thenFallsBackToLocal() {
		doThrow(new ResourceAccessException("Connection refused"))
				.when(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

		final TradeConfirmationDto dto = new TradeConfirmationDto("Acme", new BigDecimal("1000000"));

		tradeConfirmationClient.sendConfirmation(dto);

		verify(restTemplate).exchange(
				anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
	}
}
