package com.javieraviles.counterpartycredit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.counterpartycredit.domain.Counterparty;
import com.javieraviles.counterpartycredit.exception.CounterpartyNotFoundException;
import com.javieraviles.counterpartycredit.repository.CounterpartyRepository;

/**
 * In-process adapter for {@link CounterpartyCreditService}.
 *
 * <p>Strangler-fig step 1: the monolith calls this bean through the
 * {@link CounterpartyCreditService} interface. Because it currently shares the
 * monolith's datasource and transaction, credit mutations made here join the
 * caller's transaction (propagation REQUIRED) and stay atomic with the RFQ
 * execution saga. When this context is split onto its own database/process,
 * only this class is replaced by a network-backed adapter; callers are
 * unaffected.
 */
@Service
public class InProcessCounterpartyCreditService implements CounterpartyCreditService {

	private final CounterpartyRepository repository;

	public InProcessCounterpartyCreditService(final CounterpartyRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Counterparty> findAll() {
		return repository.findAll();
	}

	@Override
	@Transactional(readOnly = true)
	public Counterparty findById(final long id) {
		return repository.findById(id)
				.orElseThrow(() -> new CounterpartyNotFoundException(id));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Counterparty> findByLei(final String lei) {
		return repository.findByLei(lei);
	}

	@Override
	@Transactional
	public Counterparty create(final Counterparty counterparty) {
		return repository.save(counterparty);
	}

	@Override
	@Transactional
	public Counterparty update(final long id, final Counterparty updated) {
		final Counterparty counterparty = findById(id);
		counterparty.setName(updated.getName());
		counterparty.setLei(updated.getLei());
		counterparty.setCreditLimit(updated.getCreditLimit());
		counterparty.setAvailableCredit(updated.getAvailableCredit());
		return repository.save(counterparty);
	}

	@Override
	@Transactional
	public void delete(final long id) {
		repository.deleteById(id);
	}

	@Override
	@Transactional
	public Counterparty addCredit(final long id, final BigDecimal amount) {
		final Counterparty counterparty = findById(id);
		counterparty.addCredit(amount);
		return repository.save(counterparty);
	}

	@Override
	@Transactional
	public Counterparty deductCredit(final long id, final BigDecimal amount) {
		final Counterparty counterparty = findById(id);
		counterparty.deductCredit(amount);
		return repository.save(counterparty);
	}
}
