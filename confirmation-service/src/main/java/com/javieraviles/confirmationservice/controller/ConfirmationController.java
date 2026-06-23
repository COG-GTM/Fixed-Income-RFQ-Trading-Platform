package com.javieraviles.confirmationservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.javieraviles.confirmationservice.dto.TradeConfirmationDto;

@RestController
class ConfirmationController {

	Logger logger = LoggerFactory.getLogger(ConfirmationController.class);

	@PostMapping("/confirmations/")
	ResponseEntity<Void> createConfirmation(@RequestBody TradeConfirmationDto confirmation) {
		logger.info("Trade confirmation: counterparty {} credit updated, amount {}",
				confirmation.getCounterpartyName(), confirmation.getCreditAmount());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

}
