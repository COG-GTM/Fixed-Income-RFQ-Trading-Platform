package com.javieraviles.splitthemonolith.event;

import java.math.BigDecimal;
import java.time.Instant;

import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;

public final class RfqStatusChangedEvent {

	private final long rfqId;
	private final long counterpartyId;
	private final long bondId;
	private final RfqStatus oldStatus;
	private final RfqStatus newStatus;
	private final BigDecimal notionalAmount;
	private final Side side;
	private final BigDecimal executionPrice;
	private final Instant timestamp;

	public RfqStatusChangedEvent(final long rfqId, final long counterpartyId, final long bondId,
			final RfqStatus oldStatus, final RfqStatus newStatus, final BigDecimal notionalAmount, final Side side,
			final BigDecimal executionPrice, final Instant timestamp) {
		this.rfqId = rfqId;
		this.counterpartyId = counterpartyId;
		this.bondId = bondId;
		this.oldStatus = oldStatus;
		this.newStatus = newStatus;
		this.notionalAmount = notionalAmount;
		this.side = side;
		this.executionPrice = executionPrice;
		this.timestamp = timestamp;
	}

	public long getRfqId() {
		return rfqId;
	}

	public long getCounterpartyId() {
		return counterpartyId;
	}

	public long getBondId() {
		return bondId;
	}

	public RfqStatus getOldStatus() {
		return oldStatus;
	}

	public RfqStatus getNewStatus() {
		return newStatus;
	}

	public BigDecimal getNotionalAmount() {
		return notionalAmount;
	}

	public Side getSide() {
		return side;
	}

	public BigDecimal getExecutionPrice() {
		return executionPrice;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	@Override
	public String toString() {
		return "RfqStatusChangedEvent{" +
				"rfqId=" + rfqId +
				", counterpartyId=" + counterpartyId +
				", bondId=" + bondId +
				", oldStatus=" + oldStatus +
				", newStatus=" + newStatus +
				", notionalAmount=" + notionalAmount +
				", side=" + side +
				", executionPrice=" + executionPrice +
				", timestamp=" + timestamp +
				'}';
	}
}
