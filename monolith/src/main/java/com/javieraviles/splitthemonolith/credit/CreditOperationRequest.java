package com.javieraviles.splitthemonolith.credit;

import java.math.BigDecimal;

/**
 * Wire contract for credit reserve/release calls to the extracted
 * credit-service.
 */
public class CreditOperationRequest {

	private long counterpartyId;
	private BigDecimal amount;

	public CreditOperationRequest() {
	}

	public CreditOperationRequest(final long counterpartyId, final BigDecimal amount) {
		this.counterpartyId = counterpartyId;
		this.amount = amount;
	}

	public long getCounterpartyId() {
		return counterpartyId;
	}

	public void setCounterpartyId(final long counterpartyId) {
		this.counterpartyId = counterpartyId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(final BigDecimal amount) {
		this.amount = amount;
	}
}
