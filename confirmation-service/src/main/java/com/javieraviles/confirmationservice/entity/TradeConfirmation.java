package com.javieraviles.confirmationservice.entity;

import java.math.BigDecimal;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;

@Entity(name = "trade_confirmations")
public class TradeConfirmation {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String counterpartyName;

	@Column(precision = 19, scale = 2)
	private BigDecimal creditAmount;

	@Column(updatable = false)
	private Instant confirmedAt;

	public TradeConfirmation() {
	}

	public TradeConfirmation(final String counterpartyName, final BigDecimal creditAmount) {
		this.counterpartyName = counterpartyName;
		this.creditAmount = creditAmount;
	}

	@PrePersist
	protected void onCreate() {
		this.confirmedAt = Instant.now();
	}

	public long getId() {
		return id;
	}

	public String getCounterpartyName() {
		return counterpartyName;
	}

	public void setCounterpartyName(final String counterpartyName) {
		this.counterpartyName = counterpartyName;
	}

	public BigDecimal getCreditAmount() {
		return creditAmount;
	}

	public void setCreditAmount(final BigDecimal creditAmount) {
		this.creditAmount = creditAmount;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}
}
