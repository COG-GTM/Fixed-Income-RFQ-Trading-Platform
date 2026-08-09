package com.javieraviles.creditservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import com.javieraviles.creditservice.entity.CreditAccount;
import com.javieraviles.creditservice.exception.InsufficientCreditException;

import org.junit.jupiter.api.Test;

public class CreditAccountTest {

	private static final String LEI = "549300FIDELITY00001";

	@Test
	public void whenReserveWithinLimit_thenAvailableCreditIsReduced() {
		final CreditAccount account = new CreditAccount(LEI, "Fidelity Investments", new BigDecimal("1000000.00"));

		account.reserve(new BigDecimal("250000.00"));

		assertEquals(0, new BigDecimal("750000.00").compareTo(account.getAvailableCredit()));
	}

	@Test
	public void whenReserveExactlyAvailableCredit_thenAvailableCreditIsZero() {
		final CreditAccount account = new CreditAccount(LEI, "Fidelity Investments", new BigDecimal("1000000.00"));

		account.reserve(new BigDecimal("1000000.00"));

		assertEquals(0, BigDecimal.ZERO.compareTo(account.getAvailableCredit()));
	}

	@Test
	public void whenReserveAboveAvailableCredit_thenBreachAndCreditUnchanged() {
		final CreditAccount account = new CreditAccount(LEI, "Fidelity Investments", new BigDecimal("1000000.00"));

		assertThrows(InsufficientCreditException.class, () -> account.reserve(new BigDecimal("1000000.01")));
		assertEquals(0, new BigDecimal("1000000.00").compareTo(account.getAvailableCredit()));
	}

	@Test
	public void whenRelease_thenAvailableCreditIsRestored() {
		final CreditAccount account = new CreditAccount(LEI, "Fidelity Investments", new BigDecimal("1000000.00"));

		account.reserve(new BigDecimal("400000.00"));
		account.release(new BigDecimal("400000.00"));

		assertEquals(0, new BigDecimal("1000000.00").compareTo(account.getAvailableCredit()));
	}
}
