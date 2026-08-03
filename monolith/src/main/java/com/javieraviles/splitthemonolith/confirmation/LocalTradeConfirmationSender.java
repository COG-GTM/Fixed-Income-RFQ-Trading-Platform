package com.javieraviles.splitthemonolith.confirmation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

@Component
@ConditionalOnProperty(value = "use.confirmation.service", havingValue = "false", matchIfMissing = true)
public class LocalTradeConfirmationSender implements TradeConfirmationSender {

	private final Logger logger = LoggerFactory.getLogger(LocalTradeConfirmationSender.class);

	@Override
	public void sendTradeConfirmation(final TradeConfirmationDto confirmation) {
		logger.info("Trade confirmation: counterparty {} credit updated, amount {}",
				confirmation.getCounterpartyName(), confirmation.getCreditAmount());
	}
}
