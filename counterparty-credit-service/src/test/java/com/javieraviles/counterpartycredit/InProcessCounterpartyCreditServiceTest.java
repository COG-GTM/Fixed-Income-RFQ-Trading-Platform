package com.javieraviles.counterpartycredit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.javieraviles.counterpartycredit.domain.Counterparty;
import com.javieraviles.counterpartycredit.exception.CounterpartyNotFoundException;
import com.javieraviles.counterpartycredit.exception.InsufficientCreditException;
import com.javieraviles.counterpartycredit.repository.CounterpartyRepository;

@ExtendWith(MockitoExtension.class)
class InProcessCounterpartyCreditServiceTest {

	@Mock
	private CounterpartyRepository repository;

	@InjectMocks
	private InProcessCounterpartyCreditService service;

	private Counterparty acme;

	@BeforeEach
	void setUp() {
		acme = new Counterparty("Acme Asset Management", "549300EXAMPLE12345678", new BigDecimal("50000000.00"));
	}

	@Test
	void deductCredit_reducesAvailableCredit() {
		when(repository.findById(1L)).thenReturn(Optional.of(acme));
		when(repository.save(any(Counterparty.class))).thenAnswer(invocation -> invocation.getArgument(0));

		final Counterparty result = service.deductCredit(1L, new BigDecimal("5000000.00"));

		assertEquals(new BigDecimal("45000000.00"), result.getAvailableCredit());
		assertEquals(new BigDecimal("50000000.00"), result.getCreditLimit());
		verify(repository).save(acme);
	}

	@Test
	void deductCredit_withInsufficientCredit_isRejectedAndNotPersisted() {
		when(repository.findById(2L)).thenReturn(Optional.of(acme));

		assertThrows(InsufficientCreditException.class,
				() -> service.deductCredit(2L, new BigDecimal("50000000.01")));

		assertEquals(new BigDecimal("50000000.00"), acme.getAvailableCredit());
		verify(repository, never()).save(any(Counterparty.class));
	}

	@Test
	void deductCredit_forMissingCounterparty_throwsNotFound() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(CounterpartyNotFoundException.class,
				() -> service.deductCredit(99L, new BigDecimal("1.00")));
		verify(repository, never()).save(any(Counterparty.class));
	}

	@Test
	void addCredit_increasesAvailableCredit() {
		when(repository.findById(1L)).thenReturn(Optional.of(acme));
		when(repository.save(any(Counterparty.class))).thenAnswer(invocation -> invocation.getArgument(0));

		final Counterparty result = service.addCredit(1L, new BigDecimal("1000000.00"));

		assertEquals(new BigDecimal("51000000.00"), result.getAvailableCredit());
	}

	@Test
	void findByLei_resolvesCounterparty() {
		when(repository.findByLei("549300EXAMPLE12345678")).thenReturn(Optional.of(acme));

		final Optional<Counterparty> resolved = service.findByLei("549300EXAMPLE12345678");

		assertTrue(resolved.isPresent());
		assertSame(acme, resolved.get());
		assertEquals("Acme Asset Management", resolved.get().getName());
	}

	@Test
	void findByLei_returnsEmptyForUnknownLei() {
		when(repository.findByLei("000000UNKNOWN0000000")).thenReturn(Optional.empty());

		assertTrue(service.findByLei("000000UNKNOWN0000000").isEmpty());
	}

	@Test
	void findById_forMissingCounterparty_throwsNotFound() {
		when(repository.findById(404L)).thenReturn(Optional.empty());

		assertThrows(CounterpartyNotFoundException.class, () -> service.findById(404L));
	}
}
