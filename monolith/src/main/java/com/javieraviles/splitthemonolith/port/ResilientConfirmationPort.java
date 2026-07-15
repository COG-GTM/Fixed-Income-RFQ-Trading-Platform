package com.javieraviles.splitthemonolith.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * Wraps the remote {@link ConfirmationPort} with graceful degradation: if the
 * remote confirmation-service is slow or unavailable, the failure is swallowed
 * (after logging) and delegated to a local fallback port so that a confirmation
 * outage can never break the credit-add path.
 */
public class ResilientConfirmationPort implements ConfirmationPort {

	private final Logger logger = LoggerFactory.getLogger(ResilientConfirmationPort.class);

	private final ConfirmationPort remote;
	private final ConfirmationPort fallback;

	public ResilientConfirmationPort(final ConfirmationPort remote, final ConfirmationPort fallback) {
		this.remote = remote;
		this.fallback = fallback;
	}

	@Override
	public void sendConfirmation(final TradeConfirmationDto confirmation) {
		try {
			remote.sendConfirmation(confirmation);
		} catch (final RuntimeException e) {
			logger.warn("Remote confirmation-service call failed ({}); falling back to local confirmation",
					e.getMessage());
			fallback.sendConfirmation(confirmation);
		}
	}

}
