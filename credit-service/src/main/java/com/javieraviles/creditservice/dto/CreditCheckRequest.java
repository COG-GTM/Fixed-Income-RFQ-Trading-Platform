package com.javieraviles.creditservice.dto;

import java.math.BigDecimal;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

public class CreditCheckRequest {

	@NotBlank
	private String lei;

	@NotNull
	@Positive
	private BigDecimal amount;

	public CreditCheckRequest() {
	}

	public CreditCheckRequest(final String lei, final BigDecimal amount) {
		this.lei = lei;
		this.amount = amount;
	}

	public String getLei() {
		return lei;
	}

	public void setLei(final String lei) {
		this.lei = lei;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(final BigDecimal amount) {
		this.amount = amount;
	}
}
