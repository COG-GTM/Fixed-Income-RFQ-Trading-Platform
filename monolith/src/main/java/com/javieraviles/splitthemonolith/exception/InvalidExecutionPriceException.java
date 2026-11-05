package com.javieraviles.splitthemonolith.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Execution price outside allowed range")
public class InvalidExecutionPriceException extends RuntimeException {

}
