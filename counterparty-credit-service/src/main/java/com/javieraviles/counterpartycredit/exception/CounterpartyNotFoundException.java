package com.javieraviles.counterpartycredit.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND)
public class CounterpartyNotFoundException extends RuntimeException {

	public CounterpartyNotFoundException() {
	}

	public CounterpartyNotFoundException(final long id) {
		super("Counterparty not found: " + id);
	}
}
