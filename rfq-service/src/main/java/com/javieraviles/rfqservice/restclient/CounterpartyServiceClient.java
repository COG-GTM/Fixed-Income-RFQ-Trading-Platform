package com.javieraviles.rfqservice.restclient;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.rfqservice.dto.AmountOperationRequest;
import com.javieraviles.rfqservice.dto.OperationEnum;
import com.javieraviles.rfqservice.exception.InsufficientCreditException;
import com.javieraviles.rfqservice.exception.ResourceNotFoundException;

/**
 * REST client for the Counterparty/Credit microservice (WP B, default port 8072).
 */
@Component
public class CounterpartyServiceClient {

	@Value(value = "${counterpartyms.url}")
	private String counterpartyMsBaseUri;

	@Autowired
	private RestTemplate restTemplate;

	/**
	 * Deducts {@code amount} of credit from the given counterparty.
	 *
	 * @throws ResourceNotFoundException   if the counterparty does not exist
	 * @throws InsufficientCreditException if the counterparty has insufficient credit
	 */
	public void deductCredit(final long counterpartyId, final BigDecimal amount) {
		final AmountOperationRequest request = new AmountOperationRequest(amount, OperationEnum.DEDUCT);
		final HttpEntity<AmountOperationRequest> requestEntity = new HttpEntity<>(request, getJsonHeaders());
		try {
			restTemplate.exchange(counterpartyMsBaseUri + "counterparties/" + counterpartyId, HttpMethod.PATCH,
					requestEntity, String.class);
		} catch (HttpClientErrorException.NotFound e) {
			throw new ResourceNotFoundException();
		} catch (HttpClientErrorException.BadRequest e) {
			throw new InsufficientCreditException();
		}
	}

	private HttpHeaders getJsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
