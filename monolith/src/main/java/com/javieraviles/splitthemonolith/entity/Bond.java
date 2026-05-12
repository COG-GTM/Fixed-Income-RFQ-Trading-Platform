package com.javieraviles.splitthemonolith.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.validation.constraints.PositiveOrZero;

import com.javieraviles.splitthemonolith.exception.InsufficientNotionalException;

@Entity(name = "bonds")
public class Bond {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(unique = true, length = 12)
	private String isin;

	private String issuer;

	@Column(precision = 7, scale = 4)
	private BigDecimal couponRate;

	private LocalDate maturityDate;

	@PositiveOrZero
	@Column(precision = 19, scale = 2)
	private BigDecimal availableNotional;

	public Bond() {
	}

	public Bond(final String isin, final String issuer, final BigDecimal couponRate,
			final LocalDate maturityDate, final BigDecimal availableNotional) {
		this.isin = isin;
		this.issuer = issuer;
		this.couponRate = couponRate;
		this.maturityDate = maturityDate;
		this.availableNotional = availableNotional;
	}

	public void addNotional(final BigDecimal amount) {
		this.availableNotional = this.availableNotional.add(amount);
	}

	public void deductNotional(final BigDecimal amount) {
		if (amount.compareTo(this.availableNotional) > 0) {
			throw new InsufficientNotionalException();
		}
		this.availableNotional = this.availableNotional.subtract(amount);
	}

	public long getId() {
		return id;
	}

	public String getIsin() {
		return isin;
	}

	public void setIsin(final String isin) {
		this.isin = isin;
	}

	public String getIssuer() {
		return issuer;
	}

	public void setIssuer(final String issuer) {
		this.issuer = issuer;
	}

	public BigDecimal getCouponRate() {
		return couponRate;
	}

	public void setCouponRate(final BigDecimal couponRate) {
		this.couponRate = couponRate;
	}

	public LocalDate getMaturityDate() {
		return maturityDate;
	}

	public void setMaturityDate(final LocalDate maturityDate) {
		this.maturityDate = maturityDate;
	}

	public BigDecimal getAvailableNotional() {
		return availableNotional;
	}

	public void setAvailableNotional(final BigDecimal availableNotional) {
		this.availableNotional = availableNotional;
	}
}
