package com.javieraviles.splitthemonolith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.javieraviles.splitthemonolith.dto.RfqPreviewDto;
import com.javieraviles.splitthemonolith.service.RfqEligibilityCalculator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RfqEligibilityCalculatorTest {

    private final RfqEligibilityCalculator calculator = new RfqEligibilityCalculator();

    @Test
    void calculatesFractionalBalancesWithoutFloatingPointRounding() {
        RfqPreviewDto result = calculator.evaluate(new BigDecimal("0.10"), new BigDecimal("0.20"),
                new BigDecimal("0.30"), new BigDecimal("0.30"));
        assertThat(result.isEligible()).isTrue();
        assertThat(result.getRemainingNotional()).isEqualByComparingTo("0.20");
        assertThat(result.getRemainingCredit()).isEqualByComparingTo("0.10");
        assertThat(result.getAvailableNotional()).isEqualByComparingTo("0.30");
        assertThat(result.getAvailableCredit()).isEqualByComparingTo("0.30");
    }

    @Test
    void permitsExactCapacityRegardlessOfDecimalScale() {
        RfqPreviewDto result = calculator.evaluate(new BigDecimal("100.0"), new BigDecimal("50.000"),
                new BigDecimal("100.00"), new BigDecimal("50"));
        assertThat(result.isEligible()).isTrue();
        assertThat(result.getReasons()).isEmpty();
        assertThat(result.getRemainingCredit()).isEqualByComparingTo("0");
        assertThat(result.getRemainingNotional()).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @CsvSource({
        "100.01, 50, INSUFFICIENT_NOTIONAL",
        "100, 50.01, INSUFFICIENT_CREDIT"
    })
    void reportsTheExceededLimit(String notional, String settlement, String reason) {
        RfqPreviewDto result = calculator.evaluate(new BigDecimal(notional), new BigDecimal(settlement),
                new BigDecimal("100"), new BigDecimal("50"));
        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReasons()).containsExactly(reason);
    }

    @Test
    void reportsBothLimitsAndNegativeHeadroom() {
        RfqPreviewDto result = calculator.evaluate(new BigDecimal("100.01"), new BigDecimal("50.01"),
                new BigDecimal("100"), new BigDecimal("50"));
        assertThat(result.getReasons()).containsExactly("INSUFFICIENT_NOTIONAL", "INSUFFICIENT_CREDIT");
        assertThat(result.getRemainingNotional()).isEqualByComparingTo("-0.01");
        assertThat(result.getRemainingCredit()).isEqualByComparingTo("-0.01");
        assertThatThrownBy(() -> result.getReasons().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.01"})
    void rejectsMissingAndNonPositiveAmounts(String value) {
        BigDecimal amount = value == null ? null : new BigDecimal(value);
        assertThatThrownBy(() -> calculator.evaluate(amount, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.evaluate(BigDecimal.ONE, amount, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
