package com.javieraviles.splitthemonolith.saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;
import com.javieraviles.splitthemonolith.restclient.CounterpartyServiceProxy;

@Component
public class RFQExecutionSaga {

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private CounterpartyServiceProxy counterpartyServiceProxy;

	@Autowired
	private BondRepository bondRepository;

	@Transactional
	public Rfq executeRfq(final RfqDto rfqDto) {

		final Bond bond = bondRepository.findById(rfqDto.getBondId())
				.orElseThrow(() -> new ResourceNotFoundException());

		counterpartyServiceProxy.validateCounterparty(rfqDto.getCounterpartyId());

		bond.deductNotional(rfqDto.getNotionalAmount());

		counterpartyServiceProxy.deductCredit(rfqDto.getCounterpartyId(), rfqDto.getExecutionPrice());

		return rfqRepository.save(new Rfq(rfqDto.getCounterpartyId(), bond, rfqDto.getNotionalAmount(),
				rfqDto.getSide(), RfqStatus.EXECUTED, rfqDto.getExecutionPrice()));
	}
}
