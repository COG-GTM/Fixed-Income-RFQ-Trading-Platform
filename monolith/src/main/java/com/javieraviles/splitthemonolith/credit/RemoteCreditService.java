package com.javieraviles.splitthemonolith.credit;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;

/**
 * HTTP adapter routing the monolith's credit operations to the extracted
 * credit-service. Active when {@code rfq.credit.service.remote=true}.
 *
 * <p>
 * Service-side status codes are translated back to the monolith's existing
 * domain exceptions so the external RFQ API contract is unchanged from the
 * caller's perspective:
 * <ul>
 * <li>409 Conflict &rarr; {@link InsufficientCreditException} (HTTP 400)</li>
 * <li>404 Not Found &rarr; {@link ResourceNotFoundException} (HTTP 404)</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "rfq.credit.service.remote", havingValue = "true")
public class RemoteCreditService implements CreditService {

	private final RestTemplate restTemplate;
	private final String baseUri;

	public RemoteCreditService(final RestTemplate restTemplate,
			@Value("${rfq.credit.service.url}") final String baseUri) {
		this.restTemplate = restTemplate;
		this.baseUri = baseUri.endsWith("/") ? baseUri : baseUri + "/";
	}

	@Override
	public void reserveCredit(final long counterpartyId, final BigDecimal amount) {
		post("credit/reservations", new CreditOperationRequest(counterpartyId, amount));
	}

	@Override
	public void releaseCredit(final long counterpartyId, final BigDecimal amount) {
		post("credit/releases", new CreditOperationRequest(counterpartyId, amount));
	}

	private void post(final String path, final CreditOperationRequest request) {
		try {
			restTemplate.postForEntity(baseUri + path, request, Void.class);
		} catch (final HttpClientErrorException e) {
			if (e.getStatusCode() == HttpStatus.CONFLICT) {
				throw new InsufficientCreditException();
			}
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				throw new ResourceNotFoundException();
			}
			throw e;
		}
	}
}
