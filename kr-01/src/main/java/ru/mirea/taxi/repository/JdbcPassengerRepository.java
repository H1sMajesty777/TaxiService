package ru.mirea.taxi.repository;

import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.exception.DatabaseException;
import ru.mirea.taxi.model.Passenger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcPassengerRepository implements PassengerRepository {
    private final Database database;

    public JdbcPassengerRepository(Database database) {
        this.database = database;
    }

    @Override
    public List<Passenger> findAll() {
        String sql = "SELECT id, full_name, phone FROM passengers ORDER BY id";
        try (var connection = database.open();
             var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            List<Passenger> passengers = new ArrayList<>();
            while (rows.next()) {
                passengers.add(readPassenger(rows));
            }
            return passengers;
        } catch (SQLException error) {
            throw new DatabaseException("Не удалось загрузить список пассажиров", error);
        }
    }

    @Override
    public Optional<Passenger> findById(long id) {
        String sql = "SELECT id, full_name, phone FROM passengers WHERE id = ?";
        try (var connection = database.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readPassenger(rows)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw new DatabaseException("Не удалось найти пассажира", error);
        }
    }

    @Override
    public long insert(String fullName, String phone) {
        String sql = "INSERT INTO passengers (full_name, phone) VALUES (?, ?) RETURNING id";
        try (var connection = database.open();
             var statement = connection.prepareStatement(sql)) {
            setContact(statement, fullName, phone);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong("id");
            }
        } catch (SQLException error) {
            throw contactError("Не удалось добавить пассажира", error);
        }
    }

    @Override
    public boolean update(long id, String fullName, String phone) {
        String sql = "UPDATE passengers SET full_name = ?, phone = ? WHERE id = ?";
        try (var connection = database.open();
             var statement = connection.prepareStatement(sql)) {
            setContact(statement, fullName, phone);
            statement.setLong(3, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException error) {
            throw contactError("Не удалось обновить пассажира", error);
        }
    }

    @Override
    public boolean delete(long id) {
        String sql = "DELETE FROM passengers WHERE id = ?";
        try (var connection = database.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException error) {
            if ("23503".equals(error.getSQLState())) {
                throw new BusinessException("У пассажира есть заказы. Удаление запрещено.");
            }
            throw new DatabaseException("Не удалось удалить пассажира", error);
        }
    }

    private static void setContact(PreparedStatement statement, String fullName, String phone)
            throws SQLException {
        statement.setString(1, fullName);
        statement.setString(2, phone);
    }

    private static RuntimeException contactError(String message, SQLException error) {
        if ("23505".equals(error.getSQLState())) {
            return new BusinessException("Пассажир с таким номером телефона уже существует.");
        }
        return new DatabaseException(message, error);
    }

    private static Passenger readPassenger(ResultSet row) throws SQLException {
        return new Passenger(row.getLong("id"), row.getString("full_name"), row.getString("phone"));
    }
}
