package ru.mirea.taxi.mobile.ui;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TaxiText {
    static final Map<String, String> STATUSES = Map.of(
            "NEW", "Новый", "CONFIRMED", "Подтверждён", "IN_PROGRESS", "В поездке",
            "COMPLETED", "Завершён", "CANCELLED", "Отменён");
    static final Map<String, String> PRIORITIES = Map.of("LOW", "Низкий", "NORMAL", "Обычный", "HIGH", "Высокий");
    static final Map<String, String> ROLES = Map.of("VIEWER", "Наблюдатель", "DISPATCHER", "Диспетчер", "ADMIN", "Администратор");
    private static final DateTimeFormatter LOCAL_INPUT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private TaxiText() {}

    static String status(String status) {
        return status == null ? "—" : STATUSES.getOrDefault(status, status);
    }

    static String time(String iso) {
        return iso == null ? "—" : iso.substring(0, Math.min(16, iso.length())).replace('T', ' ');
    }

    static String amount(BigDecimal fare) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU")).format(fare);
    }

    static BigDecimal fare(String raw) {
        try {
            var amount = new BigDecimal(raw.trim().replace(',', '.'));
            if (amount.signum() > 0 && amount.compareTo(new BigDecimal("9999999.99")) <= 0
                    && amount.stripTrailingZeros().scale() <= 2) {
                return amount;
            }
        } catch (NumberFormatException ignored) {
            // Преобразуем ошибку формата в сообщение формы.
        }
        throw new IllegalArgumentException("Введите стоимость от 0,01 до 9 999 999,99 ₽ с точностью до копеек.");
    }

    static String pickupAt(String text) {
        try {
            var result = LocalDateTime.parse(text.trim(), LOCAL_INPUT);
            if (result.getYear() >= 2000 && result.getYear() <= 2099) {
                return result.toString();
            }
        } catch (DateTimeParseException ignored) {
            // Клиент показывает один формат для диапазона и для календарной ошибки.
        }
        throw new IllegalArgumentException("Введите дату и время подачи в формате ГГГГ-ММ-ДД ЧЧ:ММ.");
    }

    static String address(String value) {
        String text = value.trim();
        if (text.length() < 3 || text.length() > 150) {
            throw new IllegalArgumentException("Адрес должен содержать от 3 до 150 символов.");
        }
        return text;
    }

    static Set<String> tags(String raw) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw.split(",")) {
            String tag = value.trim();
            if (tag.length() > 40 || tag.contains("|")) {
                throw new IllegalArgumentException("Тег должен быть не длиннее 40 символов и без знака |.");
            }
            if (!tag.isEmpty()) {
                result.add(tag);
            }
        }
        if (result.size() > 16) {
            throw new IllegalArgumentException("В заказе может быть не больше 16 тегов.");
        }
        return result;
    }

    static void password(String value) {
        if (value.codePointCount(0, value.length()) < 12 || value.getBytes(StandardCharsets.UTF_8).length > 72
                || value.codePoints().noneMatch(Character::isUpperCase)
                || value.codePoints().noneMatch(Character::isLowerCase)
                || value.codePoints().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Минимум 12 символов, с прописной, строчной буквой и цифрой.");
        }
    }
}
