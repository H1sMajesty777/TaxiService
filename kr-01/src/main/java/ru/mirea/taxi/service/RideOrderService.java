package ru.mirea.taxi.service;

import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.exception.EntityNotFoundException;
import ru.mirea.taxi.model.RideOrder;
import ru.mirea.taxi.model.RideRequest;
import ru.mirea.taxi.model.RideStatus;
import ru.mirea.taxi.repository.RideOrderRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RideOrderService {
    private final RideOrderRepository rides;
    private final PassengerService passengers;

    public RideOrderService(RideOrderRepository rides, PassengerService passengers) {
        this.rides = rides;
        this.passengers = passengers;
    }

    public List<RideOrder> listRides() {
        return rides.findAll();
    }

    public RideOrder getRide(long id) {
        return rides.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Заказ с ID " + id + " не найден."));
    }

    public long createRide(RideRequest request) {
        return rides.insert(validateDetails(request));
    }

    public void editRide(long id, long expectedVersion, RideRequest request) {
        RideOrder current = getRide(id);
        checkVersion(current, expectedVersion);
        if (current.status() != RideStatus.NEW && current.status() != RideStatus.CONFIRMED) {
            throw new BusinessException("Редактировать маршрут можно только у новых и подтверждённых заказов.");
        }
        requireUpdated(rides.updateDetails(id, expectedVersion, validateDetails(request)));
    }

    public void changeStatus(long id, long expectedVersion, RideStatus nextStatus) {
        RideOrder current = getRide(id);
        checkVersion(current, expectedVersion);
        if (nextStatus == null) {
            throw new BusinessException("Выберите новый статус заказа.");
        }
        if (!current.status().canChangeTo(nextStatus)) {
            throw new BusinessException(
                    "Переход «" + current.status().label() + " → " + nextStatus.label() + "» запрещён."
            );
        }
        requireUpdated(rides.updateStatus(id, expectedVersion, nextStatus));
    }

    public void deleteRide(long id, long expectedVersion) {
        RideOrder current = getRide(id);
        checkVersion(current, expectedVersion);
        if (current.status() != RideStatus.NEW && current.status() != RideStatus.CANCELLED) {
            throw new BusinessException("Удалить можно только новый или отменённый заказ.");
        }
        requireUpdated(rides.delete(id, expectedVersion));
    }

    public List<RideOrder> searchByAddress(String query) {
        String searchTerm = searchTerm(query);
        return rides.findAll().stream()
                .filter(ride -> contains(ride.pickupAddress(), searchTerm)
                        || contains(ride.dropoffAddress(), searchTerm))
                .toList();
    }

    public List<RideOrder> searchByPassengerName(String query) {
        String searchTerm = searchTerm(query);
        return rides.findAll().stream()
                .filter(ride -> contains(ride.passenger().fullName(), searchTerm))
                .toList();
    }

    public List<RideOrder> filterByStatus(RideStatus status) {
        if (status == null) {
            throw new BusinessException("Выберите статус для фильтра.");
        }
        return rides.findAll().stream()
                .filter(ride -> ride.status() == status)
                .toList();
    }

    public List<RideOrder> filterByPickupDates(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessException("Укажите диапазон дат, где начало не позже конца.");
        }
        return rides.findAll().stream()
                .filter(ride -> {
                    LocalDate pickupDate = ride.pickupAt().toLocalDate();
                    return !pickupDate.isBefore(from) && !pickupDate.isAfter(to);
                })
                .toList();
    }

    public List<RideOrder> sortByPickupTime() {
        return rides.findAll().stream()
                .sorted(Comparator.comparing(RideOrder::pickupAt).reversed().thenComparingLong(RideOrder::id))
                .toList();
    }

    public List<RideOrder> sortByFare() {
        return rides.findAll().stream()
                .sorted(Comparator.comparing(RideOrder::fare).thenComparingLong(RideOrder::id))
                .toList();
    }

    private RideRequest validateDetails(RideRequest request) {
        RideRequest validated = RideRequestRules.validate(request);
        passengers.getPassenger(request.passengerId());
        return validated;
    }

    private static String searchTerm(String query) {
        if (query == null || query.isBlank()) {
            throw new BusinessException("Введите текст для поиска.");
        }
        return query.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String searchTerm) {
        return value.toLowerCase(Locale.ROOT).contains(searchTerm);
    }

    private static void checkVersion(RideOrder current, long expectedVersion) {
        requireUpdated(current.version() == expectedVersion);
    }

    private static void requireUpdated(boolean updated) {
        if (!updated) {
            throw new BusinessException("Заказ уже изменился или был удалён. Откройте его заново.");
        }
    }
}
