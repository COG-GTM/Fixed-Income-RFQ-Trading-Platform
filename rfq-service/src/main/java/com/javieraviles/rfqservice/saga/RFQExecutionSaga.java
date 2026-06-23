package com.javieraviles.rfqservice.saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.javieraviles.rfqservice.dto.RfqDto;
import com.javieraviles.rfqservice.entity.Rfq;
import com.javieraviles.rfqservice.entity.RfqStatus;
import com.javieraviles.rfqservice.repository.RfqRepository;
import com.javieraviles.rfqservice.restclient.BondServiceClient;
import com.javieraviles.rfqservice.restclient.CounterpartyServiceClient;

/**
 * Distributed saga orchestrator for RFQ execution.
 *
 * <p>
 * Bond inventory and counterparty credit now live in separate services with their
 * own databases, so a single ACID transaction is no longer possible. Instead the
 * orchestrator coordinates the steps via REST and relies on a compensating
 * transaction to undo the bond notional deduction if the credit deduction fails:
 * </p>
 *
 * <ol>
 * <li>Deduct notional on the Bond Service (port 8071).</li>
 * <li>Deduct credit on the Counterparty Service (port 8072).</li>
 * <li>If step 2 fails, compensate by adding the notional back on the Bond
 * Service, then propagate the failure.</li>
 * <li>If both succeed, persist the Rfq locally with status EXECUTED.</li>
 * </ol>
 */
@Component
public class RFQExecutionSaga {

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private BondServiceClient bondServiceClient;

	@Autowired
	private CounterpartyServiceClient counterpartyServiceClient;

	public Rfq executeRfq(final RfqDto rfqDto) {

		// Step 1: deduct notional on the Bond Service.
		bondServiceClient.deductNotional(rfqDto.getBondId(), rfqDto.getNotionalAmount());

		// Step 2: deduct credit on the Counterparty Service.
		try {
			counterpartyServiceClient.deductCredit(rfqDto.getCounterpartyId(), rfqDto.getExecutionPrice());
		} catch (final RuntimeException e) {
			// Step 3: compensating transaction — restore the notional we deducted in step 1.
			bondServiceClient.addNotional(rfqDto.getBondId(), rfqDto.getNotionalAmount());
			throw e;
		}

		// Step 4: both remote steps succeeded — persist the executed RFQ locally.
		return rfqRepository.save(new Rfq(rfqDto.getCounterpartyId(), rfqDto.getBondId(),
				rfqDto.getNotionalAmount(), rfqDto.getSide(), RfqStatus.EXECUTED, rfqDto.getExecutionPrice()));
	}
}
