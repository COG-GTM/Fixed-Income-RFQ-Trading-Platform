package com.javieraviles.rfqservice.entity;

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

@Entity(name = "rfqs")
public class Rfq {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@NotNull
	private long counterpartyId;

	@NotNull
	private long bondId;

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

	public Rfq(final long counterpartyId, final long bondId, final BigDecimal notionalAmount,
			final Side side, final RfqStatus status, final BigDecimal executionPrice) {
		this.counterpartyId = counterpartyId;
		this.bondId = bondId;
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

	public long getCounterpartyId() {
		return counterpartyId;
	}

	public void setCounterpartyId(final long counterpartyId) {
		this.counterpartyId = counterpartyId;
	}

	public long getBondId() {
		return bondId;
	}

	public void setBondId(final long bondId) {
		this.bondId = bondId;
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
