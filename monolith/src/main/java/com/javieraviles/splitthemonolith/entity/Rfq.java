package com.javieraviles.splitthemonolith.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Entity(name = "rfqs")
public class Rfq {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@NotNull
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "counterparty_id")
	private Counterparty counterparty;

	@NotNull
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "bond_id")
	private Bond bond;

	@Positive
	@Column(precision = 19, scale = 2)
	private BigDecimal notionalAmount;

	@NotNull
	@Enumerated(EnumType.STRING)
	private Side side;

	@NotNull
	@Enumerated(EnumType.STRING)
	private RfqStatus status;

	@Positive
	@Column(precision = 19, scale = 2)
	private BigDecimal executionPrice;

	@Column(updatable = false)
	private Instant createdAt;

	public Rfq() {
	}

	public Rfq(final Counterparty counterparty, final Bond bond, final BigDecimal notionalAmount,
			final Side side, final RfqStatus status, final BigDecimal executionPrice) {
		this.counterparty = counterparty;
		this.bond = bond;
		this.notionalAmount = notionalAmount;
		this.side = side;
		this.status = status;
		this.executionPrice = executionPrice;
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = Instant.now();
	}

	public long getId() {
		return id;
	}

	public Counterparty getCounterparty() {
		return counterparty;
	}

	public void setCounterparty(final Counterparty counterparty) {
		this.counterparty = counterparty;
	}

	public Bond getBond() {
		return bond;
	}

	public void setBond(final Bond bond) {
		this.bond = bond;
	}

	public BigDecimal getNotionalAmount() {
		return notionalAmount;
	}

	public void setNotionalAmount(final BigDecimal notionalAmount) {
		this.notionalAmount = notionalAmount;
	}

	public Side getSide() {
		return side;
	}

	public void setSide(final Side side) {
		this.side = side;
	}

	public RfqStatus getStatus() {
		return status;
	}

	public void setStatus(final RfqStatus status) {
		this.status = status;
	}

	public BigDecimal getExecutionPrice() {
		return executionPrice;
	}

	public void setExecutionPrice(final BigDecimal executionPrice) {
		this.executionPrice = executionPrice;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
