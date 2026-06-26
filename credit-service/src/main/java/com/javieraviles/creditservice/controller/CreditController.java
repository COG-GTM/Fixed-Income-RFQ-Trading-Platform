package com.javieraviles.creditservice.controller;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javieraviles.creditservice.dto.CreditOperationRequest;
import com.javieraviles.creditservice.dto.CreditView;
import com.javieraviles.creditservice.entity.Counterparty;
import com.javieraviles.creditservice.exception.ResourceNotFoundException;
import com.javieraviles.creditservice.repository.CounterpartyRepository;
import com.javieraviles.creditservice.service.CreditRiskEngine;

/**
 * Credit-check API contract consumed by the RFQ monolith.
 *
 * <ul>
 * <li>POST /credit/reservations &mdash; atomically check &amp; reserve credit
 * (200 ok / 409 insufficient / 404 unknown counterparty).</li>
 * <li>POST /credit/releases &mdash; compensating release of a prior reservation
 * (200 ok / 404 unknown counterparty).</li>
 * <li>GET /credit/{counterpartyId} &mdash; current credit view (200 / 404).</li>
 * </ul>
 */
@RestController
@RequestMapping("/credit")
class CreditController {

	private final CreditRiskEngine creditRiskEngine;
	private final CounterpartyRepository counterpartyRepository;

	CreditController(final CreditRiskEngine creditRiskEngine,
			final CounterpartyRepository counterpartyRepository) {
		this.creditRiskEngine = creditRiskEngine;
		this.counterpartyRepository = counterpartyRepository;
	}

	@PostMapping("/reservations")
	ResponseEntity<CreditView> reserve(@Valid @RequestBody CreditOperationRequest request) {
		final Counterparty counterparty = creditRiskEngine.reserve(request.getCounterpartyId(), request.getAmount());
		return ResponseEntity.ok(CreditView.of(counterparty));
	}

	@PostMapping("/releases")
	ResponseEntity<CreditView> release(@Valid @RequestBody CreditOperationRequest request) {
		final Counterparty counterparty = creditRiskEngine.release(request.getCounterpartyId(), request.getAmount());
		return ResponseEntity.ok(CreditView.of(counterparty));
	}

	@GetMapping("/{counterpartyId}")
	ResponseEntity<CreditView> view(@PathVariable Long counterpartyId) {
		final Counterparty counterparty = counterpartyRepository.findById(counterpartyId)
				.orElseThrow(ResourceNotFoundException::new);
		return ResponseEntity.ok(CreditView.of(counterparty));
	}
}
