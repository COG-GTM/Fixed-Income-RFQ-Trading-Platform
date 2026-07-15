package com.javieraviles.splitthemonolith.dto;

import java.math.BigDecimal;

import javax.validation.constraints.Positive;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trade confirmation sent to a counterparty whenever credit is added")
public class TradeConfirmationDto {

	@Schema(description = "Name of the counterparty being confirmed", example = "Acme Asset Management")
	private String counterpartyName;

	@Positive
	@Schema(description = "Credit amount added (must be positive)", example = "1000000.00")
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
