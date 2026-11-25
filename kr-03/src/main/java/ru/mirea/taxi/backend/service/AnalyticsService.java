package ru.mirea.taxi.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.taxi.backend.domain.Priority;
import ru.mirea.taxi.backend.domain.Ride;
import ru.mirea.taxi.backend.repository.RideRepository;
import ru.mirea.taxi.model.RideStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@Service
public class AnalyticsService {
    private final RideRepository rides;

    public AnalyticsService(RideRepository rides) {
        this.rides = rides;
    }

    @Transactional(readOnly = true)
    public Summary summarize() {
        long active = 0;
        long childSeats = 0;
        long total = 0;
        BigDecimal amounts = BigDecimal.ZERO;
        Map<String, Long> tariffs = new HashMap<>();
        Map<RideStatus, Long> statuses = new EnumMap<>(RideStatus.class);
        Map<Priority, Long> priorities = new EnumMap<>(Priority.class);

        for (Ride ride : rides.findAll()) {
            total++;
            if (!ride.isArchived()) {
                active++;
            }
            if (ride.isChildSeat()) {
                childSeats++;
            }
            amounts = amounts.add(ride.getFare());
            tariffs.merge(ride.getTariff().getName(), 1L, Long::sum);
            statuses.merge(ride.getStatus(), 1L, Long::sum);
            priorities.merge(ride.getPriority(), 1L, Long::sum);
        }
        BigDecimal average = total == 0 ? BigDecimal.ZERO : amounts.divide(BigDecimal.valueOf(total), 2,
                RoundingMode.HALF_UP);
        return new Summary(total, active, total - active, childSeats, average, tariffs, statuses, priorities);
    }

    public record Summary(long total, long active, long archived, long childSeat, BigDecimal averageFare,
                          Map<String, Long> byTariff, Map<RideStatus, Long> byStatus,
                          Map<Priority, Long> byPriority) {}
}
