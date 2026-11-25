package ru.mirea.taxi.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RideOrder(long id, Passenger passenger, String pickupAddress, String dropoffAddress,
                        LocalDateTime pickupAt, BigDecimal fare, RideStatus status, long version) {
}
