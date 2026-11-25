package ru.mirea.taxi.ui;

import ru.mirea.taxi.model.Passenger;
import ru.mirea.taxi.model.RideOrder;
import ru.mirea.taxi.model.RideStatus;
import ru.mirea.taxi.model.TaxiStatistics;

import java.io.PrintStream;
import java.util.List;

final class ConsoleView {
    private final PrintStream output;

    ConsoleView(PrintStream output) {
        this.output = output;
    }

    void showPassengers(List<Passenger> passengers) {
        if (passengers.isEmpty()) {
            output.println("Пассажиров пока нет.");
            return;
        }
        passengers.forEach(this::showPassenger);
    }

    void showPassenger(Passenger passenger) {
        output.printf("#%d | %s | %s%n", passenger.id(), passenger.fullName(), passenger.phone());
    }

    void showRides(List<RideOrder> rides) {
        if (rides.isEmpty()) {
            output.println("Заказы не найдены.");
            return;
        }
        rides.forEach(this::showRide);
    }

    void showRide(RideOrder ride) {
        output.printf("#%d | %s МСК | %s | %s ₽ | %s → %s | пассажир #%d %s%n",
                ride.id(), ConsoleInput.DATE_TIME.format(ride.pickupAt()),
                ride.status().label(), ride.fare().toPlainString(),
                ride.pickupAddress(), ride.dropoffAddress(), ride.passenger().id(), ride.passenger().fullName());
    }

    void showStatistics(TaxiStatistics statistics) {
        output.println("Пассажиры: " + statistics.passengerCount());
        output.println("Заказы: " + statistics.rideCount());
        for (RideStatus status : RideStatus.values()) {
            output.println(status.label() + ": " + statistics.countByStatus(status));
        }
        output.println("Сумма завершённых поездок: " + statistics.completedFareTotal().toPlainString() + " ₽");
    }
}
