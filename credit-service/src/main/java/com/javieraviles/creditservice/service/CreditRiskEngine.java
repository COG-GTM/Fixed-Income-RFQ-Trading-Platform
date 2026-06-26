package com.javieraviles.creditservice.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.creditservice.entity.Counterparty;
import com.javieraviles.creditservice.exception.ResourceNotFoundException;
import com.javieraviles.creditservice.repository.CounterpartyRepository;

/**
 * Owns the credit invariant for the extracted capability: a counterparty's
 * available credit may never go negative. Reservation and release each run in
 * their own local transaction so the service is the single source of truth for
 * credit, independent of the monolith's transaction.
 */
@Service
public class CreditRiskEngine {

	private final CounterpartyRepository counterpartyRepository;

	public CreditRiskEngine(final CounterpartyRepository counterpartyRepository) {
		this.counterpartyRepository = counterpartyRepository;
	}

	@Transactional
	public Counterparty reserve(final long counterpartyId, final BigDecimal amount) {
		final Counterparty counterparty = counterpartyRepository.findById(counterpartyId)
				.orElseThrow(ResourceNotFoundException::new);
		// Enforces the credit invariant; throws InsufficientCreditException when
		// the reservation would overdraw available credit.
		counterparty.deductCredit(amount);
		return counterpartyRepository.save(counterparty);
	}

	@Transactional
	public Counterparty release(final long counterpartyId, final BigDecimal amount) {
		final Counterparty counterparty = counterpartyRepository.findById(counterpartyId)
				.orElseThrow(ResourceNotFoundException::new);
		counterparty.addCredit(amount);
		return counterpartyRepository.save(counterparty);
	}
}
