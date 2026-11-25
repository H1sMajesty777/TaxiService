package ru.mirea.taxi.mobile.ui;

import android.app.AlertDialog;
import android.text.InputType;
import ru.mirea.taxi.mobile.net.Models.*;

import java.util.ArrayList;
import java.util.List;

import static ru.mirea.taxi.mobile.net.Gateway.call;

final class RideEditor {
    private final MainActivity activity;
    private final Views view;

    RideEditor(MainActivity activity, Views view) {
        this.activity = activity;
        this.view = view;
    }

    void open(Ride ride, Runnable reload) {
        activity.task(() -> new References(call(activity.api().passengers()), call(activity.api().tariffs())),
                references -> showForm(ride, references, reload));
    }

    private void showForm(Ride ride, References references, Runnable reload) {
        var fields = view.column();
        var passengers = new ArrayList<Views.Option<Long>>();
        references.passengers().forEach(passenger ->
                passengers.add(new Views.Option<>(passenger.id(), passenger.fullName())));
        var passenger = view.select(fields, "Пассажир", passengers, ride == null ? null : ride.passengerId());
        var tariffs = new ArrayList<Views.Option<String>>();
        for (var tariff : references.tariffs()) {
            if (tariff.active() || (ride != null && ride.tariffId().equals(tariff.id()))) {
                tariffs.add(new Views.Option<>(tariff.id(), tariff.name()));
            }
        }
        var tariff = view.select(fields, "Тариф", tariffs, ride == null ? null : ride.tariffId());
        var pickup = view.field(fields, "Адрес подачи", ride == null ? "" : ride.pickupAddress(), MainActivity.TEXT);
        var dropoff = view.field(fields, "Адрес назначения", ride == null ? "" : ride.dropoffAddress(), MainActivity.TEXT);
        var at = view.field(fields, "Подача МСК · ГГГГ-ММ-ДД ЧЧ:ММ",
                ride == null ? "" : TaxiText.time(ride.pickupAt()), MainActivity.TEXT);
        var fare = view.field(fields, "Стоимость в рублях", ride == null ? "" : ride.fare().toPlainString(),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        var statusOptions = new ArrayList<Views.Option<String>>();
        String selectedStatus = ride == null ? "NEW" : ride.status();
        for (String code : statuses(ride)) {
            statusOptions.add(new Views.Option<>(code, TaxiText.status(code)));
        }
        var status = view.select(fields, "Статус", statusOptions, selectedStatus);
        var tags = view.field(fields, "Теги через запятую", ride == null ? "" : String.join(", ", ride.tags()),
                MainActivity.TEXT);
        var seat = view.check(fields, "Нужно детское кресло", ride != null && ride.childSeat());
        var error = view.text("", 14, Views.ERROR, false);
        fields.addView(error);

        var dialog = view.form(ride == null ? "Новый заказ" : "Изменить " + ride.externalCode(), fields);
        dialog.show();
        var save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setOnClickListener(button -> {
            RideInput input;
            try {
                if (passenger.value() == null || tariff.value() == null) {
                    throw new IllegalArgumentException("Выберите пассажира и тариф.");
                }
                String from = TaxiText.address(Views.value(pickup));
                String to = TaxiText.address(Views.value(dropoff));
                if (from.equalsIgnoreCase(to)) throw new IllegalArgumentException("Укажите разные адреса.");
                input = new RideInput(null, passenger.value(), tariff.value(), from, to,
                        TaxiText.pickupAt(Views.value(at)), TaxiText.fare(Views.value(fare)), status.value(),
                        seat.isChecked(), TaxiText.tags(Views.value(tags)), ride == null ? null : ride.version());
            } catch (IllegalArgumentException invalid) {
                error.setText(invalid.getMessage());
                return;
            }
            save.setEnabled(false);
            activity.task(() -> ride == null ? call(activity.api().createRide(input))
                    : call(activity.api().updateRide(ride.id(), input)), saved -> {
                dialog.dismiss();
                reload.run();
                activity.notifyUser("Заказ сохранён.");
            }, failure -> {
                save.setEnabled(true);
                activity.onFailure(failure, error);
            });
        });
    }

    void note(String ride, Note note, Runnable reload) {
        var fields = view.column();
        var text = view.field(fields, "Текст", note == null ? "" : note.body(),
                MainActivity.TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        var pinned = view.check(fields, "Закрепить заметку", note != null && note.pinned());
        var error = view.text("", 14, Views.ERROR, false);
        fields.addView(error);
        var dialog = view.form(note == null ? "Новая заметка" : "Изменить заметку", fields);
        dialog.show();
        var save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setOnClickListener(button -> {
            String body = Views.value(text);
            if (body.isEmpty() || body.length() > 1500) {
                text.setError("Введите от 1 до 1500 символов.");
                return;
            }
            var input = new NoteInput(body, pinned.isChecked());
            save.setEnabled(false);
            activity.task(() -> note == null ? call(activity.api().createNote(ride, input))
                    : call(activity.api().updateNote(ride, note.id(), input)), changed -> {
                dialog.dismiss();
                reload.run();
            }, failure -> {
                save.setEnabled(true);
                activity.onFailure(failure, error);
            });
        });
    }

    private static List<String> statuses(Ride ride) {
        if (ride == null) return List.of("NEW");
        return switch (ride.status()) {
            case "NEW" -> List.of("NEW", "CONFIRMED", "CANCELLED");
            case "CONFIRMED" -> List.of("CONFIRMED", "IN_PROGRESS", "CANCELLED");
            case "IN_PROGRESS" -> List.of("IN_PROGRESS", "COMPLETED");
            default -> List.of(ride.status());
        };
    }

    private record References(List<Passenger> passengers, List<Tariff> tariffs) {}
}
