package com.javieraviles.splitthemonolith.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.InsufficientNotionalException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

import io.micrometer.core.instrument.MeterRegistry;

@Component
public class RFQExecutionSaga {

	private static final Logger logger = LoggerFactory.getLogger(RFQExecutionSaga.class);

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Autowired
	private MeterRegistry meterRegistry;

	@Transactional
	public Rfq executeRfq(final RfqDto rfqDto) {

		logger.info("RFQ execution started: counterpartyId={}, bondId={}, notional={}, side={}",
				rfqDto.getCounterpartyId(), rfqDto.getBondId(), rfqDto.getNotionalAmount(), rfqDto.getSide());
		meterRegistry.counter("rfq.execution.started").increment();

		final Bond bond = bondRepository.findById(rfqDto.getBondId())
				.orElseThrow(() -> new ResourceNotFoundException());
		final Counterparty counterparty = counterpartyRepository.findById(rfqDto.getCounterpartyId())
				.orElseThrow(() -> new ResourceNotFoundException());

		try {
			bond.deductNotional(rfqDto.getNotionalAmount());
			/*
			 * This is all part of one transaction due to @Transactional annotation.
			 * No need for saga compensation as credit will only be deducted if the
			 * bond had sufficient available notional.
			 */
			counterparty.deductCredit(rfqDto.getExecutionPrice());
		} catch (final InsufficientNotionalException e) {
			logger.warn("RFQ rejected: reason=InsufficientNotional, bondId={}, requestedNotional={}",
					rfqDto.getBondId(), rfqDto.getNotionalAmount());
			meterRegistry.counter("rfq.execution.rejected", "reason", "insufficient_notional").increment();
			throw e;
		} catch (final InsufficientCreditException e) {
			logger.warn("RFQ rejected: reason=InsufficientCredit, counterpartyId={}, executionPrice={}",
					rfqDto.getCounterpartyId(), rfqDto.getExecutionPrice());
			meterRegistry.counter("rfq.execution.rejected", "reason", "insufficient_credit").increment();
			throw e;
		}

		final Rfq executed = rfqRepository.save(new Rfq(counterparty, bond, rfqDto.getNotionalAmount(),
				rfqDto.getSide(), RfqStatus.EXECUTED, rfqDto.getExecutionPrice()));

		logger.info("RFQ executed: rfqId={}, counterpartyId={}, bondId={}, notional={}",
				executed.getId(), rfqDto.getCounterpartyId(), rfqDto.getBondId(), rfqDto.getNotionalAmount());
		meterRegistry.counter("rfq.execution.executed").increment();

		return executed;
	}
}
