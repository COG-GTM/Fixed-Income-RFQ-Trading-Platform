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
import com.javieraviles.splitthemonolith.exception.InvalidNotionalException;
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

	@Value("${rfq.pricing.min-price-ratio:0.50}")
	private BigDecimal minPriceRatio;

	@Value("${rfq.pricing.max-price-ratio:1.50}")
	private BigDecimal maxPriceRatio;

	@Transactional
	public Rfq executeRfq(final RfqDto rfqDto) {
		validatePricing(rfqDto);

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

	/*
	 * The execution price is a total settlement amount, so it must stay within a
	 * configured band around the notional (par) amount. This stops callers from
	 * booking trades at arbitrary prices such as a single cent.
	 */
	private void validatePricing(final RfqDto rfqDto) {
		final BigDecimal notional = rfqDto.getNotionalAmount();
		if (notional == null || notional.signum() <= 0) {
			throw new InvalidNotionalException();
		}
		final BigDecimal price = rfqDto.getExecutionPrice();
		if (price == null || price.signum() <= 0
				|| price.compareTo(notional.multiply(minPriceRatio)) < 0
				|| price.compareTo(notional.multiply(maxPriceRatio)) > 0) {
			throw new InvalidExecutionPriceException();
		}
	}
}
