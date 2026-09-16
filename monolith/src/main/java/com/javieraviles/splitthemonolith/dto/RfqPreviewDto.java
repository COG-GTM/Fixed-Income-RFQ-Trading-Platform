package com.javieraviles.splitthemonolith.dto;

import java.math.BigDecimal;
import java.util.List;

public class RfqPreviewDto {

    private final BigDecimal availableCredit;
    private final BigDecimal availableNotional;
    private final BigDecimal remainingCredit;
    private final BigDecimal remainingNotional;
    private final List<String> reasons;

    public RfqPreviewDto(BigDecimal availableCredit, BigDecimal availableNotional,
            BigDecimal remainingCredit, BigDecimal remainingNotional, List<String> reasons) {
        this.availableCredit = availableCredit;
        this.availableNotional = availableNotional;
        this.remainingCredit = remainingCredit;
        this.remainingNotional = remainingNotional;
        this.reasons = List.copyOf(reasons);
    }

    public boolean isEligible() {
        return reasons.isEmpty();
    }

    public BigDecimal getAvailableCredit() {
        return availableCredit;
    }

    public BigDecimal getAvailableNotional() {
        return availableNotional;
    }

    public BigDecimal getRemainingCredit() {
        return remainingCredit;
    }

    public BigDecimal getRemainingNotional() {
        return remainingNotional;
    }

    public List<String> getReasons() {
        return reasons;
    }
}
