package com.javieraviles.splitthemonolith.saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.counterpartycredit.CounterpartyCreditService;
import com.javieraviles.counterpartycredit.domain.Counterparty;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

@Component
public class RFQExecutionSaga {

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private CounterpartyCreditService counterpartyCreditService;

	@Autowired
	private BondRepository bondRepository;

	@Transactional
	public Rfq executeRfq(final RfqDto rfqDto) {

		final Bond bond = bondRepository.findById(rfqDto.getBondId())
				.orElseThrow(() -> new ResourceNotFoundException());
		// Counterparty existence and credit are owned by the Counterparty Credit
		// bounded context and reached only through its service seam.
		final Counterparty counterparty = counterpartyCreditService.findById(rfqDto.getCounterpartyId());

		bond.deductNotional(rfqDto.getNotionalAmount());
		/*
		 * The credit deduction is delegated to the counterparty credit service.
		 * The in-process adapter joins this @Transactional method (propagation
		 * REQUIRED), so the bond notional update and the credit deduction remain
		 * atomic: if either fails the whole RFQ execution rolls back, exactly as
		 * before the extraction. See docs/counterparty-credit-extraction.md for
		 * the eventual-consistency tradeoffs once this context is split onto its
		 * own database.
		 */
		counterpartyCreditService.deductCredit(counterparty.getId(), rfqDto.getExecutionPrice());

		return rfqRepository.save(new Rfq(counterparty, bond, rfqDto.getNotionalAmount(),
				rfqDto.getSide(), RfqStatus.EXECUTED, rfqDto.getExecutionPrice()));
	}
}
