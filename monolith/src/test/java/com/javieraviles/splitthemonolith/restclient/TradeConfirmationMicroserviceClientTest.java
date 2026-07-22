package com.javieraviles.splitthemonolith.restclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

@ExtendWith(MockitoExtension.class)
class TradeConfirmationMicroserviceClientTest {

	private static final String BASE_URI = "http://confirmationms/";

	@Mock
	private RestTemplate restTemplate;

	@InjectMocks
	private TradeConfirmationMicroserviceClient client;

	private TradeConfirmationDto confirmation;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(client, "confirmationMsBaseUri", BASE_URI);
		confirmation = new TradeConfirmationDto("Acme Asset Management", new BigDecimal("1000000.00"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void sendConfirmation_postsJsonEntityToConfirmationsEndpoint() {
		when(restTemplate.exchange(eq(BASE_URI + "confirmations/"), eq(HttpMethod.POST),
				any(HttpEntity.class), eq(String.class)))
				.thenReturn(ResponseEntity.ok("created"));

		client.sendConfirmation(confirmation);

		final ArgumentCaptor<HttpEntity<TradeConfirmationDto>> captor =
				ArgumentCaptor.forClass(HttpEntity.class);
		verify(restTemplate).exchange(eq(BASE_URI + "confirmations/"), eq(HttpMethod.POST),
				captor.capture(), eq(String.class));

		final HttpEntity<TradeConfirmationDto> sent = captor.getValue();
		assertThat(sent.getBody()).isSameAs(confirmation);
		assertThat(sent.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
	}

	@SuppressWarnings("unchecked")
	@Test
	void sendConfirmation_propagatesRestClientException() {
		when(restTemplate.exchange(eq(BASE_URI + "confirmations/"), eq(HttpMethod.POST),
				any(HttpEntity.class), eq(String.class)))
				.thenThrow(new RestClientException("downstream unavailable"));

		assertThatThrownBy(() -> client.sendConfirmation(confirmation))
				.isInstanceOf(RestClientException.class);
	}
}
