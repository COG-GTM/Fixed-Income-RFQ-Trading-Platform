package com.javieraviles.splitthemonolith.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ConcurrencyExceptionHandler {

	@ExceptionHandler({ OptimisticLockingFailureException.class, PessimisticLockingFailureException.class })
	ResponseEntity<String> handleConcurrentModification(final RuntimeException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body("Concurrent modification, please retry");
	}
}
