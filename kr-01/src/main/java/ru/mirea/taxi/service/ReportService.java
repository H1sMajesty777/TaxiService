package ru.mirea.taxi.service;

import ru.mirea.taxi.model.RideOrder;
import ru.mirea.taxi.model.RideStatus;
import ru.mirea.taxi.model.TaxiStatistics;
import ru.mirea.taxi.util.ExcelExporter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ReportService {
    private final PassengerService passengers;
    private final RideOrderService rides;
    private final ExcelExporter exporter;

    public ReportService(PassengerService passengers, RideOrderService rides, ExcelExporter exporter) {
        this.passengers = passengers;
        this.rides = rides;
        this.exporter = exporter;
    }

    public TaxiStatistics getStatistics() {
        List<RideOrder> rideOrders = rides.listRides();
        Map<RideStatus, Long> counts = new EnumMap<>(RideStatus.class);
        BigDecimal completedFareTotal = BigDecimal.ZERO;

        for (RideOrder ride : rideOrders) {
            counts.merge(ride.status(), 1L, Long::sum);
            if (ride.status() == RideStatus.COMPLETED) {
                completedFareTotal = completedFareTotal.add(ride.fare());
            }
        }
        return new TaxiStatistics(passengers.listPassengers().size(), rideOrders.size(), counts, completedFareTotal);
    }

    public Path exportRides(Path file) throws IOException {
        return exporter.writeRides(file, rides.listRides());
    }
}
