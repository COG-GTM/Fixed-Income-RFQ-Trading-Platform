package com.javieraviles.splitthemonolith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.exception.InvalidAmountException;

import org.junit.jupiter.api.Test;

public class EntityAmountValidationTest {

	private final Bond bond = new Bond("US912828XX00", "US Treasury", new BigDecimal("2.0000"),
			LocalDate.of(2030, 1, 15), new BigDecimal("1000.00"));

	private final Counterparty counterparty = new Counterparty("Validation Fund", "549300VALIDATION0001",
			new BigDecimal("1000.00"));

	@Test
	public void deductNotional_rejectsNonPositiveAmounts() {
		assertThrows(InvalidAmountException.class, () -> bond.deductNotional(new BigDecimal("-500.00")));
		assertThrows(InvalidAmountException.class, () -> bond.deductNotional(BigDecimal.ZERO));
		assertThrows(InvalidAmountException.class, () -> bond.deductNotional(null));
		assertEquals(0, new BigDecimal("1000.00").compareTo(bond.getAvailableNotional()));
	}

	@Test
	public void addNotional_rejectsNonPositiveAmounts() {
		assertThrows(InvalidAmountException.class, () -> bond.addNotional(new BigDecimal("-500.00")));
		assertEquals(0, new BigDecimal("1000.00").compareTo(bond.getAvailableNotional()));
	}

	@Test
	public void deductCredit_rejectsNonPositiveAmounts() {
		assertThrows(InvalidAmountException.class, () -> counterparty.deductCredit(new BigDecimal("-500.00")));
		assertThrows(InvalidAmountException.class, () -> counterparty.deductCredit(BigDecimal.ZERO));
		assertThrows(InvalidAmountException.class, () -> counterparty.deductCredit(null));
		assertEquals(0, new BigDecimal("1000.00").compareTo(counterparty.getAvailableCredit()));
	}

	@Test
	public void addCredit_rejectsNonPositiveAmounts() {
		assertThrows(InvalidAmountException.class, () -> counterparty.addCredit(new BigDecimal("-500.00")));
		assertEquals(0, new BigDecimal("1000.00").compareTo(counterparty.getAvailableCredit()));
	}
}
