package com.javieraviles.splitthemonolith.dto;

import java.math.BigDecimal;

public class CounterpartyDto {

        private long id;
        private String name;
        private String lei;
        private BigDecimal creditLimit;
        private BigDecimal availableCredit;

        public CounterpartyDto() {
        }

        public long getId() {
                return id;
        }

        public void setId(final long id) {
                this.id = id;
        }

        public String getName() {
                return name;
        }

        public void setName(final String name) {
                this.name = name;
        }

        public String getLei() {
                return lei;
        }

        public void setLei(final String lei) {
                this.lei = lei;
        }

        public BigDecimal getCreditLimit() {
                return creditLimit;
        }

        public void setCreditLimit(final BigDecimal creditLimit) {
                this.creditLimit = creditLimit;
        }

        public BigDecimal getAvailableCredit() {
                return availableCredit;
        }

        public void setAvailableCredit(final BigDecimal availableCredit) {
                this.availableCredit = availableCredit;
        }
}
