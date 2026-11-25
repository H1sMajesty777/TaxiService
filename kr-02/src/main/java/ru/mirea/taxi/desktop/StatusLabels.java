package ru.mirea.taxi.desktop;

import javafx.util.StringConverter;
import ru.mirea.taxi.model.RideStatus;

import java.util.function.Function;

final class StatusLabels {
    private StatusLabels() {
    }

    static StringConverter<RideStatus> statusConverter(String emptyLabel) {
        return displayConverter(RideStatus::label, emptyLabel);
    }

    static <T> StringConverter<T> displayConverter(Function<T, String> label, String emptyLabel) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? emptyLabel : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                throw new UnsupportedOperationException("Этот список поддерживает только выбор из готовых значений.");
            }
        };
    }
}
