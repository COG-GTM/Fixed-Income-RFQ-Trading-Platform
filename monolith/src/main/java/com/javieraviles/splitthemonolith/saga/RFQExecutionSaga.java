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
import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

@Component
public class RFQExecutionSaga {

	private static final int CURRENCY_SCALE = 2;
	private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Transactional
	public Rfq executeRfq(final RfqDto rfqDto) {

		final BigDecimal settlementAmount = rfqDto.getExecutionPrice()
				.setScale(CURRENCY_SCALE, ROUNDING_MODE);

		final Bond bond = bondRepository.findById(rfqDto.getBondId())
				.orElseThrow(() -> new ResourceNotFoundException());
		final Counterparty counterparty = counterpartyRepository.findById(rfqDto.getCounterpartyId())
				.orElseThrow(() -> new ResourceNotFoundException());

		bond.deductNotional(rfqDto.getNotionalAmount());
		try {
			counterparty.deductCredit(settlementAmount);
		} catch (InsufficientCreditException e) {
			bond.addNotional(rfqDto.getNotionalAmount());
			throw e;
		}

		return rfqRepository.save(new Rfq(counterparty, bond, rfqDto.getNotionalAmount(),
				rfqDto.getSide(), RfqStatus.EXECUTED, settlementAmount));
	}
}
