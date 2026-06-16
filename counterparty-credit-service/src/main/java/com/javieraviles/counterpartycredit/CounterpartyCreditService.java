package com.javieraviles.counterpartycredit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.javieraviles.counterpartycredit.domain.Counterparty;

/**
 * Seam for the Counterparty Credit bounded context.
 *
 * <p>This interface is the strangler-fig boundary between the RFQ trading
 * monolith and the counterparty credit domain. Today it is satisfied by an
 * in-process adapter ({@code InProcessCounterpartyCreditService}); later it can
 * be backed by an HTTP/gRPC client without changing any caller in the monolith.
 *
 * <p>The bounded context owns the {@code counterparties} table and is the only
 * component permitted to read or mutate counterparty credit state (credit
 * limits, available credit) and to resolve counterparties by LEI.
 */
public interface CounterpartyCreditService {

	List<Counterparty> findAll();

	/**
	 * @throws com.javieraviles.counterpartycredit.exception.CounterpartyNotFoundException
	 *             if no counterparty exists with the given id
	 */
	Counterparty findById(long id);

	/**
	 * LEI resolution: resolve a counterparty by its 20/24-character Legal Entity
	 * Identifier. Returns empty when no counterparty carries the given LEI.
	 */
	Optional<Counterparty> findByLei(String lei);

	Counterparty create(Counterparty counterparty);

	/**
	 * @throws com.javieraviles.counterpartycredit.exception.CounterpartyNotFoundException
	 *             if no counterparty exists with the given id
	 */
	Counterparty update(long id, Counterparty counterparty);

	void delete(long id);

	/**
	 * Increase available credit (e.g. settlement, top-up).
	 *
	 * @throws com.javieraviles.counterpartycredit.exception.CounterpartyNotFoundException
	 *             if no counterparty exists with the given id
	 */
	Counterparty addCredit(long id, BigDecimal amount);

	/**
	 * Reserve/consume available credit as part of trade execution.
	 *
	 * @throws com.javieraviles.counterpartycredit.exception.CounterpartyNotFoundException
	 *             if no counterparty exists with the given id
	 * @throws com.javieraviles.counterpartycredit.exception.InsufficientCreditException
	 *             if the requested amount exceeds available credit
	 */
	Counterparty deductCredit(long id, BigDecimal amount);
}
