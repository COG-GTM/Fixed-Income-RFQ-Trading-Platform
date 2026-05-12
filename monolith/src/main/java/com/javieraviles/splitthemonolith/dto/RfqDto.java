package com.javieraviles.splitthemonolith.dto;

import java.math.BigDecimal;
import java.time.Instant;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;

public class RfqDto {

	private long id;

	@NotNull
	private long counterpartyId;

	@NotNull
	private long bondId;

	@Positive
	private BigDecimal notionalAmount;

	@NotNull
	private Side side;

	private RfqStatus status;

	@Positive
	private BigDecimal executionPrice;

	private Instant createdAt;

	public RfqDto() {
	}

	public RfqDto(long id, long counterpartyId, long bondId, BigDecimal notionalAmount,
			Side side, RfqStatus status, BigDecimal executionPrice, Instant createdAt) {
		this.id = id;
		this.counterpartyId = counterpartyId;
		this.bondId = bondId;
		this.notionalAmount = notionalAmount;
		this.side = side;
		this.status = status;
		this.executionPrice = executionPrice;
		this.createdAt = createdAt;
	}

	public long getId() {
		return id;
	}

	public long getCounterpartyId() {
		return counterpartyId;
	}

	public void setCounterpartyId(long counterpartyId) {
		this.counterpartyId = counterpartyId;
	}

	public long getBondId() {
		return bondId;
	}

	public void setBondId(long bondId) {
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

	public void setCreatedAt(final Instant createdAt) {
		this.createdAt = createdAt;
	}
}
