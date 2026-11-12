package com.finemp.planning.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GoalProgressTest {

    @Test
    void calculatesUpAndDownProgressFromBaselineToTarget() {
        assertThat(GoalProgress.calculate(
                GoalDirection.UP,
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("150")))
                .isEqualByComparingTo("0.5000");

        assertThat(GoalProgress.calculate(
                GoalDirection.DOWN,
                new BigDecimal("100"),
                new BigDecimal("50"),
                new BigDecimal("75")))
                .isEqualByComparingTo("0.5000");
    }

    @Test
    void clampsProgressBetweenZeroAndOnePointTwo() {
        assertThat(GoalProgress.calculate(
                GoalDirection.UP,
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("50")))
                .isEqualByComparingTo("0.0000");

        assertThat(GoalProgress.calculate(
                GoalDirection.UP,
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("250")))
                .isEqualByComparingTo("1.2000");
    }

    @Test
    void rejectsTargetsThatContradictTheDirection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GoalProgress.calculate(
                        GoalDirection.UP,
                        new BigDecimal("100"),
                        new BigDecimal("50"),
                        new BigDecimal("75")));

        assertThrows(
                IllegalArgumentException.class,
                () -> GoalProgress.calculate(
                        GoalDirection.DOWN,
                        new BigDecimal("50"),
                        new BigDecimal("100"),
                        new BigDecimal("75")));
    }

    @Test
    void targetAchievementRespectsGoalDirection() {
        assertThat(GoalProgress.isAchieved(
                GoalDirection.UP,
                new BigDecimal("200"),
                new BigDecimal("200"))).isTrue();

        assertThat(GoalProgress.isAchieved(
                GoalDirection.UP,
                new BigDecimal("199.99"),
                new BigDecimal("200"))).isFalse();

        assertThat(GoalProgress.isAchieved(
                GoalDirection.DOWN,
                new BigDecimal("50"),
                new BigDecimal("50"))).isTrue();

        assertThat(GoalProgress.isAchieved(
                GoalDirection.DOWN,
                new BigDecimal("50.01"),
                new BigDecimal("50"))).isFalse();
    }
}
