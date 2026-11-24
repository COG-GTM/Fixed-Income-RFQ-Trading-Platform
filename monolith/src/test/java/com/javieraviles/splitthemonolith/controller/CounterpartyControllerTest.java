package com.javieraviles.splitthemonolith.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.restclient.TradeConfirmationMicroserviceClient;
import com.javieraviles.splitthemonolith.service.TradeConfirmationService;

@ExtendWith(MockitoExtension.class)
class CounterpartyControllerTest {

	private static final long ID = 1L;

	@Mock
	private CounterpartyRepository repository;

	@Mock
	private TradeConfirmationService tradeConfirmationService;

	@Mock
	private TradeConfirmationMicroserviceClient confirmationMsClient;

	@InjectMocks
	private CounterpartyController controller;

	private Counterparty counterparty;

	@BeforeEach
	void setUp() {
		counterparty = new Counterparty("Acme Asset Management", "549300EXAMPLE12345678",
				new BigDecimal("1000000.00"));
	}

	private static Map<String, String> body(final String operation, final String amount) {
		final Map<String, String> body = new HashMap<>();
		body.put("operation", operation);
		body.put("amount", amount);
		return body;
	}

	private void givenExistingCounterpartySaved() {
		when(repository.findById(ID)).thenReturn(Optional.of(counterparty));
		when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void patchAdd_withToggleOff_usesInProcessConfirmationService() {
		ReflectionTestUtils.setField(controller, "useConfirmationService", false);
		givenExistingCounterpartySaved();

		final ResponseEntity<?> response = controller.partialUpdateGeneric(body("ADD", "250000.00"), ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("1250000.00");
		final ArgumentCaptor<TradeConfirmationDto> captor = ArgumentCaptor.forClass(TradeConfirmationDto.class);
		verify(tradeConfirmationService).sendTradeConfirmation(captor.capture());
		assertThat(captor.getValue().getCounterpartyName()).isEqualTo("Acme Asset Management");
		assertThat(captor.getValue().getCreditAmount()).isEqualByComparingTo("250000.00");
		verify(confirmationMsClient, never()).sendConfirmation(any());
	}

	@Test
	void patchAdd_withToggleOn_usesConfirmationMicroservice() {
		ReflectionTestUtils.setField(controller, "useConfirmationService", true);
		givenExistingCounterpartySaved();

		controller.partialUpdateGeneric(body("ADD", "250000.00"), ID);

		verify(confirmationMsClient).sendConfirmation(any(TradeConfirmationDto.class));
		verify(tradeConfirmationService, never()).sendTradeConfirmation(any());
	}

	@Test
	void patchDeduct_deductsCreditWithoutSendingConfirmation() {
		givenExistingCounterpartySaved();

		final ResponseEntity<?> response = controller.partialUpdateGeneric(body("DEDUCT", "400000.00"), ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("600000.00");
		verify(tradeConfirmationService, never()).sendTradeConfirmation(any());
		verify(confirmationMsClient, never()).sendConfirmation(any());
	}

	@Test
	void patchDeduct_aboveAvailableCredit_throwsAndDoesNotSave() {
		when(repository.findById(ID)).thenReturn(Optional.of(counterparty));

		assertThatThrownBy(() -> controller.partialUpdateGeneric(body("DEDUCT", "1000000.01"), ID))
				.isInstanceOf(InsufficientCreditException.class);

		verify(repository, never()).save(any());
	}

	@Test
	void patch_withUnknownOperation_returnsBadRequest() {
		when(repository.findById(ID)).thenReturn(Optional.of(counterparty));

		final ResponseEntity<?> response = controller.partialUpdateGeneric(body("MULTIPLY", "1.00"), ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isEqualTo("wrong operation");
		verify(repository, never()).save(any());
	}

	@Test
	void patch_withNonNumericAmount_returnsBadRequest() {
		when(repository.findById(ID)).thenReturn(Optional.of(counterparty));

		final ResponseEntity<?> response = controller.partialUpdateGeneric(body("ADD", "lots"), ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		verify(repository, never()).save(any());
	}

	@Test
	void patch_withUnknownId_throwsNotFound() {
		when(repository.findById(ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.partialUpdateGeneric(body("ADD", "1.00"), ID))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void put_copiesAllEditableFields() {
		givenExistingCounterpartySaved();
		final Counterparty update = new Counterparty("Acme AM Ltd", "549300UPDATED0000001",
				new BigDecimal("9000000.00"));
		update.setAvailableCredit(new BigDecimal("8000000.00"));

		final Counterparty result = controller.updateCounterparty(update, ID).getBody();

		assertThat(result.getName()).isEqualTo("Acme AM Ltd");
		assertThat(result.getLei()).isEqualTo("549300UPDATED0000001");
		assertThat(result.getCreditLimit()).isEqualByComparingTo("9000000.00");
		assertThat(result.getAvailableCredit()).isEqualByComparingTo("8000000.00");
	}

	@Test
	void getOne_withUnknownId_throwsNotFound() {
		when(repository.findById(ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.getOne(ID)).isInstanceOf(ResourceNotFoundException.class);
	}
}
