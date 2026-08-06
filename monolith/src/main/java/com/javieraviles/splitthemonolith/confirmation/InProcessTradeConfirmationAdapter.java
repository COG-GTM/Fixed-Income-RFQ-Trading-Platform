package com.javieraviles.splitthemonolith.confirmation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * In-process implementation of the confirmation capability, kept inside the
 * monolith. Selected when {@code use.confirmation.service} is {@code false}.
 */
@Component
public class InProcessTradeConfirmationAdapter implements TradeConfirmationPort {

	private static final Logger LOGGER = LoggerFactory.getLogger(InProcessTradeConfirmationAdapter.class);

	@Override
	public void sendConfirmation(final TradeConfirmationDto confirmation) {
		LOGGER.info("Trade confirmation: counterparty {} credit updated, amount {}",
				confirmation.getCounterpartyName(), confirmation.getCreditAmount());
	}
}
