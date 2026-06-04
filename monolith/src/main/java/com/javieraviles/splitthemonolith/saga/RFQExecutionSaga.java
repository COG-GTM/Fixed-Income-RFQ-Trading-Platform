package com.javieraviles.splitthemonolith.saga;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.exception.InvalidAmountException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

@Component
public class RFQExecutionSaga {

	/** Monetary values settle to whole cents using a deterministic rounding mode. */
	private static final int MONEY_SCALE = 2;
	private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Transactional
	public Rfq executeRfq(final RfqDto rfqDto) {

		// Validate and normalise monetary inputs up front so we fail fast with a
		// clear domain exception before mutating any inventory or credit.
		final BigDecimal notionalAmount = normaliseMoney(rfqDto.getNotionalAmount(), "notionalAmount");
		final BigDecimal executionPrice = normaliseMoney(rfqDto.getExecutionPrice(), "executionPrice");

		final Bond bond = bondRepository.findById(rfqDto.getBondId())
				.orElseThrow(() -> new ResourceNotFoundException("Bond not found: " + rfqDto.getBondId()));
		final Counterparty counterparty = counterpartyRepository.findById(rfqDto.getCounterpartyId())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Counterparty not found: " + rfqDto.getCounterpartyId()));

		bond.deductNotional(notionalAmount);
		/*
		 * Both deductions run inside the single @Transactional boundary. If the
		 * credit deduction throws (insufficient credit), the bond notional
		 * deduction above is rolled back too, so the two updates always commit
		 * or roll back together.
		 */
		counterparty.deductCredit(executionPrice);

		return rfqRepository.save(new Rfq(counterparty, bond, notionalAmount,
				rfqDto.getSide(), RfqStatus.EXECUTED, executionPrice));
	}

	private static BigDecimal normaliseMoney(final BigDecimal amount, final String field) {
		if (amount == null) {
			throw new InvalidAmountException(field + " must be provided");
		}
		final BigDecimal normalised = amount.setScale(MONEY_SCALE, MONEY_ROUNDING);
		if (normalised.signum() <= 0) {
			throw new InvalidAmountException(field + " must be positive");
		}
		return normalised;
	}
}
