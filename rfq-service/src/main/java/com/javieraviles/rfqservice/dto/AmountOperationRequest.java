package com.javieraviles.rfqservice.dto;

import java.math.BigDecimal;

public class AmountOperationRequest {

	private BigDecimal amount;

	private OperationEnum operation;

	public AmountOperationRequest() {
	}

	public AmountOperationRequest(final BigDecimal amount, final OperationEnum operation) {
		this.amount = amount;
		this.operation = operation;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(final BigDecimal amount) {
		this.amount = amount;
	}

	public OperationEnum getOperation() {
		return operation;
	}

	public void setOperation(final OperationEnum operation) {
		this.operation = operation;
	}
}
