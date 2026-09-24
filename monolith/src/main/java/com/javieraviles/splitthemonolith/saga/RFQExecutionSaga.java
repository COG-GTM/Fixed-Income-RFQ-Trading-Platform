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
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.exception.InvalidRfqException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

@Component
public class RFQExecutionSaga {

	private static final int SETTLEMENT_SCALE = 2;

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Transactional
	public Rfq executeRfq(final RfqDto rfqDto) {

		final Side side = rfqDto.getSide();
		if (side == null) {
			throw new InvalidRfqException();
		}
		final BigDecimal notionalAmount = toSettlementScale(rfqDto.getNotionalAmount());
		final BigDecimal executionPrice = toSettlementScale(rfqDto.getExecutionPrice());

		final Bond bond = bondRepository.findById(rfqDto.getBondId())
				.orElseThrow(() -> new ResourceNotFoundException());
		final Counterparty counterparty = counterpartyRepository.findById(rfqDto.getCounterpartyId())
				.orElseThrow(() -> new ResourceNotFoundException());

		/*
		 * This is all part of one transaction due to @Transactional annotation,
		 * so no saga compensation is needed: inventory and credit either both
		 * move or neither of them does.
		 */
		if (side == Side.BUY) {
			bond.deductNotional(notionalAmount);
			counterparty.deductCredit(executionPrice);
		} else {
			bond.addNotional(notionalAmount);
			counterparty.releaseCredit(executionPrice);
		}

		return rfqRepository.save(new Rfq(counterparty, bond, notionalAmount,
				side, RfqStatus.EXECUTED, executionPrice));
	}

	private static BigDecimal toSettlementScale(final BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new InvalidRfqException();
		}
		return amount.setScale(SETTLEMENT_SCALE, RoundingMode.HALF_UP);
	}
}
