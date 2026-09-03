package sk.knizat.tennisclub.service;

import org.junit.jupiter.api.Test;
import sk.knizat.tennisclub.entity.GameType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PriceCalculatorTest {

    private static final Instant START = Instant.parse("2026-06-01T10:00:00Z");

    private final PriceCalculator calculator = new PriceCalculator();

    private BigDecimal price(Duration length, String pricePerMinute, GameType type) {
        return calculator.calculate(START, START.plus(length), new BigDecimal(pricePerMinute), type);
    }

    @Test
    void should_multiplyMinutesByPrice_when_singles() {
        assertThat(price(Duration.ofMinutes(90), "2.00", GameType.SINGLES)).isEqualTo(new BigDecimal("180.00"));
    }

    @Test
    void should_applyMultiplier_when_doubles() {
        assertThat(PriceCalculator.DOUBLES_MULTIPLIER).isEqualByComparingTo("1.5");
        assertThat(price(Duration.ofMinutes(90), "2.00", GameType.DOUBLES)).isEqualTo(new BigDecimal("270.00"));
    }

    @Test
    void should_roundHalfUp_when_resultHasMoreThanTwoDecimals() {
        assertThat(price(Duration.ofMinutes(1), "0.005", GameType.SINGLES)).isEqualTo(new BigDecimal("0.01"));
        // 3 x 1.555 x 1.5 = 6.9975 -> 7.00
        assertThat(price(Duration.ofMinutes(3), "1.555", GameType.DOUBLES)).isEqualTo(new BigDecimal("7.00"));
        // 1 x 1.004 = 1.004 -> 1.00
        assertThat(price(Duration.ofMinutes(1), "1.004", GameType.SINGLES)).isEqualTo(new BigDecimal("1.00"));
    }

    @Test
    void should_alwaysReturnScaleTwo_when_inputsHaveDifferentScales() {
        assertThat(price(Duration.ofMinutes(10), "3", GameType.SINGLES)).isEqualTo(new BigDecimal("30.00"));
        assertThat(price(Duration.ofMinutes(10), "3.12345", GameType.SINGLES)).isEqualTo(new BigDecimal("31.23"));
    }

    @Test
    void should_countWholeMinutesOnly_when_intervalHasSeconds() {
        BigDecimal result = calculator.calculate(START, START.plus(Duration.ofSeconds(119)),
                new BigDecimal("2.00"), GameType.SINGLES);

        assertThat(result).isEqualTo(new BigDecimal("2.00"));
    }

    @Test
    void should_handleLongDurations_when_manyHours() {
        assertThat(price(Duration.ofHours(240), "2.50", GameType.DOUBLES))
                .isEqualTo(new BigDecimal("54000.00"));
    }
}
