package com.javieraviles.creditservice.dto;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

public class CreditOperationRequest {

	@NotNull
	private Long counterpartyId;

	@NotNull
	@Positive
	private BigDecimal amount;

	public CreditOperationRequest() {
	}

	public CreditOperationRequest(final Long counterpartyId, final BigDecimal amount) {
		this.counterpartyId = counterpartyId;
		this.amount = amount;
	}

	public Long getCounterpartyId() {
		return counterpartyId;
	}

	public void setCounterpartyId(final Long counterpartyId) {
		this.counterpartyId = counterpartyId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(final BigDecimal amount) {
		this.amount = amount;
	}
}
