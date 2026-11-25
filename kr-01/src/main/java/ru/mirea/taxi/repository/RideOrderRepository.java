package ru.mirea.taxi.repository;

import ru.mirea.taxi.model.RideOrder;
import ru.mirea.taxi.model.RideRequest;
import ru.mirea.taxi.model.RideStatus;

import java.util.List;
import java.util.Optional;

public interface RideOrderRepository {
    List<RideOrder> findAll();
    Optional<RideOrder> findById(long id);
    long insert(RideRequest request);
    boolean updateDetails(long id, long expectedVersion, RideRequest request);
    boolean updateStatus(long id, long expectedVersion, RideStatus status);
    boolean delete(long id, long expectedVersion);
}
