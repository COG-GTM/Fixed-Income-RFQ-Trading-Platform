package com.javieraviles.creditservice.dto;

import java.math.BigDecimal;

import com.javieraviles.creditservice.entity.Counterparty;

public class CreditView {

	private long counterpartyId;
	private String name;
	private BigDecimal creditLimit;
	private BigDecimal availableCredit;

	public CreditView() {
	}

	public CreditView(final long counterpartyId, final String name,
			final BigDecimal creditLimit, final BigDecimal availableCredit) {
		this.counterpartyId = counterpartyId;
		this.name = name;
		this.creditLimit = creditLimit;
		this.availableCredit = availableCredit;
	}

	public static CreditView of(final Counterparty counterparty) {
		return new CreditView(counterparty.getId(), counterparty.getName(),
				counterparty.getCreditLimit(), counterparty.getAvailableCredit());
	}

	public long getCounterpartyId() {
		return counterpartyId;
	}

	public void setCounterpartyId(final long counterpartyId) {
		this.counterpartyId = counterpartyId;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
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
