package ru.mirea.taxi.repository;

import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.exception.DatabaseException;
import ru.mirea.taxi.model.Passenger;
import ru.mirea.taxi.model.RideOrder;
import ru.mirea.taxi.model.RideRequest;
import ru.mirea.taxi.model.RideStatus;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcRideOrderRepository implements RideOrderRepository {
    private static final String SELECT_RIDES = """
            SELECT r.id, r.passenger_id, r.pickup_address, r.dropoff_address, r.pickup_at,
                   r.fare, r.status, r.version, p.full_name, p.phone
            FROM ride_orders r
            JOIN passengers p ON p.id = r.passenger_id
            """;

    private final Database database;

    public JdbcRideOrderRepository(Database database) {
        this.database = database;
    }

    @Override
    public List<RideOrder> findAll() {
        try (var connection = database.open();
             var statement = connection.prepareStatement(SELECT_RIDES + "ORDER BY r.id");
             var rows = statement.executeQuery()) {
            List<RideOrder> rideOrders = new ArrayList<>();
            while (rows.next()) {
                rideOrders.add(readRide(rows));
            }
            return rideOrders;
        } catch (SQLException error) {
            throw new DatabaseException("Не удалось загрузить заказы", error);
        }
    }

    @Override
    public Optional<RideOrder> findById(long id) {
        try (var connection = database.open();
             var statement = connection.prepareStatement(SELECT_RIDES + "WHERE r.id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readRide(rows)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw new DatabaseException("Не удалось найти заказ", error);
        }
    }

    @Override
    public long insert(RideRequest request) {
        String sql = """
                INSERT INTO ride_orders (passenger_id, pickup_address, dropoff_address, pickup_at, fare)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """;
        try (var connection = database.open();
             var statement = connection.prepareStatement(sql)) {
            setDetails(statement, request);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong("id");
            }
        } catch (SQLException error) {
            throw detailsError("Не удалось создать заказ", error);
        }
    }

    @Override
    public boolean updateDetails(long id, long expectedVersion, RideRequest request) {
        String sql = """
                UPDATE ride_orders
                SET passenger_id = ?, pickup_address = ?, dropoff_address = ?, pickup_at = ?,
                    fare = ?, version = version + 1
                WHERE id = ? AND version = ?
                """;
        try (var connection = database.open();
             var statement = connection.prepareStatement(sql)) {
            setDetails(statement, request);
            statement.setLong(6, id);
            statement.setLong(7, expectedVersion);
            return statement.executeUpdate() == 1;
        } catch (SQLException error) {
            throw detailsError("Не удалось изменить заказ", error);
        }
    }

    @Override
    public boolean updateStatus(long id, long expectedVersion, RideStatus status) {
        String sql = """
                UPDATE ride_orders SET status = ?, version = version + 1
                WHERE id = ? AND version = ?
                """;
        try (var connection = database.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setLong(2, id);
            statement.setLong(3, expectedVersion);
            return statement.executeUpdate() == 1;
        } catch (SQLException error) {
            throw new DatabaseException("Не удалось изменить статус заказа", error);
        }
    }

    @Override
    public boolean delete(long id, long expectedVersion) {
        String sql = "DELETE FROM ride_orders WHERE id = ? AND version = ?";
        try (var connection = database.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, expectedVersion);
            return statement.executeUpdate() == 1;
        } catch (SQLException error) {
            throw new DatabaseException("Не удалось удалить заказ", error);
        }
    }

    private static void setDetails(PreparedStatement statement, RideRequest request) throws SQLException {
        statement.setLong(1, request.passengerId());
        statement.setString(2, request.pickupAddress());
        statement.setString(3, request.dropoffAddress());
        statement.setObject(4, request.pickupAt());
        statement.setBigDecimal(5, request.fare());
    }

    private static RuntimeException detailsError(String message, SQLException error) {
        if ("23503".equals(error.getSQLState())) {
            return new BusinessException("Пассажир для заказа больше не существует.");
        }
        return new DatabaseException(message, error);
    }

    private static RideOrder readRide(ResultSet row) throws SQLException {
        Passenger passenger = new Passenger(
                row.getLong("passenger_id"), row.getString("full_name"), row.getString("phone")
        );
        return new RideOrder(
                row.getLong("id"), passenger, row.getString("pickup_address"),
                row.getString("dropoff_address"), row.getObject("pickup_at", LocalDateTime.class),
                row.getBigDecimal("fare"), RideStatus.valueOf(row.getString("status")), row.getLong("version")
        );
    }
}
