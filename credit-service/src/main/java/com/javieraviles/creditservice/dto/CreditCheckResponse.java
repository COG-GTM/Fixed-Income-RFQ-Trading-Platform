package com.javieraviles.creditservice.dto;

import java.math.BigDecimal;

public class CreditCheckResponse {

	private String lei;

	private boolean approved;

	private BigDecimal reservedAmount;

	private BigDecimal availableCredit;

	public CreditCheckResponse() {
	}

	public CreditCheckResponse(final String lei, final boolean approved, final BigDecimal reservedAmount,
			final BigDecimal availableCredit) {
		this.lei = lei;
		this.approved = approved;
		this.reservedAmount = reservedAmount;
		this.availableCredit = availableCredit;
	}

	public String getLei() {
		return lei;
	}

	public void setLei(final String lei) {
		this.lei = lei;
	}

	public boolean isApproved() {
		return approved;
	}

	public void setApproved(final boolean approved) {
		this.approved = approved;
	}

	public BigDecimal getReservedAmount() {
		return reservedAmount;
	}

	public void setReservedAmount(final BigDecimal reservedAmount) {
		this.reservedAmount = reservedAmount;
	}

	public BigDecimal getAvailableCredit() {
		return availableCredit;
	}

	public void setAvailableCredit(final BigDecimal availableCredit) {
		this.availableCredit = availableCredit;
	}
}
