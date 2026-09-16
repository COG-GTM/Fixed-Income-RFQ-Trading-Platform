package com.javieraviles.splitthemonolith.service;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.dto.RfqPreviewDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RfqPreviewService {

    private final BondRepository bondRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final RfqEligibilityCalculator calculator = new RfqEligibilityCalculator();

    public RfqPreviewService(BondRepository bondRepository, CounterpartyRepository counterpartyRepository) {
        this.bondRepository = bondRepository;
        this.counterpartyRepository = counterpartyRepository;
    }

    @Transactional(readOnly = true)
    public RfqPreviewDto preview(RfqDto request) {
        final Bond bond = bondRepository.findById(request.getBondId())
                .orElseThrow(ResourceNotFoundException::new);
        final Counterparty counterparty = counterpartyRepository.findById(request.getCounterpartyId())
                .orElseThrow(ResourceNotFoundException::new);
        if (bond.getAvailableNotional() == null || counterparty.getAvailableCredit() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Preview unavailable: credit and notional balances must be configured");
        }
        return calculator.evaluate(request.getNotionalAmount(), request.getExecutionPrice(),
                bond.getAvailableNotional(), counterparty.getAvailableCredit());
    }
}
