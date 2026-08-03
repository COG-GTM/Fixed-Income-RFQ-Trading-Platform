package com.javieraviles.splitthemonolith;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Toggle on: confirmations are delivered to the extracted confirmation-service over
 * the HTTP contract it exposes (POST /confirmations/).
 */
@SpringBootTest(properties = { "use.confirmation.service=true",
		"confirmation.service.url=http://localhost:8070/",
		"spring.datasource.generate-unique-name=true" })
class ConfirmationToggleOnIntegrationTest extends AbstractConfirmationParityIntegrationTest {

	@Autowired
	private RestTemplate restTemplate;

	private MockRestServiceServer confirmationService;

	@BeforeEach
	void bindConfirmationService() {
		confirmationService = MockRestServiceServer.bindTo(restTemplate).build();
	}

	@Override
	protected void expectConfirmation(final String counterpartyName, final BigDecimal creditAmount) {
		confirmationService.expect(requestTo("http://localhost:8070/confirmations/"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json(String.format("{\"counterpartyName\":\"%s\",\"creditAmount\":%s}",
						counterpartyName, creditAmount.toPlainString())))
				.andRespond(withStatus(HttpStatus.CREATED));
	}

	@Override
	protected void verifyConfirmationSent() {
		confirmationService.verify();
	}

	@Override
	protected void verifyNoConfirmationSent() {
		confirmationService.verify();
	}
}
