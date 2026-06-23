package com.javieraviles.splitthemonolith.restclient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;

@Component
public class CounterpartyMicroserviceClient {

	@Value(value = "${counterpartyms.url}")
	private String counterpartyMsBaseUri;

	@Autowired
	private RestTemplate restTemplate;

	public List<Counterparty> getAll() {
		return restTemplate.exchange(counterpartyMsBaseUri + "counterparties", HttpMethod.GET, null,
				new ParameterizedTypeReference<List<Counterparty>>() {
				}).getBody();
	}

	public Counterparty create(final Counterparty newCounterparty) {
		final HttpEntity<Counterparty> requestEntity = new HttpEntity<>(newCounterparty, getJsonHeaders());
		return restTemplate.exchange(counterpartyMsBaseUri + "counterparties", HttpMethod.POST, requestEntity,
				Counterparty.class).getBody();
	}

	public Counterparty getOne(final Long id) {
		try {
			return restTemplate.getForObject(counterpartyMsBaseUri + "counterparties/" + id, Counterparty.class);
		} catch (final HttpClientErrorException.NotFound e) {
			throw new ResourceNotFoundException();
		}
	}

	public Counterparty update(final Long id, final Counterparty updatedCounterparty) {
		final HttpEntity<Counterparty> requestEntity = new HttpEntity<>(updatedCounterparty, getJsonHeaders());
		try {
			return restTemplate.exchange(counterpartyMsBaseUri + "counterparties/" + id, HttpMethod.PUT, requestEntity,
					Counterparty.class).getBody();
		} catch (final HttpClientErrorException.NotFound e) {
			throw new ResourceNotFoundException();
		}
	}

	public ResponseEntity<String> patchCredit(final Long id, final Map<String, String> creditUpdate) {
		final HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(creditUpdate, getJsonHeaders());
		return restTemplate.exchange(counterpartyMsBaseUri + "counterparties/" + id, HttpMethod.PATCH, requestEntity,
				String.class);
	}

	public void delete(final Long id) {
		restTemplate.delete(counterpartyMsBaseUri + "counterparties/" + id);
	}

	public Counterparty deductCredit(final Long id, final BigDecimal amount) {
		final Map<String, String> creditUpdate = new HashMap<>();
		creditUpdate.put("amount", amount.toPlainString());
		creditUpdate.put("operation", "DEDUCT");
		final HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(creditUpdate, getJsonHeaders());
		try {
			return restTemplate.exchange(counterpartyMsBaseUri + "counterparties/" + id, HttpMethod.PATCH, requestEntity,
					Counterparty.class).getBody();
		} catch (final HttpClientErrorException.BadRequest e) {
			throw new InsufficientCreditException();
		} catch (final HttpClientErrorException.NotFound e) {
			throw new ResourceNotFoundException();
		}
	}

	private HttpHeaders getJsonHeaders() {
		final HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

}
