package com.javieraviles.splitthemonolith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import com.javieraviles.splitthemonolith.confirmation.LocalTradeConfirmationSender;
import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

/**
 * Toggle off: confirmations stay inside the monolith.
 */
@SpringBootTest(properties = { "use.confirmation.service=false",
		"spring.datasource.generate-unique-name=true" })
class ConfirmationToggleOffIntegrationTest extends AbstractConfirmationParityIntegrationTest {

	@SpyBean
	private LocalTradeConfirmationSender localSender;

	private String expectedCounterpartyName;

	private BigDecimal expectedCreditAmount;

	@Override
	protected void expectConfirmation(final String counterpartyName, final BigDecimal creditAmount) {
		this.expectedCounterpartyName = counterpartyName;
		this.expectedCreditAmount = creditAmount;
	}

	@Override
	protected void verifyConfirmationSent() {
		final ArgumentCaptor<TradeConfirmationDto> captor = ArgumentCaptor.forClass(TradeConfirmationDto.class);
		verify(localSender).sendTradeConfirmation(captor.capture());
		assertEquals(expectedCounterpartyName, captor.getValue().getCounterpartyName());
		assertEquals(0, expectedCreditAmount.compareTo(captor.getValue().getCreditAmount()));
	}

	@Override
	protected void verifyNoConfirmationSent() {
		verify(localSender, never()).sendTradeConfirmation(any(TradeConfirmationDto.class));
	}
}
