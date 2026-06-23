package com.javieraviles.counterpartyservice.dto;

import java.math.BigDecimal;

import javax.validation.constraints.Positive;

public class TradeConfirmationDto {

	private String counterpartyName;

	@Positive
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
