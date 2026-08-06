package com.javieraviles.splitthemonolith.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.javieraviles.splitthemonolith.confirmation.TradeConfirmationPort;
import com.javieraviles.splitthemonolith.dto.OperationEnum;
import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;

@RestController
class CounterpartyController {

	@Autowired
	private CounterpartyRepository repository;

	@Autowired
	private TradeConfirmationPort tradeConfirmation;

	@GetMapping("/counterparties")
	List<Counterparty> getAll() {
		return repository.findAll();
	}

	@PostMapping("/counterparties")
	ResponseEntity<Counterparty> createCounterparty(@RequestBody Counterparty newCounterparty) {
		return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(newCounterparty));
	}

	@GetMapping("/counterparties/{id}")
	ResponseEntity<Counterparty> getOne(@PathVariable Long id) {
		final Counterparty counterparty = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException());
		return ResponseEntity.ok(counterparty);
	}

	@PutMapping("/counterparties/{id}")
	ResponseEntity<Counterparty> updateCounterparty(@RequestBody Counterparty updatedCounterparty, @PathVariable Long id) {
		final Counterparty counterparty = repository.findById(id).map(c -> {
			c.setName(updatedCounterparty.getName());
			c.setLei(updatedCounterparty.getLei());
			c.setCreditLimit(updatedCounterparty.getCreditLimit());
			c.setAvailableCredit(updatedCounterparty.getAvailableCredit());
			return repository.save(c);
		}).orElseThrow(() -> new ResourceNotFoundException());
		return ResponseEntity.ok(counterparty);
	}

	@RequestMapping(value = "/counterparties/{id}", method = RequestMethod.PATCH, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> partialUpdateGeneric(@RequestBody Map<String, String> creditUpdate,
			@PathVariable("id") Long id) {
		try {
			final Counterparty counterparty = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException());
			final BigDecimal creditAmount = new BigDecimal(creditUpdate.get("amount"));
			final OperationEnum operation = OperationEnum.valueOf(creditUpdate.get("operation"));
			if (operation == OperationEnum.ADD) {
				counterparty.addCredit(creditAmount);
				tradeConfirmation.sendConfirmation(new TradeConfirmationDto(counterparty.getName(), creditAmount));
			} else {
				counterparty.deductCredit(creditAmount);
			}
			return ResponseEntity.ok(repository.save(counterparty));
		} catch (final IllegalArgumentException e) {
			return ResponseEntity.badRequest().body("wrong operation");
		}
	}

	@DeleteMapping("/counterparties/{id}")
	void deleteCounterparty(@PathVariable Long id) {
		repository.deleteById(id);
	}
}
