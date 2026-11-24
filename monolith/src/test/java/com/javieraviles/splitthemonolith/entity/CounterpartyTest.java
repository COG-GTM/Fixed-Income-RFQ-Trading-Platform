package com.javieraviles.splitthemonolith.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;

class CounterpartyTest {

	private Counterparty counterparty;

	@BeforeEach
	void setUp() {
		counterparty = new Counterparty("Acme Asset Management", "549300EXAMPLE12345678",
				new BigDecimal("5000000.00"));
	}

	@Test
	void constructor_initialisesAvailableCreditToCreditLimit() {
		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("5000000.00");
		assertThat(counterparty.getCreditLimit()).isEqualByComparingTo("5000000.00");
	}

	@Test
	void addCredit_increasesAvailableCredit() {
		counterparty.addCredit(new BigDecimal("1000000.00"));

		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("6000000.00");
	}

	@Test
	void deductCredit_decreasesAvailableCredit() {
		counterparty.deductCredit(new BigDecimal("1500000.00"));

		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("3500000.00");
	}

	@Test
	void deductCredit_ofEntireBalance_leavesZero() {
		counterparty.deductCredit(new BigDecimal("5000000.00"));

		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void deductCredit_aboveAvailable_throwsAndLeavesBalanceUnchanged() {
		assertThatThrownBy(() -> counterparty.deductCredit(new BigDecimal("5000000.01")))
				.isInstanceOf(InsufficientCreditException.class);

		assertThat(counterparty.getAvailableCredit()).isEqualByComparingTo("5000000.00");
	}

	@Test
	void prePersist_withNoAvailableCredit_defaultsToCreditLimit() {
		final Counterparty fromJson = new Counterparty();
		fromJson.setCreditLimit(new BigDecimal("750000.00"));

		ReflectionTestUtils.invokeMethod(fromJson, "initAvailableCredit");

		assertThat(fromJson.getAvailableCredit()).isEqualByComparingTo("750000.00");
	}

	@Test
	void prePersist_withExplicitAvailableCredit_keepsIt() {
		final Counterparty fromJson = new Counterparty();
		fromJson.setCreditLimit(new BigDecimal("750000.00"));
		fromJson.setAvailableCredit(new BigDecimal("100.00"));

		ReflectionTestUtils.invokeMethod(fromJson, "initAvailableCredit");

		assertThat(fromJson.getAvailableCredit()).isEqualByComparingTo("100.00");
	}
}
