package com.javieraviles.confirmationservice.controller;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.javieraviles.confirmationservice.dto.TradeConfirmationDto;

@RestController
public class ConfirmationController {

	private final Logger logger = LoggerFactory.getLogger(ConfirmationController.class);

	@PostMapping("/confirmations/")
	public ResponseEntity<Void> createConfirmation(@Valid @RequestBody final TradeConfirmationDto confirmation) {
		logger.info("Trade confirmation received: counterparty {} credit updated, amount {}",
				confirmation.getCounterpartyName(), confirmation.getCreditAmount());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

}
