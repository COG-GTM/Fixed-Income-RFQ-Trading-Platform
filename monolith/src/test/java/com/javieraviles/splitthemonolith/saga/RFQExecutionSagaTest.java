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

	private static final long CP_ID = 1L;
	private static final long BOND_ID = 2L;

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
		counterparty = new Counterparty("Acme Asset Management", "549300EXAMPLE12345678",
				new BigDecimal("2000000.00"));
		bond = new Bond("US912828YK15", "US Treasury", new BigDecimal("2.7500"),
				LocalDate.of(2030, 11, 15), new BigDecimal("5000000.00"));
	}

	private RfqDto rfq(final String notional, final String price) {
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(CP_ID);
		dto.setBondId(BOND_ID);
		dto.setNotionalAmount(new BigDecimal(notional));
		dto.setExecutionPrice(new BigDecimal(price));
		dto.setSide(Side.BUY);
		return dto;
	}

	@Test
	void executeRfq_deductsNotionalAndCreditAndSavesExecutedRfq() {
		when(bondRepository.findById(BOND_ID)).thenReturn(Optional.of(bond));
		when(counterpartyRepository.findById(CP_ID)).thenReturn(Optional.of(counterparty));
		when(rfqRepository.save(any(Rfq.class))).thenAnswer(inv -> inv.getArgument(0));

		final Rfq result = saga.executeRfq(rfq("1000000.00", "998750.00"));

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("4000000.00");
		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("1001250.00");
		assertThat(result.getStatus()).isEqualTo(RfqStatus.EXECUTED);
		assertThat(result.getSide()).isEqualTo(Side.BUY);
		assertThat(result.getBond()).isSameAs(bond);
		assertThat(result.getCounterparty()).isSameAs(counterparty);
		assertThat(result.getNotionalAmount()).isEqualByComparingTo("1000000.00");
		assertThat(result.getExecutionPrice()).isEqualByComparingTo("998750.00");
	}

	@Test
	void executeRfq_withInsufficientNotional_throwsBeforeTouchingCredit() {
		when(bondRepository.findById(BOND_ID)).thenReturn(Optional.of(bond));
		when(counterpartyRepository.findById(CP_ID)).thenReturn(Optional.of(counterparty));

		assertThatThrownBy(() -> saga.executeRfq(rfq("6000000.00", "5990000.00")))
				.isInstanceOf(InsufficientNotionalException.class);

		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("2000000.00");
		verify(rfqRepository, never()).save(any());
	}

	@Test
	void executeRfq_withInsufficientCredit_throwsAndDoesNotSave() {
		when(bondRepository.findById(BOND_ID)).thenReturn(Optional.of(bond));
		when(counterpartyRepository.findById(CP_ID)).thenReturn(Optional.of(counterparty));

		assertThatThrownBy(() -> saga.executeRfq(rfq("3000000.00", "2995000.00")))
				.isInstanceOf(InsufficientCreditException.class);

		verify(rfqRepository, never()).save(any());
	}

	@Test
	void executeRfq_withUnknownBond_throwsNotFound() {
		when(bondRepository.findById(BOND_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> saga.executeRfq(rfq("1000000.00", "998750.00")))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(rfqRepository, never()).save(any());
	}

	@Test
	void executeRfq_withUnknownCounterparty_throwsNotFoundAndLeavesNotionalUnchanged() {
		when(bondRepository.findById(BOND_ID)).thenReturn(Optional.of(bond));
		when(counterpartyRepository.findById(CP_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> saga.executeRfq(rfq("1000000.00", "998750.00")))
				.isInstanceOf(ResourceNotFoundException.class);

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("5000000.00");
		verify(rfqRepository, never()).save(any());
	}
}
