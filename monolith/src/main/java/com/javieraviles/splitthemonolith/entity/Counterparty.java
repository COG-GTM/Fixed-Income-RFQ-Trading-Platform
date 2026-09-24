package com.javieraviles.splitthemonolith.entity;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.InvalidAmountException;

@Entity(name = "counterparties")
public class Counterparty {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Size(min = 3, max = 100)
	private String name;

	@Column(length = 24)
	private String lei;

	@PositiveOrZero
	@Column(precision = 19, scale = 2)
	private BigDecimal creditLimit;

	@PositiveOrZero
	@Column(precision = 19, scale = 2)
	private BigDecimal availableCredit;

	public Counterparty() {
	}

	@PrePersist
	private void initCreditBalances() {
		if (this.availableCredit == null && this.creditLimit != null) {
			this.availableCredit = this.creditLimit;
		}
		if (this.creditLimit == null && this.availableCredit != null) {
			this.creditLimit = this.availableCredit;
		}
	}

	public Counterparty(final String name, final String lei, final BigDecimal creditLimit) {
		this.name = name;
		this.lei = lei;
		this.creditLimit = creditLimit;
		this.availableCredit = creditLimit;
	}

	public void addCredit(final BigDecimal amount) {
		requirePositive(amount);
		this.availableCredit = this.availableCredit.add(amount);
	}

	/**
	 * Gives back credit consumed by previous exposure, never taking available
	 * credit above the approved limit.
	 */
	public void releaseCredit(final BigDecimal amount) {
		requirePositive(amount);
		final BigDecimal released = this.availableCredit.add(amount);
		this.availableCredit = this.creditLimit == null ? released : released.min(this.creditLimit);
	}

	public void deductCredit(final BigDecimal amount) {
		requirePositive(amount);
		if (amount.compareTo(this.availableCredit) > 0) {
			throw new InsufficientCreditException();
		}
		this.availableCredit = this.availableCredit.subtract(amount);
	}

	private static void requirePositive(final BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new InvalidAmountException();
		}
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getLei() {
		return lei;
	}

	public void setLei(final String lei) {
		this.lei = lei;
	}

	public BigDecimal getCreditLimit() {
		return creditLimit;
	}

	/**
	 * An omitted limit keeps the currently approved one: credit lines are
	 * changed by supplying a new figure, never by clearing the field. Credit
	 * already consumed by open trades survives the change, so the balance
	 * becomes the new line less that exposure, never below zero.
	 */
	public void setCreditLimit(final BigDecimal creditLimit) {
		if (creditLimit == null) {
			return;
		}
		if (this.availableCredit != null) {
			final BigDecimal consumed = this.creditLimit == null ? BigDecimal.ZERO
					: this.creditLimit.subtract(this.availableCredit);
			this.availableCredit = creditLimit.subtract(consumed).max(BigDecimal.ZERO)
					.min(creditLimit);
		}
		this.creditLimit = creditLimit;
	}

	public BigDecimal getAvailableCredit() {
		return availableCredit;
	}

	/**
	 * An omitted balance keeps the credit already consumed by open trades, and
	 * a supplied one is bounded by the approved line.
	 */
	public void setAvailableCredit(final BigDecimal availableCredit) {
		if (availableCredit == null) {
			return;
		}
		this.availableCredit = this.creditLimit == null ? availableCredit
				: availableCredit.min(this.creditLimit);
	}
}
