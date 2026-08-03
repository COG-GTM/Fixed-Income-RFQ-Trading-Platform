package com.javieraviles.confirmationservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Trade confirmation not found")
public class ResourceNotFoundException extends RuntimeException {

}
