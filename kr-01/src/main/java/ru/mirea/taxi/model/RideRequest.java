package ru.mirea.taxi.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RideRequest(long passengerId, String pickupAddress, String dropoffAddress,
                          LocalDateTime pickupAt, BigDecimal fare) {
}
