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

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A fixed-income instrument identified by its ISIN")
@Entity(name = "bonds")
public class Bond {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Schema(description = "Auto-generated identifier", accessMode = Schema.AccessMode.READ_ONLY)
	private long id;

	@Column(unique = true, length = 12)
	@Schema(description = "International Securities Identification Number (unique)", example = "US912828YK15")
	private String isin;

	@Schema(description = "Bond issuer", example = "US Treasury")
	private String issuer;

	@Column(precision = 7, scale = 4)
	@Schema(description = "Coupon rate percentage", example = "2.7500")
	private BigDecimal couponRate;

	@Schema(description = "Maturity date (ISO-8601)", example = "2030-11-15")
	private LocalDate maturityDate;

	@PositiveOrZero
	@Column(precision = 19, scale = 2)
	@Schema(description = "Par amount available for trading", example = "100000000.00")
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
