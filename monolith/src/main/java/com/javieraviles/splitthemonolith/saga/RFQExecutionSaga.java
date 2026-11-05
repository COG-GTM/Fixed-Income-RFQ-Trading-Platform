package com.javieraviles.splitthemonolith.saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

@Component
public class RFQExecutionSaga {

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Transactional
	public Rfq executeRfq(final RfqDto rfqDto) {

		final Bond bond = bondRepository.findByIdForUpdate(rfqDto.getBondId())
				.orElseThrow(() -> new ResourceNotFoundException());
		final Counterparty counterparty = counterpartyRepository.findByIdForUpdate(rfqDto.getCounterpartyId())
				.orElseThrow(() -> new ResourceNotFoundException());

		/*
		 * Bond and counterparty rows are locked (always in this order) until commit,
		 * so concurrent RFQs check and deduct against the latest committed balances.
		 * This is all part of one transaction due to @Transactional annotation.
		 * No need for saga compensation as credit will only be deducted if the
		 * bond had sufficient available notional.
		 */
		bond.deductNotional(rfqDto.getNotionalAmount());
		counterparty.deductCredit(rfqDto.getExecutionPrice());

		return rfqRepository.save(new Rfq(counterparty, bond, rfqDto.getNotionalAmount(),
				rfqDto.getSide(), RfqStatus.EXECUTED, rfqDto.getExecutionPrice()));
	}
}
