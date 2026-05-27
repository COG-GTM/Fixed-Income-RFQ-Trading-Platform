package com.javieraviles.splitthemonolith.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * Resilient decorator around the REST client.
 * <ul>
 *   <li>When {@code use.confirmation.service=true}: attempts the REST call
 *       first and falls back to the local logger on any failure.</li>
 *   <li>When {@code use.confirmation.service=false}: routes directly to the
 *       local fallback (preserving v1 behaviour during incremental rollout).</li>
 * </ul>
 */
@Component
@Primary
public class ResilientTradeConfirmationClient implements TradeConfirmationClient {

	private static final Logger logger = LoggerFactory.getLogger(ResilientTradeConfirmationClient.class);

	private final RestTradeConfirmationClient restClient;
	private final FallbackTradeConfirmationClient fallbackClient;

	@Value(value = "${use.confirmation.service}")
	private boolean useConfirmationService;

	public ResilientTradeConfirmationClient(RestTradeConfirmationClient restClient,
			FallbackTradeConfirmationClient fallbackClient) {
		this.restClient = restClient;
		this.fallbackClient = fallbackClient;
	}

	@Override
	public void sendConfirmation(final TradeConfirmationDto confirmation) {
		if (!useConfirmationService) {
			fallbackClient.sendConfirmation(confirmation);
			return;
		}
		try {
			restClient.sendConfirmation(confirmation);
		} catch (Exception e) {
			logger.error("REST confirmation service unavailable, falling back to local logging", e);
			fallbackClient.sendConfirmation(confirmation);
		}
	}
}
