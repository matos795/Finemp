package com.finemp.planning.goal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class GoalProgress {

    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final BigDecimal MAX = new BigDecimal("1.2000");

    private GoalProgress() {
    }

    public static BigDecimal calculate(
            GoalDirection direction,
            BigDecimal baseline,
            BigDecimal target,
            BigDecimal current) {

        Objects.requireNonNull(direction);
        Objects.requireNonNull(baseline);
        Objects.requireNonNull(target);
        Objects.requireNonNull(current);

        var denominator = target.subtract(baseline);

        if (denominator.signum() == 0
                || (direction == GoalDirection.UP && denominator.signum() < 0)
                || (direction == GoalDirection.DOWN && denominator.signum() > 0)) {
            throw new IllegalArgumentException(
                    "Alvo incompatível com o sentido da meta.");
        }

        var raw = current.subtract(baseline)
                .divide(denominator, 4, RoundingMode.HALF_UP);

        if (raw.compareTo(ZERO) < 0) {
            return ZERO;
        }

        if (raw.compareTo(MAX) > 0) {
            return MAX;
        }

        return raw.setScale(4, RoundingMode.HALF_UP);
    }

    public static boolean isAchieved(
            GoalDirection direction,
            BigDecimal current,
            BigDecimal target) {

        Objects.requireNonNull(direction);
        Objects.requireNonNull(current);
        Objects.requireNonNull(target);

        return switch (direction) {
            case UP -> current.compareTo(target) >= 0;
            case DOWN -> current.compareTo(target) <= 0;
        };
    }
}
