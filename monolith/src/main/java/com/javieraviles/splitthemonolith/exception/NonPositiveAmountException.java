package com.javieraviles.splitthemonolith.exception;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Amount must be positive")
public class NonPositiveAmountException extends IllegalArgumentException {

	public NonPositiveAmountException() {
		super("Amount must be positive");
	}

	public static void requirePositive(final BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new NonPositiveAmountException();
		}
	}
}
