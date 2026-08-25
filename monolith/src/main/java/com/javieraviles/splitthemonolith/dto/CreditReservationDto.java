package com.javieraviles.splitthemonolith.dto;

import java.math.BigDecimal;

import javax.validation.constraints.Positive;

public class CreditReservationDto {

	private long id;

	private long counterpartyId;

	@Positive
	private BigDecimal amount;

	private String status;

	public CreditReservationDto() {
	}

	public CreditReservationDto(final BigDecimal amount) {
		this.amount = amount;
	}

	public long getId() {
		return id;
	}

	public void setId(final long id) {
		this.id = id;
	}

	public long getCounterpartyId() {
		return counterpartyId;
	}

	public void setCounterpartyId(final long counterpartyId) {
		this.counterpartyId = counterpartyId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(final BigDecimal amount) {
		this.amount = amount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(final String status) {
		this.status = status;
	}
}
