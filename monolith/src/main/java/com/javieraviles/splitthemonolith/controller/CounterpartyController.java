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

import com.javieraviles.splitthemonolith.dto.CounterpartyDto;
import com.javieraviles.splitthemonolith.dto.OperationEnum;
import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;
import com.javieraviles.splitthemonolith.restclient.CreditServiceClient;
import com.javieraviles.splitthemonolith.restclient.TradeConfirmationMicroserviceClient;
import com.javieraviles.splitthemonolith.service.TradeConfirmationService;

/**
 * Kept as the public entry point for counterparties so existing callers do not
 * break: every request is proxied to the credit service, which owns the
 * aggregate now.
 */
@RestController
class CounterpartyController {

	@Value(value = "${use.confirmation.service}")
	private boolean useConfirmationService;

	@Autowired
	private CreditServiceClient creditServiceClient;

	@Autowired
	private TradeConfirmationService tradeConfirmationService;

	@Autowired
	private TradeConfirmationMicroserviceClient confirmationMsClient;

	@GetMapping("/counterparties")
	List<CounterpartyDto> getAll() {
		return creditServiceClient.getCounterparties();
	}

	@PostMapping("/counterparties")
	ResponseEntity<CounterpartyDto> createCounterparty(@RequestBody CounterpartyDto newCounterparty) {
		return ResponseEntity.status(HttpStatus.CREATED).body(creditServiceClient.createCounterparty(newCounterparty));
	}

	@GetMapping("/counterparties/{id}")
	ResponseEntity<CounterpartyDto> getOne(@PathVariable Long id) {
		return ResponseEntity.ok(creditServiceClient.getCounterparty(id));
	}

	@PutMapping("/counterparties/{id}")
	ResponseEntity<CounterpartyDto> updateCounterparty(@RequestBody CounterpartyDto updatedCounterparty,
			@PathVariable Long id) {
		return ResponseEntity.ok(creditServiceClient.updateCounterparty(id, updatedCounterparty));
	}

	@RequestMapping(value = "/counterparties/{id}", method = RequestMethod.PATCH, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> partialUpdateGeneric(@RequestBody Map<String, String> creditUpdate,
			@PathVariable("id") Long id) {
		final BigDecimal creditAmount;
		final OperationEnum operation;
		try {
			creditAmount = new BigDecimal(creditUpdate.get("amount"));
			operation = OperationEnum.valueOf(creditUpdate.get("operation"));
		} catch (final IllegalArgumentException e) {
			return ResponseEntity.badRequest().body("wrong operation");
		}
		final CounterpartyDto counterparty = creditServiceClient.updateCredit(id, creditAmount, operation);
		if (operation == OperationEnum.ADD) {
			final TradeConfirmationDto confirmation = new TradeConfirmationDto(counterparty.getName(), creditAmount);
			if (useConfirmationService) {
				confirmationMsClient.sendConfirmation(confirmation);
			} else {
				tradeConfirmationService.sendTradeConfirmation(confirmation);
			}
		}
		return ResponseEntity.ok(counterparty);
	}

	@DeleteMapping("/counterparties/{id}")
	void deleteCounterparty(@PathVariable Long id) {
		creditServiceClient.deleteCounterparty(id);
	}
}
