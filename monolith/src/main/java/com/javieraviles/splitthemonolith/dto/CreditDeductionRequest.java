package com.javieraviles.splitthemonolith.dto;

import java.math.BigDecimal;

import javax.validation.constraints.Positive;

public class CreditDeductionRequest {

        @Positive
        private BigDecimal amount;

        public CreditDeductionRequest() {
        }

        public CreditDeductionRequest(final BigDecimal amount) {
                this.amount = amount;
        }

        public BigDecimal getAmount() {
                return amount;
        }

        public void setAmount(final BigDecimal amount) {
                this.amount = amount;
        }
}
