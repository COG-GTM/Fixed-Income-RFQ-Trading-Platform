package com.javieraviles.creditservice.entity;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

import com.javieraviles.creditservice.exception.InsufficientCreditException;

@Entity(name = "credit_accounts")
public class CreditAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@NotNull
	@Column(unique = true, length = 24)
	private String lei;

	private String counterpartyName;

	@PositiveOrZero
	@Column(precision = 19, scale = 2)
	private BigDecimal creditLimit;

	@PositiveOrZero
	@Column(precision = 19, scale = 2)
	private BigDecimal availableCredit;

	public CreditAccount() {
	}

	public CreditAccount(final String lei, final String counterpartyName, final BigDecimal creditLimit) {
		this.lei = lei;
		this.counterpartyName = counterpartyName;
		this.creditLimit = creditLimit;
		this.availableCredit = creditLimit;
	}

	public void reserve(final BigDecimal amount) {
		if (amount.compareTo(this.availableCredit) > 0) {
			throw new InsufficientCreditException();
		}
		this.availableCredit = this.availableCredit.subtract(amount);
	}

	public void release(final BigDecimal amount) {
		this.availableCredit = this.availableCredit.add(amount);
	}

	public long getId() {
		return id;
	}

	public String getLei() {
		return lei;
	}

	public void setLei(final String lei) {
		this.lei = lei;
	}

	public String getCounterpartyName() {
		return counterpartyName;
	}

	public void setCounterpartyName(final String counterpartyName) {
		this.counterpartyName = counterpartyName;
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
