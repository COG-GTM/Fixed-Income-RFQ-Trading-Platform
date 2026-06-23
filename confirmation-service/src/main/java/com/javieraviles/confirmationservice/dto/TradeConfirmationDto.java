package com.javieraviles.confirmationservice.dto;

import java.math.BigDecimal;

public class TradeConfirmationDto {

	private String counterpartyName;

	private BigDecimal creditAmount;

	public TradeConfirmationDto() {
	}

	public TradeConfirmationDto(final String counterpartyName, final BigDecimal creditAmount) {
		this.counterpartyName = counterpartyName;
		this.creditAmount = creditAmount;
	}

	public String getCounterpartyName() {
		return counterpartyName;
	}

	public void setCounterpartyName(final String counterpartyName) {
		this.counterpartyName = counterpartyName;
	}

	public BigDecimal getCreditAmount() {
		return creditAmount;
	}

	public void setCreditAmount(final BigDecimal creditAmount) {
		this.creditAmount = creditAmount;
	}

}
