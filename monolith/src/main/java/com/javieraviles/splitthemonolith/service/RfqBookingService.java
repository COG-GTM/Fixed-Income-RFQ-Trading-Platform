package com.javieraviles.splitthemonolith.service;

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

/**
 * Local (single database) step of the RFQ execution saga: deducts the bond
 * notional and persists the RFQ atomically. Everything that is still owned by
 * the monolith stays inside one transaction, which rolls back on its own if
 * either operation fails.
 */
@Component
public class RfqBookingService {

	@Autowired
	private BondRepository bondRepository;

	@Autowired
	private RfqRepository rfqRepository;

	@Transactional
	public Rfq bookRfq(final RfqDto rfqDto) {
		final Bond bond = bondRepository.findById(rfqDto.getBondId())
				.orElseThrow(() -> new ResourceNotFoundException());

		bond.deductNotional(rfqDto.getNotionalAmount());
		bondRepository.save(bond);

		return rfqRepository.save(new Rfq(rfqDto.getCounterpartyId(), bond, rfqDto.getNotionalAmount(),
				rfqDto.getSide(), RfqStatus.EXECUTED, rfqDto.getExecutionPrice()));
	}
}
