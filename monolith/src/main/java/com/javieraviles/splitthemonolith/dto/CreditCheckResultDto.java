package com.javieraviles.splitthemonolith.dto;

import java.math.BigDecimal;

public class CreditCheckResultDto {

	private String lei;

	private boolean approved;

	private BigDecimal reservedAmount;

	private BigDecimal availableCredit;

	public CreditCheckResultDto() {
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
