package com.javieraviles.splitthemonolith.event;

import java.math.BigDecimal;
import java.time.Instant;

public final class InventoryChangedEvent {

	private final long bondId;
	private final String isin;
	private final BigDecimal availableNotional;
	private final Instant timestamp;

	public InventoryChangedEvent(final long bondId, final String isin, final BigDecimal availableNotional,
			final Instant timestamp) {
		this.bondId = bondId;
		this.isin = isin;
		this.availableNotional = availableNotional;
		this.timestamp = timestamp;
	}

	public long getBondId() {
		return bondId;
	}

	public String getIsin() {
		return isin;
	}

	public BigDecimal getAvailableNotional() {
		return availableNotional;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	@Override
	public String toString() {
		return "InventoryChangedEvent{" +
				"bondId=" + bondId +
				", isin='" + isin + '\'' +
				", availableNotional=" + availableNotional +
				", timestamp=" + timestamp +
				'}';
	}
}
