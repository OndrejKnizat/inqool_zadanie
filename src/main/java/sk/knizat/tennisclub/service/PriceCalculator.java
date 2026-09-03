package sk.knizat.tennisclub.service;

import org.springframework.stereotype.Component;
import sk.knizat.tennisclub.entity.GameType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Stateless reservation price calculator: {@code whole minutes x pricePerMinute x (1.5 for doubles)},
 * rounded {@code HALF_UP} to scale 2. Interval validity (positive length, whole minutes) is the caller's job.
 */
@Component
public class PriceCalculator {

    /** Multiplier applied to the price of a doubles game. */
    public static final BigDecimal DOUBLES_MULTIPLIER = new BigDecimal("1.5");

    /**
     * Computes the price of the interval {@code [start, end)}.
     *
     * @param start          start of the reservation
     * @param end            end of the reservation
     * @param pricePerMinute price per minute of the court surface
     * @param gameType       singles or doubles
     * @return price with scale 2
     */
    public BigDecimal calculate(Instant start, Instant end, BigDecimal pricePerMinute, GameType gameType) {
        long minutes = Duration.between(start, end).toMinutes();
        BigDecimal price = pricePerMinute.multiply(BigDecimal.valueOf(minutes));
        if (gameType == GameType.DOUBLES) {
            price = price.multiply(DOUBLES_MULTIPLIER);
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }
}
