package com.javieraviles.splitthemonolith.client;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * Abstraction over trade-confirmation delivery.
 * <p>
 * v1 used direct method invocation ({@code TradeConfirmationService}).
 * v2 targets an external REST microservice with a local fallback.
 */
public interface TradeConfirmationClient {

	void sendConfirmation(TradeConfirmationDto confirmation);
}
