package com.javieraviles.creditservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Insufficient credit")
public class InsufficientCreditException extends RuntimeException {

}
