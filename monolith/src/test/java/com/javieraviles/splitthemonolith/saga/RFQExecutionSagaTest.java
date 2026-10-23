package com.javieraviles.splitthemonolith.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

	private static final long COUNTERPARTY_ID = 10L;
	private static final long BOND_ID = 20L;

	@Mock
	private RfqRepository rfqRepository;

	@Mock
	private CounterpartyRepository counterpartyRepository;

	@Mock
	private BondRepository bondRepository;

	@InjectMocks
	private RFQExecutionSaga saga;

	private Counterparty counterparty;
	private Bond bond;

	@BeforeEach
	void setUp() {
		counterparty = new Counterparty("Northwind Capital", "549300NORTHWIND00001", new BigDecimal("10000000.00"));
		bond = new Bond("US912828TS01", "US Treasury", new BigDecimal("2.5000"), LocalDate.of(2031, 5, 15),
				new BigDecimal("20000000.00"));
	}

	@ParameterizedTest
	@EnumSource(Side.class)
	void executesRfqForEitherSide_deductingNotionalAndCredit(final Side side) {
		givenBondAndCounterpartyExist();
		when(rfqRepository.save(any(Rfq.class))).thenAnswer(invocation -> invocation.getArgument(0));

		final Rfq result = saga.executeRfq(rfq(side, "5000000.00", "4987500.00"));

		assertThat(result.getStatus()).isEqualTo(RfqStatus.EXECUTED);
		assertThat(result.getSide()).isEqualTo(side);
		assertThat(result.getCounterparty()).isSameAs(counterparty);
		assertThat(result.getBond()).isSameAs(bond);
		assertThat(result.getNotionalAmount()).isEqualByComparingTo("5000000.00");
		assertThat(result.getExecutionPrice()).isEqualByComparingTo("4987500.00");
		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("15000000.00");
		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("5012500.00");
	}

	@Test
	void persistsExactlyOneExecutedRfq() {
		givenBondAndCounterpartyExist();

		saga.executeRfq(rfq(Side.BUY, "1000000.00", "998750.00"));

		final ArgumentCaptor<Rfq> saved = ArgumentCaptor.forClass(Rfq.class);
		verify(rfqRepository).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(RfqStatus.EXECUTED);
	}

	@Test
	void executesWhenNotionalAndCreditAreExactlyExhausted() {
		givenBondAndCounterpartyExist();

		saga.executeRfq(rfq(Side.BUY, "20000000.00", "10000000.00"));

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo(BigDecimal.ZERO);
		verify(rfqRepository).save(any(Rfq.class));
	}

	@Test
	void rejectsQuote_whenNotionalExceedsBondInventory() {
		givenBondAndCounterpartyExist();

		assertThatThrownBy(() -> saga.executeRfq(rfq(Side.BUY, "20000000.01", "1000000.00")))
				.isInstanceOf(InsufficientNotionalException.class);

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("20000000.00");
		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("10000000.00");
		verify(rfqRepository, never()).save(any());
	}

	@Test
	void rejectsQuote_whenExecutionPriceExceedsAvailableCredit() {
		givenBondAndCounterpartyExist();

		assertThatThrownBy(() -> saga.executeRfq(rfq(Side.SELL, "1000000.00", "10000000.01")))
				.isInstanceOf(InsufficientCreditException.class);

		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("10000000.00");
		verify(rfqRepository, never()).save(any());
	}

	@Test
	void rejectsQuote_whenBondIsUnknown() {
		when(bondRepository.findById(BOND_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> saga.executeRfq(rfq(Side.BUY, "1000000.00", "998750.00")))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(counterpartyRepository, never()).findById(anyLong());
		verify(rfqRepository, never()).save(any());
	}

	@Test
	void rejectsQuote_whenCounterpartyIsUnknown() {
		when(bondRepository.findById(BOND_ID)).thenReturn(Optional.of(bond));
		when(counterpartyRepository.findById(COUNTERPARTY_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> saga.executeRfq(rfq(Side.BUY, "1000000.00", "998750.00")))
				.isInstanceOf(ResourceNotFoundException.class);

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("20000000.00");
		verify(rfqRepository, never()).save(any());
	}

	private void givenBondAndCounterpartyExist() {
		when(bondRepository.findById(BOND_ID)).thenReturn(Optional.of(bond));
		when(counterpartyRepository.findById(COUNTERPARTY_ID)).thenReturn(Optional.of(counterparty));
	}

	private static RfqDto rfq(final Side side, final String notional, final String executionPrice) {
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(COUNTERPARTY_ID);
		dto.setBondId(BOND_ID);
		dto.setSide(side);
		dto.setNotionalAmount(new BigDecimal(notional));
		dto.setExecutionPrice(new BigDecimal(executionPrice));
		return dto;
	}
}
