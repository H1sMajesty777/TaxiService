package ru.mirea.taxi.model;

import java.math.BigDecimal;
import java.util.Map;

public record TaxiStatistics(long passengerCount, long rideCount, Map<RideStatus, Long> ridesByStatus,
                             BigDecimal completedFareTotal) {
    public TaxiStatistics {
        ridesByStatus = Map.copyOf(ridesByStatus);
    }

    public long countByStatus(RideStatus status) {
        return ridesByStatus.getOrDefault(status, 0L);
    }
}
