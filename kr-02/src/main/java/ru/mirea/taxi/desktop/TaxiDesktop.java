package ru.mirea.taxi.desktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.mirea.taxi.repository.Database;
import ru.mirea.taxi.repository.JdbcPassengerRepository;
import ru.mirea.taxi.repository.JdbcRideOrderRepository;
import ru.mirea.taxi.service.PassengerService;
import ru.mirea.taxi.service.ReportService;
import ru.mirea.taxi.service.RideOrderService;
import ru.mirea.taxi.util.ExcelExporter;

import java.io.IOException;

public final class TaxiDesktop extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        var database = new Database(
                System.getenv().getOrDefault("TAXI_DB_URL", "jdbc:postgresql://127.0.0.1:5434/taxi"),
                System.getenv().getOrDefault("TAXI_DB_USER", "taxi"),
                System.getenv().getOrDefault("TAXI_DB_PASSWORD", "")
        );
        var passengers = new PassengerService(new JdbcPassengerRepository(database));
        var rides = new RideOrderService(new JdbcRideOrderRepository(database), passengers);
        var reports = new ReportService(passengers, rides, new ExcelExporter());

        var loader = new FXMLLoader(TaxiDesktop.class.getResource("dispatch.fxml"));
        loader.setController(new DispatchController(rides, passengers, reports));
        var scene = new Scene(loader.load(), 1180, 706);
        scene.getStylesheets().add(TaxiDesktop.class.getResource("dispatch.css").toExternalForm());

        stage.setTitle("Служба такси — рабочее место диспетчера");
        stage.setMinWidth(875);
        stage.setMinHeight(628);
        stage.setScene(scene);
        stage.show();
        loader.<DispatchController>getController().refresh();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
