package ru.mirea.taxi.repository;

import ru.mirea.taxi.exception.DatabaseException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class Database {
    private final String url;
    private final Properties connectionProperties;

    public Database(String url, String user, String password) {
        this.url = url;
        this.connectionProperties = new Properties();
        connectionProperties.setProperty("user", user);
        connectionProperties.setProperty("password", password);
        connectionProperties.setProperty("connectTimeout", "5");
    }

    public static Database fromEnvironment() {
        return new Database(
                System.getenv().getOrDefault("TAXI_DB_URL", "jdbc:postgresql://127.0.0.1:5434/taxi"),
                System.getenv().getOrDefault("TAXI_DB_USER", "taxi"),
                System.getenv().getOrDefault("TAXI_DB_PASSWORD", "taxi_local")
        );
    }

    Connection open() {
        try {
            return DriverManager.getConnection(url, connectionProperties);
        } catch (SQLException error) {
            throw new DatabaseException(
                    "Не удалось подключиться к PostgreSQL. Проверьте запуск базы и параметры TAXI_DB_*",
                    error
            );
        }
    }
}
