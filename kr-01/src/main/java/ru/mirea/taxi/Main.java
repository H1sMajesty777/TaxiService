package ru.mirea.taxi;

import ru.mirea.taxi.repository.Database;
import ru.mirea.taxi.repository.JdbcPassengerRepository;
import ru.mirea.taxi.repository.JdbcRideOrderRepository;
import ru.mirea.taxi.service.PassengerService;
import ru.mirea.taxi.service.ReportService;
import ru.mirea.taxi.service.RideOrderService;
import ru.mirea.taxi.ui.TaxiConsole;
import ru.mirea.taxi.util.ExcelExporter;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class Main {
    public static void main(String[] args) {
        Database database = Database.fromEnvironment();
        PassengerService passengers = new PassengerService(new JdbcPassengerRepository(database));
        RideOrderService rides = new RideOrderService(new JdbcRideOrderRepository(database), passengers);
        ReportService reports = new ReportService(passengers, rides, new ExcelExporter());

        var console = System.console();
        var output = console == null
                ? new PrintStream(System.out, true, StandardCharsets.UTF_8)
                : System.out;
        try (var input = console == null
                ? new Scanner(System.in, StandardCharsets.UTF_8)
                : new Scanner(console.reader())) {
            new TaxiConsole(input, output, passengers, rides, reports).run();
        }
    }
}
