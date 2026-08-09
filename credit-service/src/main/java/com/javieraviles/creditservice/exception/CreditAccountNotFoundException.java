package com.javieraviles.creditservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Credit account not found")
public class CreditAccountNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;
}
