package com.javieraviles.creditservice.entity;

import java.math.BigDecimal;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Entity(name = "credit_reservations")
public class CreditReservation {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@NotNull
	@Column(name = "counterparty_id")
	private long counterpartyId;

	@Positive
	@Column(precision = 19, scale = 2)
	private BigDecimal amount;

	@NotNull
	@Enumerated(EnumType.STRING)
	private ReservationStatus status;

	@Column(updatable = false)
	private Instant createdAt;

	public CreditReservation() {
	}

	public CreditReservation(final long counterpartyId, final BigDecimal amount) {
		this.counterpartyId = counterpartyId;
		this.amount = amount;
		this.status = ReservationStatus.RESERVED;
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = Instant.now();
	}

	public void release() {
		this.status = ReservationStatus.RELEASED;
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

	public ReservationStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
