package com.javieraviles.splitthemonolith.saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.event.TradeExecutedEvent;
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

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Transactional
	public Rfq executeRfq(final RfqDto rfqDto) {

		final Bond bond = bondRepository.findById(rfqDto.getBondId())
				.orElseThrow(() -> new ResourceNotFoundException());
		final Counterparty counterparty = counterpartyRepository.findById(rfqDto.getCounterpartyId())
				.orElseThrow(() -> new ResourceNotFoundException());

		bond.deductNotional(rfqDto.getNotionalAmount());
		/*
		 * This is all part of one transaction due to @Transactional annotation.
		 * No need for saga compensation as credit will only be deducted if the
		 * bond had sufficient available notional.
		 */
		counterparty.deductCredit(rfqDto.getExecutionPrice());

		final Rfq rfq = rfqRepository.save(new Rfq(counterparty, bond, rfqDto.getNotionalAmount(),
				rfqDto.getSide(), RfqStatus.EXECUTED, rfqDto.getExecutionPrice()));

		eventPublisher.publishEvent(
				new TradeExecutedEvent(this, counterparty.getName(), rfqDto.getExecutionPrice()));

		return rfq;
	}
}
