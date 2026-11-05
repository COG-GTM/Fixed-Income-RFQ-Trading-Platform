package com.javieraviles.splitthemonolith.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.InvalidAmountException;

public class CounterpartyTest {

	private static Counterparty newCounterparty() {
		return new Counterparty("Acme Asset Management", "549300ACME0000000001", new BigDecimal("1000.00"));
	}

	@Test
	public void deductCredit_withNegativeAmount_throwsAndLeavesBalanceUnchanged() {
		final Counterparty cp = newCounterparty();
		assertThrows(InvalidAmountException.class, () -> cp.deductCredit(new BigDecimal("-10000000")));
		assertEquals(new BigDecimal("1000.00"), cp.getAvailableCredit());
	}

	@Test
	public void deductCredit_withZeroAmount_throws() {
		final Counterparty cp = newCounterparty();
		assertThrows(InvalidAmountException.class, () -> cp.deductCredit(BigDecimal.ZERO));
		assertEquals(new BigDecimal("1000.00"), cp.getAvailableCredit());
	}

	@Test
	public void deductCredit_withNullAmount_throws() {
		final Counterparty cp = newCounterparty();
		assertThrows(InvalidAmountException.class, () -> cp.deductCredit(null));
	}

	@Test
	public void addCredit_withNegativeAmount_throwsAndLeavesBalanceUnchanged() {
		final Counterparty cp = newCounterparty();
		assertThrows(InvalidAmountException.class, () -> cp.addCredit(new BigDecimal("-1.00")));
		assertEquals(new BigDecimal("1000.00"), cp.getAvailableCredit());
	}

	@Test
	public void deductCredit_withPositiveAmount_reducesBalance() {
		final Counterparty cp = newCounterparty();
		cp.deductCredit(new BigDecimal("250.00"));
		assertEquals(new BigDecimal("750.00"), cp.getAvailableCredit());
	}

	@Test
	public void deductCredit_aboveAvailable_stillThrowsInsufficientCredit() {
		final Counterparty cp = newCounterparty();
		assertThrows(InsufficientCreditException.class, () -> cp.deductCredit(new BigDecimal("1000.01")));
	}

	@Test
	public void addCredit_withPositiveAmount_increasesBalance() {
		final Counterparty cp = newCounterparty();
		cp.addCredit(new BigDecimal("1.50"));
		assertEquals(new BigDecimal("1001.50"), cp.getAvailableCredit());
	}
}
