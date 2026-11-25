package ru.mirea.taxi.ui;

import ru.mirea.taxi.exception.BusinessException;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Scanner;

final class ConsoleInput {
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    private final Scanner input;
    private final PrintStream output;

    ConsoleInput(Scanner input, PrintStream output) {
        this.input = input;
        this.output = output;
    }

    String readLine(String label) {
        output.print(label);
        output.flush();
        return input.nextLine().strip();
    }

    int readChoice(String label) {
        try {
            return Integer.parseInt(readLine(label));
        } catch (NumberFormatException error) {
            throw new BusinessException("Номер пункта должен быть целым числом.");
        }
    }

    long readPassengerId() {
        return readPassengerId(null);
    }

    long readPassengerId(Long currentId) {
        String value = readWithDefault("ID пассажира", currentId);
        return parseId(value);
    }

    long readRideId() {
        return parseId(readLine("ID заказа: "));
    }

    String readWithDefault(String label, Object currentValue) {
        if (currentValue == null) {
            return readLine(label + ": ");
        }
        String value = readLine(label + " [" + currentValue + "]: ");
        return value.isEmpty() ? currentValue.toString() : value;
    }

    BigDecimal readFare(BigDecimal currentFare) {
        String value = readWithDefault("Стоимость в рублях", currentFare);
        if (!value.matches("[+-]?[0-9]+([.,][0-9]+)?")) {
            throw new BusinessException("Стоимость должна быть числом, например 650 или 650,50.");
        }
        return new BigDecimal(value.replace(',', '.'));
    }

    LocalDateTime readPickupTime(LocalDateTime currentTime) {
        String current = currentTime == null ? null : DATE_TIME.format(currentTime);
        try {
            return LocalDateTime.parse(readWithDefault("Подача по МСК (ГГГГ-ММ-ДД ЧЧ:ММ)", current), DATE_TIME);
        } catch (DateTimeParseException error) {
            throw new BusinessException("Введите существующие дату и время в формате ГГГГ-ММ-ДД ЧЧ:ММ.");
        }
    }

    LocalDate readDate(String label) {
        try {
            return LocalDate.parse(readLine(label + " (ГГГГ-ММ-ДД): "));
        } catch (DateTimeParseException error) {
            throw new BusinessException("Введите существующую дату в формате ГГГГ-ММ-ДД.");
        }
    }

    boolean confirmDelete() {
        String answer = readLine("Подтвердите удаление (да/нет): ").toLowerCase(Locale.ROOT);
        return switch (answer) {
            case "да" -> true;
            case "нет" -> false;
            default -> throw new BusinessException("Введите «да» или «нет».");
        };
    }

    private static long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException error) {
            throw new BusinessException("ID должен быть целым числом.");
        }
        throw new BusinessException("ID должен быть больше нуля.");
    }
}
