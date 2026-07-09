package com.javieraviles.splitthemonolith.event;

import java.math.BigDecimal;
import java.time.Instant;

public final class CreditChangedEvent {

	private final long counterpartyId;
	private final String name;
	private final BigDecimal availableCredit;
	private final Instant timestamp;

	public CreditChangedEvent(final long counterpartyId, final String name, final BigDecimal availableCredit,
			final Instant timestamp) {
		this.counterpartyId = counterpartyId;
		this.name = name;
		this.availableCredit = availableCredit;
		this.timestamp = timestamp;
	}

	public long getCounterpartyId() {
		return counterpartyId;
	}

	public String getName() {
		return name;
	}

	public BigDecimal getAvailableCredit() {
		return availableCredit;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	@Override
	public String toString() {
		return "CreditChangedEvent{" +
				"counterpartyId=" + counterpartyId +
				", name='" + name + '\'' +
				", availableCredit=" + availableCredit +
				", timestamp=" + timestamp +
				'}';
	}
}
