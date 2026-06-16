package com.javieraviles.counterpartycredit.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Insufficient credit")
public class InsufficientCreditException extends RuntimeException {

}
