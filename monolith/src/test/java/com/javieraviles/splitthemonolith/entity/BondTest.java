package com.javieraviles.splitthemonolith.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.javieraviles.splitthemonolith.exception.InsufficientNotionalException;

class BondTest {

	private Bond bond;

	@BeforeEach
	void setUp() {
		bond = new Bond("US912828YK15", "US Treasury", new BigDecimal("2.7500"),
				LocalDate.of(2030, 11, 15), new BigDecimal("1000000.00"));
	}

	@Test
	void addNotional_increasesAvailableNotional() {
		bond.addNotional(new BigDecimal("250000.00"));

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("1250000.00");
	}

	@Test
	void deductNotional_decreasesAvailableNotional() {
		bond.deductNotional(new BigDecimal("400000.00"));

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("600000.00");
	}

	@Test
	void deductNotional_ofEntireBalance_leavesZero() {
		bond.deductNotional(new BigDecimal("1000000.00"));

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void deductNotional_aboveAvailable_throwsAndLeavesBalanceUnchanged() {
		assertThatThrownBy(() -> bond.deductNotional(new BigDecimal("1000000.01")))
				.isInstanceOf(InsufficientNotionalException.class);

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("1000000.00");
	}

	@Test
	void constructor_setsAllFields() {
		assertThat(bond.getIsin()).isEqualTo("US912828YK15");
		assertThat(bond.getIssuer()).isEqualTo("US Treasury");
		assertThat(bond.getCouponRate()).isEqualByComparingTo("2.7500");
		assertThat(bond.getMaturityDate()).isEqualTo(LocalDate.of(2030, 11, 15));
	}
}
