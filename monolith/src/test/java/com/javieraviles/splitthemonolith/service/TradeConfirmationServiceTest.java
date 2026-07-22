package com.javieraviles.splitthemonolith.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

class TradeConfirmationServiceTest {

	private final TradeConfirmationService service = new TradeConfirmationService();

	@Test
	void sendTradeConfirmation_logsWithoutError() {
		final TradeConfirmationDto confirmation = new TradeConfirmationDto(
				"Acme Asset Management", new BigDecimal("1000000.00"));

		assertThatCode(() -> service.sendTradeConfirmation(confirmation))
				.doesNotThrowAnyException();
	}

	@Test
	void sendTradeConfirmation_handlesNullFields() {
		final TradeConfirmationDto confirmation = new TradeConfirmationDto(null, null);

		assertThatCode(() -> service.sendTradeConfirmation(confirmation))
				.doesNotThrowAnyException();
	}
}
