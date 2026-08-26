package com.javieraviles.splitthemonolith.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.InsufficientNotionalException;

import org.junit.jupiter.api.Test;

/**
 * Boundary coverage for the credit risk and inventory rules enforced by the
 * domain entities that back the RFQ execution saga.
 */
public class CreditAndInventoryTest {

	private static Counterparty counterparty(final String creditLimit) {
		return new Counterparty("Edge Case Fund", "549300EDGE0000000001", new BigDecimal(creditLimit));
	}

	private static Bond bond(final String availableNotional) {
		return new Bond("US000000EDGE", "US Treasury", new BigDecimal("2.0000"),
				LocalDate.of(2030, 1, 31), new BigDecimal(availableNotional));
	}

	@Test
	public void givenNewCounterparty_thenAvailableCreditEqualsCreditLimit() {
		final Counterparty cp = counterparty("5000000.00");
		assertEquals(0, cp.getCreditLimit().compareTo(cp.getAvailableCredit()));
	}

	@Test
	public void givenAmountBelowAvailableCredit_whenDeduct_thenCreditReduced() {
		final Counterparty cp = counterparty("5000000.00");
		cp.deductCredit(new BigDecimal("1500000.00"));
		assertEquals(0, new BigDecimal("3500000.00").compareTo(cp.getAvailableCredit()));
	}

	@Test
	public void givenAmountEqualToAvailableCredit_whenDeduct_thenCreditIsZero() {
		final Counterparty cp = counterparty("5000000.00");
		cp.deductCredit(new BigDecimal("5000000.00"));
		assertEquals(0, BigDecimal.ZERO.compareTo(cp.getAvailableCredit()));
	}

	@Test
	public void givenAmountAboveAvailableCredit_whenDeduct_thenBreachRejectedAndCreditUnchanged() {
		final Counterparty cp = counterparty("5000000.00");
		assertThrows(InsufficientCreditException.class, () -> cp.deductCredit(new BigDecimal("5000000.01")));
		assertEquals(0, new BigDecimal("5000000.00").compareTo(cp.getAvailableCredit()));
	}

	@Test
	public void givenExhaustedCredit_whenCreditAddedBack_thenFurtherDeductionSucceeds() {
		final Counterparty cp = counterparty("1000000.00");
		cp.deductCredit(new BigDecimal("1000000.00"));
		assertThrows(InsufficientCreditException.class, () -> cp.deductCredit(new BigDecimal("1.00")));

		cp.addCredit(new BigDecimal("250000.00"));
		cp.deductCredit(new BigDecimal("250000.00"));
		assertEquals(0, BigDecimal.ZERO.compareTo(cp.getAvailableCredit()));
	}

	@Test
	public void givenAmountBelowAvailableNotional_whenDeduct_thenInventoryReduced() {
		final Bond b = bond("10000000.00");
		b.deductNotional(new BigDecimal("2500000.00"));
		assertEquals(0, new BigDecimal("7500000.00").compareTo(b.getAvailableNotional()));
	}

	@Test
	public void givenAmountEqualToAvailableNotional_whenDeduct_thenInventoryIsZero() {
		final Bond b = bond("10000000.00");
		b.deductNotional(new BigDecimal("10000000.00"));
		assertEquals(0, BigDecimal.ZERO.compareTo(b.getAvailableNotional()));
	}

	@Test
	public void givenAmountAboveAvailableNotional_whenDeduct_thenRejectedAndInventoryUnchanged() {
		final Bond b = bond("10000000.00");
		assertThrows(InsufficientNotionalException.class, () -> b.deductNotional(new BigDecimal("10000000.01")));
		assertEquals(0, new BigDecimal("10000000.00").compareTo(b.getAvailableNotional()));
	}

	@Test
	public void givenReplenishedInventory_whenDeducted_thenInventoryReflectsBothLegs() {
		final Bond b = bond("1000000.00");
		b.addNotional(new BigDecimal("500000.00"));
		b.deductNotional(new BigDecimal("1200000.00"));
		assertEquals(0, new BigDecimal("300000.00").compareTo(b.getAvailableNotional()));
	}
}
