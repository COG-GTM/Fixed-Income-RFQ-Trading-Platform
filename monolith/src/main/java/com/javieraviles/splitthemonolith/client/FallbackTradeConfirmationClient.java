package com.javieraviles.splitthemonolith.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * Local fallback used when the external confirmation microservice is
 * unreachable. Logs the confirmation so it can be retried or audited later.
 */
@Component
public class FallbackTradeConfirmationClient implements TradeConfirmationClient {

	private static final Logger logger = LoggerFactory.getLogger(FallbackTradeConfirmationClient.class);

	@Override
	public void sendConfirmation(final TradeConfirmationDto confirmation) {
		logger.warn("FALLBACK — trade confirmation logged locally: counterparty {} credit updated, amount {}",
				confirmation.getCounterpartyName(), confirmation.getCreditAmount());
	}
}
