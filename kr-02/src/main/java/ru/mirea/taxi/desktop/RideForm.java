package ru.mirea.taxi.desktop;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.model.Passenger;
import ru.mirea.taxi.model.RideOrder;
import ru.mirea.taxi.model.RideRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class RideForm {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final ComboBox<Passenger> passenger = new ComboBox<>();
    private final TextField pickup = new TextField();
    private final TextField dropoff = new TextField();
    private final DatePicker date = new DatePicker();
    private final TextField time = new TextField();
    private final TextField fare = new TextField();
    private final Label message = new Label();

    RideForm(Window owner, List<Passenger> passengers, RideOrder current, Consumer<RideRequest> save) {
        dialog.initOwner(owner);
        dialog.setTitle(current == null ? "Новый заказ" : "Изменить заказ № " + current.id());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().add(TaxiDesktop.class.getResource("dispatch.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("ride-form");

        passenger.getItems().setAll(passengers);
        passenger.setConverter(StatusLabels.displayConverter(
                item -> "#" + item.id() + " · " + item.fullName(), "Выберите пассажира"
        ));
        passenger.setMaxWidth(Double.MAX_VALUE);
        date.setEditable(false);
        time.setPromptText("ЧЧ:ММ");
        fare.setPromptText("Например, 650,50");
        date.setValue(java.time.LocalDate.now(java.time.ZoneId.of("Europe/Moscow")));

        if (current != null) {
            passengers.stream().filter(item -> item.id() == current.passenger().id())
                    .findFirst().ifPresent(passenger::setValue);
            pickup.setText(current.pickupAddress());
            dropoff.setText(current.dropoffAddress());
            date.setValue(current.pickupAt().toLocalDate());
            time.setText(current.pickupAt().format(TIME_FORMAT));
            fare.setText(current.fare().toPlainString());
        }

        var form = new GridPane();
        form.setPadding(new Insets(12));
        form.setHgap(12);
        form.setVgap(12);
        form.addRow(0, new Label("Пассажир"), passenger);
        form.addRow(1, new Label("Адрес подачи"), pickup);
        form.addRow(2, new Label("Адрес назначения"), dropoff);
        form.addRow(3, new Label("Дата подачи"), date);
        form.addRow(4, new Label("Время подачи (МСК)"), time);
        form.addRow(5, new Label("Стоимость, ₽"), fare);
        message.getStyleClass().add("error");
        message.setWrapText(true);
        message.setMaxWidth(430);
        form.add(message, 0, 6, 2, 1);
        dialog.getDialogPane().setContent(form);

        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                save.accept(readRequest());
            } catch (BusinessException | ru.mirea.taxi.exception.DatabaseException error) {
                message.setText(error.getMessage());
                event.consume();
            }
        });
    }

    boolean showAndSave() {
        return dialog.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private RideRequest readRequest() {
        if (passenger.getValue() == null || date.getValue() == null) {
            throw new BusinessException("Укажите пассажира и дату подачи.");
        }
        String clock = time.getText().strip();
        LocalTime pickupTime;
        try {
            pickupTime = LocalTime.parse(clock);
            if (!clock.matches("[0-9]{2}:[0-9]{2}")) {
                throw new DateTimeParseException("Ожидается ЧЧ:ММ", clock, 0);
            }
        } catch (DateTimeParseException error) {
            throw new BusinessException("Введите время подачи в формате ЧЧ:ММ, например 09:30.");
        }
        BigDecimal amount;
        try {
            String amountText = fare.getText().strip();
            if (!amountText.matches("[0-9]+([.,][0-9]+)?")) {
                throw new NumberFormatException("Ожидается сумма в рублях и копейках");
            }
            amount = new BigDecimal(amountText.replace(',', '.'));
        } catch (NumberFormatException error) {
            throw new BusinessException("Введите стоимость в рублях, например 650,50.");
        }
        return new RideRequest(passenger.getValue().id(), pickup.getText(), dropoff.getText(),
                LocalDateTime.of(date.getValue(), pickupTime), amount);
    }
}
