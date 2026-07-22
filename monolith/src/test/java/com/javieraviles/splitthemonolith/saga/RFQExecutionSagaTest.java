package com.javieraviles.splitthemonolith.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.InsufficientNotionalException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

@ExtendWith(MockitoExtension.class)
class RFQExecutionSagaTest {

	@Mock
	private RfqRepository rfqRepository;

	@Mock
	private CounterpartyRepository counterpartyRepository;

	@Mock
	private BondRepository bondRepository;

	@InjectMocks
	private RFQExecutionSaga saga;

	private RfqDto rfqDto;

	@BeforeEach
	void setUp() {
		rfqDto = new RfqDto();
		rfqDto.setBondId(1L);
		rfqDto.setCounterpartyId(2L);
		rfqDto.setNotionalAmount(new BigDecimal("1000000.00"));
		rfqDto.setSide(Side.BUY);
		rfqDto.setExecutionPrice(new BigDecimal("998750.00"));
	}

	private Bond bondWithNotional(final String notional) {
		return new Bond("US912828YK15", "US Treasury", new BigDecimal("2.7500"),
				LocalDate.of(2030, 11, 15), new BigDecimal(notional));
	}

	private Counterparty counterpartyWithCredit(final String credit) {
		return new Counterparty("Acme Asset Management", "549300EXAMPLE12345678",
				new BigDecimal(credit));
	}

	@Test
	void executeRfq_happyPath_deductsInventoryAndPersistsExecutedRfq() {
		final Bond bond = bondWithNotional("100000000.00");
		final Counterparty counterparty = counterpartyWithCredit("50000000.00");
		when(bondRepository.findById(1L)).thenReturn(Optional.of(bond));
		when(counterpartyRepository.findById(2L)).thenReturn(Optional.of(counterparty));
		when(rfqRepository.save(any(Rfq.class))).thenAnswer(inv -> inv.getArgument(0));

		final Rfq result = saga.executeRfq(rfqDto);

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("99000000.00");
		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("49001250.00");

		final ArgumentCaptor<Rfq> captor = ArgumentCaptor.forClass(Rfq.class);
		verify(rfqRepository).save(captor.capture());
		final Rfq saved = captor.getValue();
		assertThat(saved.getStatus()).isEqualTo(RfqStatus.EXECUTED);
		assertThat(saved.getSide()).isEqualTo(Side.BUY);
		assertThat(saved.getBond()).isSameAs(bond);
		assertThat(saved.getCounterparty()).isSameAs(counterparty);
		assertThat(saved.getNotionalAmount()).isEqualByComparingTo("1000000.00");
		assertThat(saved.getExecutionPrice()).isEqualByComparingTo("998750.00");
		assertThat(result).isSameAs(saved);
	}

	@Test
	void executeRfq_bondNotFound_throwsResourceNotFound() {
		when(bondRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> saga.executeRfq(rfqDto))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(counterpartyRepository, never()).findById(any());
		verify(rfqRepository, never()).save(any());
	}

	@Test
	void executeRfq_counterpartyNotFound_throwsResourceNotFound() {
		when(bondRepository.findById(1L)).thenReturn(Optional.of(bondWithNotional("100000000.00")));
		when(counterpartyRepository.findById(2L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> saga.executeRfq(rfqDto))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(rfqRepository, never()).save(any());
	}

	@Test
	void executeRfq_insufficientNotional_throwsAndDoesNotPersist() {
		when(bondRepository.findById(1L)).thenReturn(Optional.of(bondWithNotional("500000.00")));
		when(counterpartyRepository.findById(2L)).thenReturn(Optional.of(counterpartyWithCredit("50000000.00")));

		assertThatThrownBy(() -> saga.executeRfq(rfqDto))
				.isInstanceOf(InsufficientNotionalException.class);

		verify(rfqRepository, never()).save(any());
	}

	@Test
	void executeRfq_insufficientCredit_throwsAndDoesNotPersist() {
		final Bond bond = bondWithNotional("100000000.00");
		when(bondRepository.findById(1L)).thenReturn(Optional.of(bond));
		when(counterpartyRepository.findById(2L)).thenReturn(Optional.of(counterpartyWithCredit("500000.00")));

		assertThatThrownBy(() -> saga.executeRfq(rfqDto))
				.isInstanceOf(InsufficientCreditException.class);

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("99000000.00");
		verify(rfqRepository, never()).save(any());
	}
}
