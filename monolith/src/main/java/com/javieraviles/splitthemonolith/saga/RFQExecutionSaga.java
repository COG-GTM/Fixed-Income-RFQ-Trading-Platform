package com.javieraviles.splitthemonolith.saga;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.exception.InvalidExecutionPriceException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

@Component
public class RFQExecutionSaga {

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	@Value("${rfq.execution.min-price-pct:50}")
	private BigDecimal minPricePct;

	@Value("${rfq.execution.max-price-pct:150}")
	private BigDecimal maxPricePct;

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Transactional
	public Rfq executeRfq(final RfqDto rfqDto) {
		validateExecutionPrice(rfqDto.getNotionalAmount(), rfqDto.getExecutionPrice());

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

		return rfqRepository.save(new Rfq(counterparty, bond, rfqDto.getNotionalAmount(),
				rfqDto.getSide(), RfqStatus.EXECUTED, rfqDto.getExecutionPrice()));
	}

	/**
	 * Rejects non-positive amounts and execution prices outside the configured
	 * band, expressed as a percentage of par (notional).
	 */
	private void validateExecutionPrice(final BigDecimal notional, final BigDecimal executionPrice) {
		if (notional == null || executionPrice == null || notional.signum() <= 0 || executionPrice.signum() <= 0) {
			throw new InvalidExecutionPriceException();
		}
		final BigDecimal min = notional.multiply(minPricePct).divide(HUNDRED);
		final BigDecimal max = notional.multiply(maxPricePct).divide(HUNDRED);
		if (executionPrice.compareTo(min) < 0 || executionPrice.compareTo(max) > 0) {
			throw new InvalidExecutionPriceException();
		}
	}
}
