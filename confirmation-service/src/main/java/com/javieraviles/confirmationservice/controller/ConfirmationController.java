package com.javieraviles.confirmationservice.controller;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.javieraviles.confirmationservice.dto.TradeConfirmationDto;
import com.javieraviles.confirmationservice.entity.TradeConfirmation;
import com.javieraviles.confirmationservice.service.TradeConfirmationService;

@RestController
class ConfirmationController {

	@Autowired
	private TradeConfirmationService service;

	@PostMapping("/confirmations")
	ResponseEntity<TradeConfirmation> confirm(@Valid @RequestBody TradeConfirmationDto confirmation) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.confirm(confirmation));
	}

	@GetMapping("/confirmations")
	List<TradeConfirmation> getAll() {
		return service.findAll();
	}

	@GetMapping("/confirmations/{id}")
	ResponseEntity<TradeConfirmation> getOne(@PathVariable Long id) {
		return ResponseEntity.ok(service.findById(id));
	}
}
