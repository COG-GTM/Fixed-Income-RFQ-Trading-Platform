package com.javieraviles.splitthemonolith.restclient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.dto.CounterpartyDto;
import com.javieraviles.splitthemonolith.dto.CreditReservationDto;
import com.javieraviles.splitthemonolith.dto.OperationEnum;
import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;

@Component
public class CreditServiceClient {

	@Value(value = "${creditservice.url}")
	private String creditServiceBaseUri;

	@Autowired
	private RestTemplate restTemplate;

	public List<CounterpartyDto> getCounterparties() {
		return exchange(HttpMethod.GET, "counterparties", null,
				new ParameterizedTypeReference<List<CounterpartyDto>>() {
				});
	}

	public CounterpartyDto getCounterparty(final long counterpartyId) {
		return exchange(HttpMethod.GET, "counterparties/" + counterpartyId, null, CounterpartyDto.class);
	}

	public CounterpartyDto createCounterparty(final CounterpartyDto counterparty) {
		return exchange(HttpMethod.POST, "counterparties", counterparty, CounterpartyDto.class);
	}

	public CounterpartyDto updateCounterparty(final long counterpartyId, final CounterpartyDto counterparty) {
		return exchange(HttpMethod.PUT, "counterparties/" + counterpartyId, counterparty, CounterpartyDto.class);
	}

	public CounterpartyDto updateCredit(final long counterpartyId, final BigDecimal amount,
			final OperationEnum operation) {
		final Map<String, String> creditUpdate = Map.of("amount", amount.toPlainString(), "operation",
				operation.name());
		return exchange(HttpMethod.PATCH, "counterparties/" + counterpartyId, creditUpdate, CounterpartyDto.class);
	}

	public void deleteCounterparty(final long counterpartyId) {
		exchange(HttpMethod.DELETE, "counterparties/" + counterpartyId, null, Void.class);
	}

	/**
	 * Reserves the credit needed to settle an RFQ. Throws
	 * {@link InsufficientCreditException} when the credit service rejects the
	 * reservation, which keeps the exception semantics the monolith had while
	 * credit was a local aggregate.
	 */
	public CreditReservationDto reserveCredit(final long counterpartyId, final BigDecimal amount) {
		return exchange(HttpMethod.POST, "counterparties/" + counterpartyId + "/reservations",
				new CreditReservationDto(amount), CreditReservationDto.class);
	}

	public void releaseCreditReservation(final long counterpartyId, final long reservationId) {
		exchange(HttpMethod.DELETE, "counterparties/" + counterpartyId + "/reservations/" + reservationId, null,
				Void.class);
	}

	private <T> T exchange(final HttpMethod method, final String path, final Object body,
			final Class<T> responseType) {
		try {
			return restTemplate
					.exchange(creditServiceBaseUri + path, method, new HttpEntity<>(body, getJsonHeaders()),
							responseType)
					.getBody();
		} catch (final HttpStatusCodeException e) {
			throw translate(e);
		}
	}

	private <T> T exchange(final HttpMethod method, final String path, final Object body,
			final ParameterizedTypeReference<T> responseType) {
		try {
			return restTemplate
					.exchange(creditServiceBaseUri + path, method, new HttpEntity<>(body, getJsonHeaders()),
							responseType)
					.getBody();
		} catch (final HttpStatusCodeException e) {
			throw translate(e);
		}
	}

	private RuntimeException translate(final HttpStatusCodeException e) {
		if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
			return new ResourceNotFoundException();
		}
		if (e.getStatusCode() == HttpStatus.CONFLICT) {
			return new InsufficientCreditException();
		}
		return e;
	}

	private HttpHeaders getJsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

}
