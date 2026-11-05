package com.javieraviles.splitthemonolith.dto;

import java.math.BigDecimal;

import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

public class AmountUpdateDto {

	@NotNull
	@Positive
	@Digits(integer = 17, fraction = 2)
	private BigDecimal amount;

	@NotNull
	private OperationEnum operation;

	public AmountUpdateDto() {
	}

	public AmountUpdateDto(final BigDecimal amount, final OperationEnum operation) {
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
