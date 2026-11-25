package ru.mirea.taxi.service;

import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.model.RideRequest;

import java.math.BigDecimal;

public final class RideRequestRules {
    private static final BigDecimal MAX_FARE = new BigDecimal("9999999.99");

    private RideRequestRules() {
    }

    public static RideRequest validate(RideRequest request) {
        String pickup = validateAddress(request.pickupAddress(), "Адрес подачи");
        String dropoff = validateAddress(request.dropoffAddress(), "Адрес назначения");
        if (pickup.equalsIgnoreCase(dropoff)) {
            throw new BusinessException("Адреса подачи и назначения должны различаться.");
        }
        if (request.pickupAt() == null || request.pickupAt().getYear() < 2000
                || request.pickupAt().getYear() > 2099 || request.pickupAt().getSecond() != 0
                || request.pickupAt().getNano() != 0) {
            throw new BusinessException("Укажите дату подачи в 2000–2099 годах с точностью до минуты.");
        }
        BigDecimal fare = request.fare();
        if (fare == null || fare.signum() <= 0 || fare.compareTo(MAX_FARE) > 0
                || fare.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("Стоимость должна быть от 0,01 до 9 999 999,99 ₽, с точностью до копеек.");
        }
        return new RideRequest(request.passengerId(), pickup, dropoff, request.pickupAt(), fare);
    }

    private static String validateAddress(String address, String label) {
        String cleaned = address == null ? "" : address.strip();
        if (cleaned.length() < 3 || cleaned.length() > 150 || cleaned.contains("\u0000")) {
            throw new BusinessException(label + " должен содержать от 3 до 150 символов.");
        }
        return cleaned;
    }
}
