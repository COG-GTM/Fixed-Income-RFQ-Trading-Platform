package com.javieraviles.splitthemonolith.entity;

import java.math.BigDecimal;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import com.javieraviles.counterpartycredit.domain.Counterparty;

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
