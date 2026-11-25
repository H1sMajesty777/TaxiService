package ru.mirea.taxi.exception;

import java.sql.SQLException;

public final class DatabaseException extends RuntimeException {
    public DatabaseException(String message, SQLException cause) {
        super(message + ". Код PostgreSQL: " + cause.getSQLState(), cause);
    }
}
