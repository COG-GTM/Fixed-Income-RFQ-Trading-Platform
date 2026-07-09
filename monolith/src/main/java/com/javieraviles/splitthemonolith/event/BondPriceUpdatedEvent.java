package com.javieraviles.splitthemonolith.event;

import java.math.BigDecimal;
import java.time.Instant;

public final class BondPriceUpdatedEvent {

	private final long bondId;
	private final String isin;
	private final BigDecimal price;
	private final BigDecimal bid;
	private final BigDecimal ask;
	private final Instant timestamp;

	public BondPriceUpdatedEvent(final long bondId, final String isin, final BigDecimal price, final BigDecimal bid,
			final BigDecimal ask, final Instant timestamp) {
		this.bondId = bondId;
		this.isin = isin;
		this.price = price;
		this.bid = bid;
		this.ask = ask;
		this.timestamp = timestamp;
	}

	public long getBondId() {
		return bondId;
	}

	public String getIsin() {
		return isin;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public BigDecimal getBid() {
		return bid;
	}

	public BigDecimal getAsk() {
		return ask;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	@Override
	public String toString() {
		return "BondPriceUpdatedEvent{" +
				"bondId=" + bondId +
				", isin='" + isin + '\'' +
				", price=" + price +
				", bid=" + bid +
				", ask=" + ask +
				", timestamp=" + timestamp +
				'}';
	}
}
