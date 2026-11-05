package com.javieraviles.splitthemonolith.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "A counterparty with this LEI already exists")
public class DuplicateLeiException extends RuntimeException {

}
