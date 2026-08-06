package com.javieraviles.splitthemonolith.confirmation;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * Routes confirmations to the in-process implementation or to the extracted
 * microservice, depending on the {@code use.confirmation.service} toggle.
 *
 * Callers depend on {@link TradeConfirmationPort} only, so the toggle — and
 * eventually its removal, once the microservice owns the capability — stays
 * confined to this class.
 */
@Primary
@Component
public class TradeConfirmationDispatcher implements TradeConfirmationPort {

	private final boolean useConfirmationService;

	private final TradeConfirmationPort inProcessAdapter;

	private final TradeConfirmationPort remoteAdapter;

	public TradeConfirmationDispatcher(
			@Value("${use.confirmation.service}") final boolean useConfirmationService,
			@Qualifier("inProcessTradeConfirmationAdapter") final TradeConfirmationPort inProcessAdapter,
			@Qualifier("remoteTradeConfirmationAdapter") final TradeConfirmationPort remoteAdapter) {
		this.useConfirmationService = useConfirmationService;
		this.inProcessAdapter = inProcessAdapter;
		this.remoteAdapter = remoteAdapter;
	}

	@Override
	public void sendConfirmation(final TradeConfirmationDto confirmation) {
		if (useConfirmationService) {
			remoteAdapter.sendConfirmation(confirmation);
		} else {
			inProcessAdapter.sendConfirmation(confirmation);
		}
	}
}
