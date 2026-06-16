package com.javieraviles.splitthemonolith.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import com.javieraviles.counterpartycredit.CounterpartyCreditService;
import com.javieraviles.counterpartycredit.domain.Counterparty;
import com.javieraviles.splitthemonolith.dto.OperationEnum;
import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;
import com.javieraviles.splitthemonolith.restclient.TradeConfirmationMicroserviceClient;
import com.javieraviles.splitthemonolith.service.TradeConfirmationService;

@RestController
class CounterpartyController {

	@Value(value = "${use.confirmation.service}")
	private boolean useConfirmationService;

	@Autowired
	private CounterpartyCreditService counterpartyCreditService;

	@Autowired
	private TradeConfirmationService tradeConfirmationService;

	@Autowired
	private TradeConfirmationMicroserviceClient confirmationMsClient;

	@GetMapping("/counterparties")
	List<Counterparty> getAll() {
		return counterpartyCreditService.findAll();
	}

	@PostMapping("/counterparties")
	ResponseEntity<Counterparty> createCounterparty(@RequestBody Counterparty newCounterparty) {
		return ResponseEntity.status(HttpStatus.CREATED).body(counterpartyCreditService.create(newCounterparty));
	}

	@GetMapping("/counterparties/{id}")
	ResponseEntity<Counterparty> getOne(@PathVariable Long id) {
		return ResponseEntity.ok(counterpartyCreditService.findById(id));
	}

	@PutMapping("/counterparties/{id}")
	ResponseEntity<Counterparty> updateCounterparty(@RequestBody Counterparty updatedCounterparty, @PathVariable Long id) {
		return ResponseEntity.ok(counterpartyCreditService.update(id, updatedCounterparty));
	}

	@RequestMapping(value = "/counterparties/{id}", method = RequestMethod.PATCH, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> partialUpdateGeneric(@RequestBody Map<String, String> creditUpdate,
			@PathVariable("id") Long id) {
		try {
			final BigDecimal creditAmount = new BigDecimal(creditUpdate.get("amount"));
			final OperationEnum operation = OperationEnum.valueOf(creditUpdate.get("operation"));
			final Counterparty counterparty;
			if (operation == OperationEnum.ADD) {
				counterparty = counterpartyCreditService.addCredit(id, creditAmount);
				final TradeConfirmationDto confirmation = new TradeConfirmationDto(counterparty.getName(), creditAmount);
				if (useConfirmationService) {
					confirmationMsClient.sendConfirmation(confirmation);
				} else {
					tradeConfirmationService.sendTradeConfirmation(confirmation);
				}
			} else {
				counterparty = counterpartyCreditService.deductCredit(id, creditAmount);
			}
			return ResponseEntity.ok(counterparty);
		} catch (final IllegalArgumentException e) {
			return ResponseEntity.badRequest().body("wrong operation");
		}
	}

	@DeleteMapping("/counterparties/{id}")
	void deleteCounterparty(@PathVariable Long id) {
		counterpartyCreditService.delete(id);
	}
}
