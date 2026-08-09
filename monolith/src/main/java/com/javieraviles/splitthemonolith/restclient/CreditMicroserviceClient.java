package com.javieraviles.splitthemonolith.restclient;

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

import com.javieraviles.splitthemonolith.dto.CreditCheckDto;
import com.javieraviles.splitthemonolith.dto.CreditCheckResultDto;
import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;

@Component
public class CreditMicroserviceClient {

	@Value(value = "${creditms.url}")
	private String creditMsBaseUri;

	@Autowired
	private RestTemplate restTemplate;

	public CreditCheckResultDto reserveCredit(final String lei, final BigDecimal amount) {
		final HttpEntity<CreditCheckDto> requestEntity = new HttpEntity<>(new CreditCheckDto(lei, amount),
				getJsonHeaders());
		try {
			return restTemplate.exchange(creditMsBaseUri + "credit-checks", HttpMethod.POST, requestEntity,
					CreditCheckResultDto.class).getBody();
		} catch (final HttpClientErrorException.Conflict e) {
			throw new InsufficientCreditException();
		} catch (final HttpClientErrorException.NotFound e) {
			throw new ResourceNotFoundException();
		}
	}

	private HttpHeaders getJsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
