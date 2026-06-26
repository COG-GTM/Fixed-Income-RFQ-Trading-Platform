package com.javieraviles.splitthemonolith.credit;

import java.math.BigDecimal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;

/**
 * In-process credit adapter (default). Operates on the monolith's own
 * {@code Counterparty} rows and joins the caller's transaction, so behavior is
 * identical to the pre-extraction monolith. Active unless
 * {@code rfq.credit.service.remote=true}.
 */
@Component
@ConditionalOnProperty(name = "rfq.credit.service.remote", havingValue = "false", matchIfMissing = true)
public class LocalCreditService implements CreditService {

	private final CounterpartyRepository counterpartyRepository;

	public LocalCreditService(final CounterpartyRepository counterpartyRepository) {
		this.counterpartyRepository = counterpartyRepository;
	}

	@Override
	public void reserveCredit(final long counterpartyId, final BigDecimal amount) {
		final Counterparty counterparty = counterpartyRepository.findById(counterpartyId)
				.orElseThrow(ResourceNotFoundException::new);
		counterparty.deductCredit(amount);
		counterpartyRepository.save(counterparty);
	}

	@Override
	public void releaseCredit(final long counterpartyId, final BigDecimal amount) {
		final Counterparty counterparty = counterpartyRepository.findById(counterpartyId)
				.orElseThrow(ResourceNotFoundException::new);
		counterparty.addCredit(amount);
		counterpartyRepository.save(counterparty);
	}
}
