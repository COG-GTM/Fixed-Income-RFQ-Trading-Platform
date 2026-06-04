package com.javieraviles.splitthemonolith.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Invalid amount")
public class InvalidAmountException extends RuntimeException {

	public InvalidAmountException(final String message) {
		super(message);
	}
}
