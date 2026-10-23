package com.javieraviles.splitthemonolith.saga;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

	private static final long CP_ID = 10L;
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
		counterparty = new Counterparty("Unit Test Fund", "549300UNITTEST000001", new BigDecimal("1000000.00"));
		bond = new Bond("US0000000001", "US Treasury", new BigDecimal("2.0000"), LocalDate.of(2030, 1, 15),
				new BigDecimal("5000000.00"));
	}

	@ParameterizedTest
	@EnumSource(Side.class)
	void executesRfqForEachSide_deductsNotionalAndCredit_andSavesExecutedRfq(final Side side) {
		givenBondAndCounterpartyExist();
		when(rfqRepository.save(any(Rfq.class))).thenAnswer(inv -> inv.getArgument(0));

		final Rfq result = saga.executeRfq(rfq(side, "2000000.00", "400000.00"));

		final ArgumentCaptor<Rfq> saved = ArgumentCaptor.forClass(Rfq.class);
		verify(rfqRepository).save(saved.capture());
		assertSame(saved.getValue(), result);
		assertAll(
				() -> assertEquals(RfqStatus.EXECUTED, result.getStatus()),
				() -> assertEquals(side, result.getSide()),
				() -> assertSame(counterparty, result.getCounterparty()),
				() -> assertSame(bond, result.getBond()),
				() -> assertEquals(new BigDecimal("2000000.00"), result.getNotionalAmount()),
				() -> assertEquals(new BigDecimal("400000.00"), result.getExecutionPrice()),
				() -> assertEquals(new BigDecimal("3000000.00"), bond.getAvailableNotional()),
				() -> assertEquals(new BigDecimal("600000.00"), counterparty.getAvailableCredit()));
	}

	@Test
	void ignoresClientSuppliedStatus_andAlwaysPersistsExecuted() {
		givenBondAndCounterpartyExist();
		when(rfqRepository.save(any(Rfq.class))).thenAnswer(inv -> inv.getArgument(0));
		final RfqDto dto = rfq(Side.BUY, "1000000.00", "100000.00");
		dto.setStatus(RfqStatus.REJECTED);

		assertEquals(RfqStatus.EXECUTED, saga.executeRfq(dto).getStatus());
	}

	@Test
	void executesWhenNotionalAndPriceExactlyMatchAvailableLimits() {
		givenBondAndCounterpartyExist();
		when(rfqRepository.save(any(Rfq.class))).thenAnswer(inv -> inv.getArgument(0));

		saga.executeRfq(rfq(Side.BUY, "5000000.00", "1000000.00"));

		assertEquals(0, BigDecimal.ZERO.compareTo(bond.getAvailableNotional()));
		assertEquals(0, BigDecimal.ZERO.compareTo(counterparty.getAvailableCredit()));
	}

	@Test
	void rejectsWhenNotionalExceedsInventory_beforeTouchingCredit() {
		givenBondAndCounterpartyExist();

		assertThrows(InsufficientNotionalException.class,
				() -> saga.executeRfq(rfq(Side.BUY, "5000000.01", "100000.00")));

		assertEquals(new BigDecimal("5000000.00"), bond.getAvailableNotional());
		assertEquals(new BigDecimal("1000000.00"), counterparty.getAvailableCredit());
		verify(rfqRepository, never()).save(any());
	}

	@Test
	void rejectsWhenPriceExceedsAvailableCredit_andDoesNotSaveRfq() {
		givenBondAndCounterpartyExist();

		assertThrows(InsufficientCreditException.class,
				() -> saga.executeRfq(rfq(Side.SELL, "1000000.00", "1000000.01")));

		assertEquals(new BigDecimal("1000000.00"), counterparty.getAvailableCredit());
		verify(rfqRepository, never()).save(any());
	}

	@Test
	void rejectsUnknownBond_withoutLookingUpCounterparty() {
		when(bondRepository.findById(BOND_ID)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class,
				() -> saga.executeRfq(rfq(Side.BUY, "1000000.00", "100000.00")));

		verifyNoInteractions(counterpartyRepository, rfqRepository);
	}

	@Test
	void rejectsUnknownCounterparty_withoutDeductingNotional() {
		when(bondRepository.findById(BOND_ID)).thenReturn(Optional.of(bond));
		when(counterpartyRepository.findById(CP_ID)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class,
				() -> saga.executeRfq(rfq(Side.BUY, "1000000.00", "100000.00")));

		assertEquals(new BigDecimal("5000000.00"), bond.getAvailableNotional());
		verifyNoInteractions(rfqRepository);
	}

	private void givenBondAndCounterpartyExist() {
		when(bondRepository.findById(BOND_ID)).thenReturn(Optional.of(bond));
		when(counterpartyRepository.findById(CP_ID)).thenReturn(Optional.of(counterparty));
	}

	private static RfqDto rfq(final Side side, final String notional, final String price) {
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(CP_ID);
		dto.setBondId(BOND_ID);
		dto.setSide(side);
		dto.setNotionalAmount(new BigDecimal(notional));
		dto.setExecutionPrice(new BigDecimal(price));
		return dto;
	}
}
