package ru.mirea.taxi.service;

import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.exception.EntityNotFoundException;
import ru.mirea.taxi.model.Passenger;
import ru.mirea.taxi.repository.PassengerRepository;

import java.util.List;
import java.util.regex.Pattern;

public final class PassengerService {
    private static final Pattern PHONE = Pattern.compile("\\+7[0-9]{10}");
    private final PassengerRepository passengers;

    public PassengerService(PassengerRepository passengers) {
        this.passengers = passengers;
    }

    public List<Passenger> listPassengers() {
        return passengers.findAll();
    }

    public Passenger getPassenger(long id) {
        return passengers.findById(id).orElseThrow(() -> passengerNotFound(id));
    }

    public long addPassenger(String fullName, String phone) {
        return passengers.insert(validateName(fullName), validatePhone(phone));
    }

    public void editPassenger(long id, String fullName, String phone) {
        if (!passengers.update(id, validateName(fullName), validatePhone(phone))) {
            throw passengerNotFound(id);
        }
    }

    public void deletePassenger(long id) {
        if (!passengers.delete(id)) {
            throw passengerNotFound(id);
        }
    }

    private static String validateName(String fullName) {
        String name = fullName == null ? "" : fullName.strip();
        if (name.length() < 2 || name.length() > 100 || name.contains("\u0000")) {
            throw new BusinessException("Имя пассажира должно содержать от 2 до 100 символов.");
        }
        return name;
    }

    private static String validatePhone(String phone) {
        String number = phone == null ? "" : phone.strip();
        if (!PHONE.matcher(number).matches()) {
            throw new BusinessException("Телефон должен начинаться с +7 и содержать ещё 10 цифр.");
        }
        return number;
    }

    private static EntityNotFoundException passengerNotFound(long id) {
        return new EntityNotFoundException("Пассажир с ID " + id + " не найден.");
    }
}
