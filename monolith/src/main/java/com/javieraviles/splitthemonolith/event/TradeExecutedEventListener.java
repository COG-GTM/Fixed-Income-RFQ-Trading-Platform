package com.javieraviles.splitthemonolith.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.javieraviles.splitthemonolith.client.TradeConfirmationClient;
import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * Sends the trade confirmation only after the originating transaction has
 * committed, preventing phantom confirmations for rolled-back trades.
 */
@Component
public class TradeExecutedEventListener {

	private final TradeConfirmationClient tradeConfirmationClient;

	public TradeExecutedEventListener(TradeConfirmationClient tradeConfirmationClient) {
		this.tradeConfirmationClient = tradeConfirmationClient;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onTradeExecuted(TradeExecutedEvent event) {
		tradeConfirmationClient.sendConfirmation(
				new TradeConfirmationDto(event.getCounterpartyName(), event.getAmount()));
	}
}
