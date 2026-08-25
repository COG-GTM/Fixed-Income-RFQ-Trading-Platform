package com.javieraviles.creditservice.dto;

import java.math.BigDecimal;

import javax.validation.constraints.Positive;

import com.javieraviles.creditservice.entity.ReservationStatus;

public class CreditReservationDto {

	private long id;

	private long counterpartyId;

	@Positive
	private BigDecimal amount;

	private ReservationStatus status;

	public CreditReservationDto() {
	}

	public CreditReservationDto(final long id, final long counterpartyId, final BigDecimal amount,
			final ReservationStatus status) {
		this.id = id;
		this.counterpartyId = counterpartyId;
		this.amount = amount;
		this.status = status;
	}

	public long getId() {
		return id;
	}

	public long getCounterpartyId() {
		return counterpartyId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(final BigDecimal amount) {
		this.amount = amount;
	}

	public ReservationStatus getStatus() {
		return status;
	}
}
