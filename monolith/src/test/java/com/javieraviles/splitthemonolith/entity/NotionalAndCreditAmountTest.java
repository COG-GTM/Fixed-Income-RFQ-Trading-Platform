package com.javieraviles.splitthemonolith.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.javieraviles.splitthemonolith.exception.NonPositiveAmountException;

public class NotionalAndCreditAmountTest {

	private static final BigDecimal START = new BigDecimal("1000000.00");

	private static Bond bond() {
		return new Bond("US912828ZZ00", "US Treasury", new BigDecimal("2.0000"), LocalDate.of(2030, 1, 15), START);
	}

	private static Counterparty counterparty() {
		return new Counterparty("Test Fund", "549300TESTFUND00001", START);
	}

	@Test
	public void deductNotional_rejectsNegativeZeroAndNull() {
		final Bond bond = bond();
		assertThrows(NonPositiveAmountException.class, () -> bond.deductNotional(new BigDecimal("-50000000")));
		assertThrows(NonPositiveAmountException.class, () -> bond.deductNotional(BigDecimal.ZERO));
		assertThrows(NonPositiveAmountException.class, () -> bond.deductNotional(null));
		assertEquals(START, bond.getAvailableNotional());
	}

	@Test
	public void addNotional_rejectsNegativeZeroAndNull() {
		final Bond bond = bond();
		assertThrows(NonPositiveAmountException.class, () -> bond.addNotional(new BigDecimal("-1")));
		assertThrows(NonPositiveAmountException.class, () -> bond.addNotional(BigDecimal.ZERO));
		assertThrows(NonPositiveAmountException.class, () -> bond.addNotional(null));
		assertEquals(START, bond.getAvailableNotional());
	}

	@Test
	public void positiveNotionalUpdatesStillApply() {
		final Bond bond = bond();
		bond.deductNotional(new BigDecimal("250000.00"));
		bond.addNotional(new BigDecimal("100000.00"));
		assertEquals(new BigDecimal("850000.00"), bond.getAvailableNotional());
	}

	@Test
	public void deductAndAddCredit_rejectNonPositive() {
		final Counterparty cp = counterparty();
		assertThrows(NonPositiveAmountException.class, () -> cp.deductCredit(new BigDecimal("-1")));
		assertThrows(NonPositiveAmountException.class, () -> cp.deductCredit(BigDecimal.ZERO));
		assertThrows(NonPositiveAmountException.class, () -> cp.addCredit(new BigDecimal("-1")));
		assertThrows(NonPositiveAmountException.class, () -> cp.addCredit(null));
		assertEquals(START, cp.getAvailableCredit());
	}
}
