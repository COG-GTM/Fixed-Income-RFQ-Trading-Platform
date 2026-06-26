package com.javieraviles.splitthemonolith.credit;

import java.math.BigDecimal;

/**
 * Port (seam) for the counterparty credit-check capability.
 *
 * <p>
 * The RFQ execution saga depends only on this interface, never on where credit
 * actually lives. Two adapters implement it:
 * <ul>
 * <li>{@code LocalCreditService} &mdash; in-process against the monolith's own
 * {@code CounterpartyRepository} (default; preserves original behavior).</li>
 * <li>{@code RemoteCreditService} &mdash; HTTP calls to the extracted
 * credit-service (enabled by {@code rfq.credit.service.remote=true}).</li>
 * </ul>
 *
 * <p>
 * Both adapters enforce the same credit invariant: a counterparty's available
 * credit may never go negative. {@link #reserveCredit} fails (without mutating
 * state) when a reservation would overdraw; {@link #releaseCredit} is the
 * compensating action used by the saga to undo a reservation when a later step
 * of RFQ execution fails.
 */
public interface CreditService {

	/**
	 * Atomically verify and reserve {@code amount} of available credit for the
	 * given counterparty.
	 *
	 * @throws com.javieraviles.splitthemonolith.exception.InsufficientCreditException
	 *             if the reservation would overdraw available credit
	 * @throws com.javieraviles.splitthemonolith.exception.ResourceNotFoundException
	 *             if the counterparty does not exist
	 */
	void reserveCredit(long counterpartyId, BigDecimal amount);

	/**
	 * Compensating action: return a previously reserved {@code amount} of credit
	 * to the counterparty.
	 */
	void releaseCredit(long counterpartyId, BigDecimal amount);
}
