package com.javieraviles.splitthemonolith.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.javieraviles.splitthemonolith.dto.RfqPreviewDto;

public class RfqEligibilityCalculator {

    public RfqPreviewDto evaluate(BigDecimal notionalAmount, BigDecimal executionPrice,
            BigDecimal availableNotional, BigDecimal availableCredit) {
        if (notionalAmount == null || notionalAmount.signum() <= 0
                || executionPrice == null || executionPrice.signum() <= 0) {
            throw new IllegalArgumentException("Notional and settlement amounts must be positive");
        }
        final BigDecimal remainingNotional = availableNotional.subtract(notionalAmount);
        final BigDecimal remainingCredit = availableCredit.subtract(executionPrice);
        final List<String> reasons = new ArrayList<>();
        if (remainingNotional.signum() < 0) {
            reasons.add("INSUFFICIENT_NOTIONAL");
        }
        if (remainingCredit.signum() < 0) {
            reasons.add("INSUFFICIENT_CREDIT");
        }
        return new RfqPreviewDto(availableCredit, availableNotional,
                remainingCredit, remainingNotional, reasons);
    }
}
