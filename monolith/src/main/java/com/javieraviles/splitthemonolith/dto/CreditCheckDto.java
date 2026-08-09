package com.javieraviles.splitthemonolith.dto;

import java.math.BigDecimal;

public class CreditCheckDto {

	private String lei;

	private BigDecimal amount;

	public CreditCheckDto() {
	}

	public CreditCheckDto(final String lei, final BigDecimal amount) {
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
