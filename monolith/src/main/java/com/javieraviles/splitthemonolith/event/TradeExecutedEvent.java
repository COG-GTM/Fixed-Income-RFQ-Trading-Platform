package com.javieraviles.splitthemonolith.event;

import java.math.BigDecimal;

import org.springframework.context.ApplicationEvent;

/**
 * Published after an RFQ is persisted but before the transaction commits.
 * Listeners annotated with {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * will fire only once the trade data is durably committed.
 */
public class TradeExecutedEvent extends ApplicationEvent {

	private final String counterpartyName;
	private final BigDecimal amount;

	public TradeExecutedEvent(Object source, String counterpartyName, BigDecimal amount) {
		super(source);
		this.counterpartyName = counterpartyName;
		this.amount = amount;
	}

	public String getCounterpartyName() {
		return counterpartyName;
	}

	public BigDecimal getAmount() {
		return amount;
	}
}
