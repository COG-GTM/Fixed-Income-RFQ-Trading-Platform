package com.javieraviles.splitthemonolith.restclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

@ExtendWith(MockitoExtension.class)
class TradeConfirmationMicroserviceClientTest {

	@Mock
	private RestTemplate restTemplate;

	@InjectMocks
	private TradeConfirmationMicroserviceClient client;

	@Test
	@SuppressWarnings("unchecked")
	void sendConfirmation_postsJsonToConfirmationsEndpoint() {
		ReflectionTestUtils.setField(client, "confirmationMsBaseUri", "http://localhost:8070/");
		final TradeConfirmationDto confirmation = new TradeConfirmationDto("Acme Asset Management",
				new BigDecimal("250000.00"));

		client.sendConfirmation(confirmation);

		final ArgumentCaptor<HttpEntity<TradeConfirmationDto>> captor = ArgumentCaptor.forClass(HttpEntity.class);
		verify(restTemplate).exchange(eq("http://localhost:8070/confirmations/"), eq(HttpMethod.POST),
				captor.capture(), eq(String.class));
		assertThat(captor.getValue().getBody()).isSameAs(confirmation);
		assertThat(captor.getValue().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
	}
}
