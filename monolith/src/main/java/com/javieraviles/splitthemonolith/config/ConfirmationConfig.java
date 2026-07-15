package com.javieraviles.splitthemonolith.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.javieraviles.splitthemonolith.port.ConfirmationPort;
import com.javieraviles.splitthemonolith.port.ResilientConfirmationPort;
import com.javieraviles.splitthemonolith.restclient.TradeConfirmationMicroserviceClient;
import com.javieraviles.splitthemonolith.service.TradeConfirmationService;

/**
 * Selects which {@link ConfirmationPort} the controllers depend on, driven by
 * the {@code use.confirmation.service} feature flag:
 * <ul>
 * <li>{@code false} (default): in-process {@link TradeConfirmationService}.</li>
 * <li>{@code true}: remote {@link TradeConfirmationMicroserviceClient} wrapped
 * in {@link ResilientConfirmationPort} so a confirmation-service outage degrades
 * gracefully to the local path instead of failing the request.</li>
 * </ul>
 */
@Configuration
public class ConfirmationConfig {

	@Bean
	@Primary
	public ConfirmationPort confirmationPort(
			@Value("${use.confirmation.service}") final boolean useConfirmationService,
			final TradeConfirmationService localService,
			final TradeConfirmationMicroserviceClient remoteClient) {
		if (useConfirmationService) {
			return new ResilientConfirmationPort(remoteClient, localService);
		}
		return localService;
	}

}
