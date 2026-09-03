package com.javieraviles.splitthemonolith.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Notional must be a multiple of the minimum lot size")
public class InvalidLotSizeException extends RuntimeException {

	private static final long serialVersionUID = 1L;
}
