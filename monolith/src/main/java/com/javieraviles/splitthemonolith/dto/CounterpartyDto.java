package com.javieraviles.splitthemonolith.dto;

import java.math.BigDecimal;

import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

/**
 * Local view of the counterparty aggregate, which is owned by the credit
 * service. The monolith never persists it.
 */
public class CounterpartyDto {

	private long id;

	@Size(min = 3, max = 100)
	private String name;

	private String lei;

	@PositiveOrZero
	private BigDecimal creditLimit;

	@PositiveOrZero
	private BigDecimal availableCredit;

	public CounterpartyDto() {
	}

	public CounterpartyDto(final long id, final String name, final String lei, final BigDecimal creditLimit,
			final BigDecimal availableCredit) {
		this.id = id;
		this.name = name;
		this.lei = lei;
		this.creditLimit = creditLimit;
		this.availableCredit = availableCredit;
	}

	public long getId() {
		return id;
	}

	public void setId(final long id) {
		this.id = id;
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

	public void setCreditLimit(final BigDecimal creditLimit) {
		this.creditLimit = creditLimit;
	}

	public BigDecimal getAvailableCredit() {
		return availableCredit;
	}

	public void setAvailableCredit(final BigDecimal availableCredit) {
		this.availableCredit = availableCredit;
	}
}
