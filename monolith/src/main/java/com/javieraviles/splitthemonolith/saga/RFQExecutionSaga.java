package com.javieraviles.splitthemonolith.saga;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.splitthemonolith.credit.CreditService;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

/**
 * Orchestrates atomic RFQ execution while the credit-check capability is being
 * strangled out into its own service.
 *
 * <p>
 * Invariants preserved:
 * <ul>
 * <li><b>Credit</b>: a counterparty never spends beyond its available credit.
 * Enforced by the {@link CreditService} adapter (in-process or remote).</li>
 * <li><b>Notional</b>: a bond never sells more than its available notional.
 * Enforced locally by {@link Bond#deductNotional}.</li>
 * </ul>
 *
 * <p>
 * Atomicity: credit is reserved through the port first. The bond notional
 * deduction and RFQ persistence then run inside this local transaction. If that
 * local step fails, the saga issues a compensating {@code releaseCredit} so the
 * reservation is undone even when credit lives in a separate service and is no
 * longer covered by the monolith's transaction rollback.
 */
@Component
public class RFQExecutionSaga {

	private final RfqRepository rfqRepository;
	private final CounterpartyRepository counterpartyRepository;
	private final BondRepository bondRepository;
	private final CreditService creditService;

	public RFQExecutionSaga(final RfqRepository rfqRepository,
			final CounterpartyRepository counterpartyRepository,
			final BondRepository bondRepository,
			final CreditService creditService) {
		this.rfqRepository = rfqRepository;
		this.counterpartyRepository = counterpartyRepository;
		this.bondRepository = bondRepository;
		this.creditService = creditService;
	}

	@Transactional
	public Rfq executeRfq(final RfqDto rfqDto) {

		final Bond bond = bondRepository.findById(rfqDto.getBondId())
				.orElseThrow(ResourceNotFoundException::new);
		final Counterparty counterparty = counterpartyRepository.findById(rfqDto.getCounterpartyId())
				.orElseThrow(ResourceNotFoundException::new);

		// Step 1: reserve credit via the seam. Fails fast (no local mutation yet)
		// when the counterparty is unknown or has insufficient available credit.
		creditService.reserveCredit(rfqDto.getCounterpartyId(), rfqDto.getExecutionPrice());

		try {
			// Step 2: deduct bond notional and persist the executed RFQ locally.
			bond.deductNotional(rfqDto.getNotionalAmount());
			return rfqRepository.save(new Rfq(counterparty, bond, rfqDto.getNotionalAmount(),
					rfqDto.getSide(), RfqStatus.EXECUTED, rfqDto.getExecutionPrice()));
		} catch (final RuntimeException e) {
			// Compensation: undo the credit reservation so no money is locked when
			// the local notional/persistence step rejects the trade.
			creditService.releaseCredit(rfqDto.getCounterpartyId(), rfqDto.getExecutionPrice());
			throw e;
		}
	}
}
