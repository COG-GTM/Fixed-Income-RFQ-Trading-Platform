package com.javieraviles.splitthemonolith.port;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * Anti-corruption boundary for the Trade Confirmation capability (Domain D).
 *
 * The monolith depends only on this port; whether confirmations are handled
 * in-process ({@code TradeConfirmationService}) or by the remote
 * {@code confirmation-service} ({@code TradeConfirmationMicroserviceClient}) is
 * a wiring decision driven by the {@code use.confirmation.service} flag.
 */
public interface ConfirmationPort {

	void sendConfirmation(TradeConfirmationDto confirmation);

}
