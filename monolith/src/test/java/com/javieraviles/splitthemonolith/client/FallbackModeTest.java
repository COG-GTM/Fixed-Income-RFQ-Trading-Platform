package com.javieraviles.splitthemonolith.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

@SpringBootTest
@TestPropertySource(properties = {
		"use.confirmation.service=false",
		"spring.datasource.generate-unique-name=true"
})
public class FallbackModeTest {

	@MockBean
	private RestTemplate restTemplate;

	@Autowired
	private TradeConfirmationClient tradeConfirmationClient;

	@Test
	public void whenToggleOff_thenSkipsRestAndUsesLocalFallback() {
		final TradeConfirmationDto dto = new TradeConfirmationDto("Acme", new BigDecimal("1000000"));

		tradeConfirmationClient.sendConfirmation(dto);

		verify(restTemplate, never()).exchange(
				anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
	}
}
