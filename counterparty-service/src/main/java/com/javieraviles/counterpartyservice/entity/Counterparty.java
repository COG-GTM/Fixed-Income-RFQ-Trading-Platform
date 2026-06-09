package com.javieraviles.counterpartyservice.entity;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

import com.javieraviles.counterpartyservice.exception.InsufficientCreditException;

@Entity(name = "counterparties")
public class Counterparty {

        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private long id;

        @Size(min = 3, max = 100)
        private String name;

        @Column(length = 24)
        private String lei;

        @PositiveOrZero
        @Column(precision = 19, scale = 2)
        private BigDecimal creditLimit;

        @PositiveOrZero
        @Column(precision = 19, scale = 2)
        private BigDecimal availableCredit;

        public Counterparty() {
        }

        @PrePersist
        private void initAvailableCredit() {
                if (this.availableCredit == null && this.creditLimit != null) {
                        this.availableCredit = this.creditLimit;
                }
        }

        public Counterparty(final String name, final String lei, final BigDecimal creditLimit) {
                this.name = name;
                this.lei = lei;
                this.creditLimit = creditLimit;
                this.availableCredit = creditLimit;
        }

        public void addCredit(final BigDecimal amount) {
                this.availableCredit = this.availableCredit.add(amount);
        }

        public void deductCredit(final BigDecimal amount) {
                if (amount.compareTo(this.availableCredit) > 0) {
                        throw new InsufficientCreditException();
                }
                this.availableCredit = this.availableCredit.subtract(amount);
        }

        public long getId() {
                return id;
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
