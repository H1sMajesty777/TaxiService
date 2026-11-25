package ru.mirea.taxi.backend.domain;

import java.math.BigDecimal;

public enum Priority {
    HIGH, NORMAL, LOW;

    public static final BigDecimal HIGH_FARE = new BigDecimal("1200");
    public static final BigDecimal NORMAL_FARE = new BigDecimal("750");

    public static Priority calculate(boolean childSeat, BigDecimal fare) {
        if (childSeat || fare.compareTo(HIGH_FARE) >= 0) {
            return HIGH;
        }
        return fare.compareTo(NORMAL_FARE) >= 0 ? NORMAL : LOW;
    }
}
