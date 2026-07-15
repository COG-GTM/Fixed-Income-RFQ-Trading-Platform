package com.javieraviles.splitthemonolith.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;
import com.javieraviles.splitthemonolith.port.ConfirmationPort;

/**
 * In-process implementation of {@link ConfirmationPort} (Domain D). It only
 * logs; it is the default path and doubles as the local fallback when the
 * remote confirmation-service is unavailable.
 */
@Component
public class TradeConfirmationService implements ConfirmationPort {

	Logger logger = LoggerFactory.getLogger(TradeConfirmationService.class);

	@Override
	public void sendConfirmation(final TradeConfirmationDto confirmation) {
		logger.info("Trade confirmation: counterparty {} credit updated, amount {}",
				confirmation.getCounterpartyName(), confirmation.getCreditAmount());
	}
}
