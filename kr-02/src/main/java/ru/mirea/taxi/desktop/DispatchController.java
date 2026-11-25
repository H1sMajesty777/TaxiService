package ru.mirea.taxi.desktop;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.exception.DatabaseException;
import ru.mirea.taxi.model.Passenger;
import ru.mirea.taxi.model.RideOrder;
import ru.mirea.taxi.model.RideStatus;
import ru.mirea.taxi.service.PassengerService;
import ru.mirea.taxi.service.ReportService;
import ru.mirea.taxi.service.RideOrderService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class DispatchController {
    private static final DateTimeFormatter SCHEDULE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final RideOrderService rides;
    private final PassengerService passengers;
    private final ReportService reports;
    private final ObservableList<RideOrder> orderRows = FXCollections.observableArrayList();
    private final ObservableList<Passenger> passengerRows = FXCollections.observableArrayList();
    private final FilteredList<RideOrder> filteredRows = new FilteredList<>(orderRows);
    private final SortedList<RideOrder> sortedRows = new SortedList<>(filteredRows);

    @FXML private TableView<RideOrder> orderTable;
    @FXML private TableView<Passenger> passengerTable;
    @FXML private TableColumn<RideOrder, Long> orderId;
    @FXML private TableColumn<RideOrder, String> orderPassenger;
    @FXML private TableColumn<RideOrder, LocalDateTime> orderPickupTime;
    @FXML private TableColumn<RideOrder, String> orderPickup;
    @FXML private TableColumn<RideOrder, String> orderDropoff;
    @FXML private TableColumn<RideOrder, BigDecimal> orderFare;
    @FXML private TableColumn<RideOrder, String> orderStatus;
    @FXML private TableColumn<Passenger, Long> passengerId;
    @FXML private TableColumn<Passenger, String> passengerName;
    @FXML private TableColumn<Passenger, String> passengerPhone;
    @FXML private TextField queryField;
    @FXML private DatePicker pickupDate;
    @FXML private ComboBox<RideStatus> statusFilter;
    @FXML private Label passengerDetails;
    @FXML private Label footer;
    @FXML private Button editButton;
    @FXML private Button statusButton;
    @FXML private Button deleteButton;

    DispatchController(RideOrderService rides, PassengerService passengers, ReportService reports) {
        this.rides = rides;
        this.passengers = passengers;
        this.reports = reports;
    }

    @FXML
    private void initialize() {
        bindColumn(orderId, RideOrder::id);
        bindColumn(orderPassenger, ride -> ride.passenger().fullName());
        bindColumn(orderPickupTime, RideOrder::pickupAt);
        bindColumn(orderPickup, RideOrder::pickupAddress);
        bindColumn(orderDropoff, RideOrder::dropoffAddress);
        bindColumn(orderFare, RideOrder::fare);
        bindColumn(orderStatus, ride -> ride.status().label());
        bindColumn(passengerId, Passenger::id);
        bindColumn(passengerName, Passenger::fullName);
        bindColumn(passengerPhone, Passenger::phone);

        orderPickupTime.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : SCHEDULE.format(value));
            }
        });
        sortedRows.comparatorProperty().bind(orderTable.comparatorProperty());
        orderTable.setItems(sortedRows);
        passengerTable.setItems(passengerRows);
        orderPickupTime.setSortType(TableColumn.SortType.DESCENDING);
        orderTable.getSortOrder().add(orderPickupTime);

        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(RideStatus.values());
        statusFilter.setConverter(StatusLabels.statusConverter("Все статусы"));
        statusFilter.setPromptText("Все статусы");
        pickupDate.setEditable(false);
        queryField.textProperty().addListener((property, before, after) -> refilter());
        pickupDate.valueProperty().addListener((property, before, after) -> refilter());
        statusFilter.valueProperty().addListener((property, before, after) -> refilter());
        orderTable.getSelectionModel().selectedItemProperty().addListener((property, before, selected) -> {
            showPassengerDetails(selected);
            updateActionButtons(selected);
        });
        updateActionButtons(null);
    }

    @FXML
    void refresh() {
        try {
            List<RideOrder> latestRides = rides.listRides();
            List<Passenger> latestPassengers = passengers.listPassengers();
            orderRows.setAll(latestRides);
            passengerRows.setAll(latestPassengers);
            showCount();
        } catch (DatabaseException error) {
            footer.setText("Обновление не выполнено");
            showError(error);
        }
    }

    @FXML
    private void resetFilters() {
        queryField.clear();
        pickupDate.setValue(null);
        statusFilter.setValue(null);
        refilter();
    }

    @FXML
    private void createRide() {
        perform(() -> {
            var form = new RideForm(owner(), passengers.listPassengers(), null, rides::createRide);
            if (form.showAndSave()) {
                refresh();
            }
        });
    }

    @FXML
    private void editRide() {
        RideOrder order = selectedOrder();
        if (order == null) {
            return;
        }
        perform(() -> {
            var form = new RideForm(owner(), passengers.listPassengers(), order,
                    request -> rides.editRide(order.id(), order.version(), request));
            if (form.showAndSave()) {
                refresh();
            }
        });
    }

    @FXML
    private void changeStatus() {
        RideOrder order = selectedOrder();
        if (order == null) {
            return;
        }
        List<RideStatus> options = Arrays.stream(RideStatus.values()).filter(order.status()::canChangeTo).toList();
        if (options.isEmpty()) {
            return;
        }
        var choices = options.stream().map(RideStatus::label).toList();
        var dialog = new ChoiceDialog<>(choices.getFirst(), choices);
        dialog.initOwner(owner());
        dialog.setTitle("Смена статуса");
        dialog.setHeaderText("Заказ № " + order.id());
        dialog.setContentText("Следующий статус");
        dialog.showAndWait().ifPresent(label -> perform(() -> {
            rides.changeStatus(order.id(), order.version(), options.get(choices.indexOf(label)));
            refresh();
        }));
    }

    @FXML
    private void deleteRide() {
        RideOrder order = selectedOrder();
        if (order == null) {
            return;
        }
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Удалить заказ № " + order.id() + "?",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.initOwner(owner());
        confirmation.setTitle("Удаление заказа");
        confirmation.setHeaderText(null);
        confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(choice -> perform(() -> {
            rides.deleteRide(order.id(), order.version());
            refresh();
        }));
    }

    @FXML
    private void showStatistics() {
        perform(() -> {
            var stats = reports.getStatistics();
            var text = new StringBuilder()
                    .append("Пассажиры: ").append(stats.passengerCount()).append('\n')
                    .append("Заказы: ").append(stats.rideCount()).append('\n');
            for (RideStatus status : RideStatus.values()) {
                text.append(status.label()).append(": ").append(stats.countByStatus(status)).append('\n');
            }
            text.append("Выручка по завершённым: ").append(stats.completedFareTotal().toPlainString()).append(" ₽");
            var dialog = new Alert(Alert.AlertType.INFORMATION, text.toString(), ButtonType.OK);
            dialog.initOwner(owner());
            dialog.setTitle("Статистика");
            dialog.setHeaderText("Состояние всей службы");
            dialog.showAndWait();
        });
    }

    @FXML
    private void exportRides() {
        var chooser = new FileChooser();
        chooser.setTitle("Экспорт заказов");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Книга Excel", "*.xlsx"));
        chooser.setInitialFileName("ride-orders.xlsx");
        var file = chooser.showSaveDialog(owner());
        if (file != null) {
            perform(() -> {
                var path = file.toPath();
                if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                    path = path.resolveSibling(path.getFileName() + ".xlsx");
                }
                reports.exportRides(path);
                footer.setText("Заказы выгружены в " + path.getFileName());
            });
        }
    }

    private void refilter() {
        var query = queryField.getText().strip().toLowerCase(Locale.ROOT);
        var date = pickupDate.getValue();
        var status = statusFilter.getValue();
        filteredRows.setPredicate(order -> (status == null || order.status() == status)
                && (date == null || order.pickupAt().toLocalDate().equals(date))
                && (matches(order.pickupAddress(), query)
                    || matches(order.dropoffAddress(), query)
                    || matches(order.passenger().fullName(), query)
                    || Long.toString(order.id()).contains(query)));
        showCount();
    }

    private void showCount() {
        footer.setText("Заказы " + filteredRows.size() + " из " + orderRows.size()
                + "  ·  Пассажиры " + passengerRows.size());
    }

    private void showPassengerDetails(RideOrder order) {
        if (order == null) {
            passengerDetails.setText("Выберите заказ, чтобы увидеть контакт пассажира.");
            return;
        }
        Passenger passenger = order.passenger();
        passengerDetails.setText("Пассажир #" + passenger.id() + " · "
                + passenger.fullName() + " · " + passenger.phone());
    }

    private void updateActionButtons(RideOrder order) {
        editButton.setDisable(order == null
                || (order.status() != RideStatus.NEW && order.status() != RideStatus.CONFIRMED));
        statusButton.setDisable(order == null
                || order.status() == RideStatus.CANCELLED || order.status() == RideStatus.COMPLETED);
        deleteButton.setDisable(order == null
                || (order.status() != RideStatus.NEW && order.status() != RideStatus.CANCELLED));
    }

    private RideOrder selectedOrder() {
        return orderTable.getSelectionModel().getSelectedItem();
    }

    private Window owner() {
        return orderTable.getScene().getWindow();
    }

    private void showError(Exception error) {
        var alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
        alert.initOwner(owner());
        alert.setTitle("Служба такси");
        alert.setHeaderText("Операция не выполнена");
        alert.showAndWait();
    }

    private void perform(DeskAction action) {
        try {
            action.run();
        } catch (BusinessException | DatabaseException | IOException error) {
            showError(error);
        }
    }

    private static boolean matches(String text, String query) {
        return text.toLowerCase(Locale.ROOT).contains(query);
    }

    private static <R, V> void bindColumn(TableColumn<R, V> column, Function<R, V> value) {
        column.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(value.apply(row.getValue())));
    }

    @FunctionalInterface
    private interface DeskAction {
        void run() throws IOException;
    }
}
