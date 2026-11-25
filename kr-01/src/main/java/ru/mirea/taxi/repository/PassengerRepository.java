package ru.mirea.taxi.repository;

import ru.mirea.taxi.model.Passenger;

import java.util.List;
import java.util.Optional;

public interface PassengerRepository {
    List<Passenger> findAll();
    Optional<Passenger> findById(long id);
    long insert(String fullName, String phone);
    boolean update(long id, String fullName, String phone);
    boolean delete(long id);
}
