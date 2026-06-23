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
import com.javieraviles.rfqservice.exception.InsufficientNotionalException;
import com.javieraviles.rfqservice.exception.ResourceNotFoundException;

/**
 * REST client for the Bond Inventory microservice (WP A, default port 8071).
 */
@Component
public class BondServiceClient {

	@Value(value = "${bondms.url}")
	private String bondMsBaseUri;

	@Autowired
	private RestTemplate restTemplate;

	/**
	 * Deducts {@code amount} of notional from the given bond.
	 *
	 * @throws ResourceNotFoundException    if the bond does not exist
	 * @throws InsufficientNotionalException if the bond has insufficient notional
	 */
	public void deductNotional(final long bondId, final BigDecimal amount) {
		patchBond(bondId, amount, OperationEnum.DEDUCT);
	}

	/**
	 * Restores (adds back) {@code amount} of notional to the given bond. Used as the
	 * compensating transaction when a downstream step of the saga fails.
	 */
	public void addNotional(final long bondId, final BigDecimal amount) {
		patchBond(bondId, amount, OperationEnum.ADD);
	}

	private void patchBond(final long bondId, final BigDecimal amount, final OperationEnum operation) {
		final AmountOperationRequest request = new AmountOperationRequest(amount, operation);
		final HttpEntity<AmountOperationRequest> requestEntity = new HttpEntity<>(request, getJsonHeaders());
		try {
			restTemplate.exchange(bondMsBaseUri + "bonds/" + bondId, HttpMethod.PATCH, requestEntity, String.class);
		} catch (HttpClientErrorException.NotFound e) {
			throw new ResourceNotFoundException();
		} catch (HttpClientErrorException.BadRequest e) {
			throw new InsufficientNotionalException();
		}
	}

	private HttpHeaders getJsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
