package ru.mirea.taxi.ui;

import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.exception.DatabaseException;
import ru.mirea.taxi.model.Passenger;
import ru.mirea.taxi.model.RideOrder;
import ru.mirea.taxi.model.RideRequest;
import ru.mirea.taxi.model.RideStatus;
import ru.mirea.taxi.service.PassengerService;
import ru.mirea.taxi.service.ReportService;
import ru.mirea.taxi.service.RideOrderService;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Scanner;

public final class TaxiConsole {
    private static final String MAIN_MENU = """

            === Служба такси ===
            1. Пассажиры
            2. Заказы поездок
            3. Поиск заказов
            4. Фильтрация и сортировка
            5. Статистика
            6. Экспорт в Excel
            7. Просмотр таблиц базы данных
            0. Выход
            """;
    private static final String PASSENGER_MENU = """

            Пассажиры
            1. Список
            2. Найти по ID
            3. Добавить
            4. Изменить
            5. Удалить
            0. Назад
            """;
    private static final String RIDE_MENU = """

            Заказы поездок
            1. Список
            2. Найти по ID
            3. Создать заказ
            4. Изменить заказ
            5. Изменить статус
            6. Удалить заказ
            0. Назад
            """;
    private static final String SEARCH_MENU = """

            Поиск заказов
            1. По адресу подачи или назначения
            2. По имени пассажира
            0. Назад
            """;
    private static final String FILTER_MENU = """

            Фильтрация и сортировка
            1. Фильтр по статусу
            2. Фильтр по диапазону дат подачи
            3. Сортировка по времени подачи (сначала поздние)
            4. Сортировка по стоимости (сначала дешёвые)
            0. Назад
            """;

    private final ConsoleInput input;
    private final ConsoleView view;
    private final PrintStream output;
    private final PassengerService passengers;
    private final RideOrderService rides;
    private final ReportService reports;

    public TaxiConsole(Scanner input, PrintStream output, PassengerService passengers,
                       RideOrderService rides, ReportService reports) {
        this.input = new ConsoleInput(input, output);
        this.view = new ConsoleView(output);
        this.output = output;
        this.passengers = passengers;
        this.rides = rides;
        this.reports = reports;
    }

    public void run() {
        try {
            menu(MAIN_MENU, this::handleMainMenu);
        } catch (NoSuchElementException endOfInput) {
            // Конец ввода может прийти посередине формы, например при чтении из файла.
        }
        output.println("Работа завершена.");
    }

    private void handleMainMenu(int choice) throws IOException {
        switch (choice) {
            case 1 -> menu(PASSENGER_MENU, this::handlePassengerMenu);
            case 2 -> menu(RIDE_MENU, this::handleRideMenu);
            case 3 -> menu(SEARCH_MENU, this::handleSearchMenu);
            case 4 -> menu(FILTER_MENU, this::handleFilterMenu);
            case 5 -> view.showStatistics(reports.getStatistics());
            case 6 -> exportRides();
            case 7 -> showTables();
            default -> throw unknownOption();
        }
    }

    private void handlePassengerMenu(int choice) {
        switch (choice) {
            case 1 -> view.showPassengers(passengers.listPassengers());
            case 2 -> view.showPassenger(passengers.getPassenger(input.readPassengerId()));
            case 3 -> addPassenger();
            case 4 -> editPassenger();
            case 5 -> deletePassenger();
            default -> throw unknownOption();
        }
    }

    private void handleRideMenu(int choice) {
        switch (choice) {
            case 1 -> view.showRides(rides.listRides());
            case 2 -> view.showRide(rides.getRide(input.readRideId()));
            case 3 -> createRide();
            case 4 -> editRide();
            case 5 -> changeRideStatus();
            case 6 -> deleteRide();
            default -> throw unknownOption();
        }
    }

    private void handleSearchMenu(int choice) {
        switch (choice) {
            case 1 -> view.showRides(rides.searchByAddress(input.readLine("Часть адреса: ")));
            case 2 -> view.showRides(rides.searchByPassengerName(input.readLine("Часть имени: ")));
            default -> throw unknownOption();
        }
    }

    private void handleFilterMenu(int choice) {
        switch (choice) {
            case 1 -> view.showRides(rides.filterByStatus(readStatus()));
            case 2 -> view.showRides(rides.filterByPickupDates(input.readDate("С даты"), input.readDate("По дату")));
            case 3 -> view.showRides(rides.sortByPickupTime());
            case 4 -> view.showRides(rides.sortByFare());
            default -> throw unknownOption();
        }
    }

    private void addPassenger() {
        String name = input.readLine("Имя пассажира: ");
        String phone = input.readLine("Телефон (+7 и 10 цифр): ");
        output.println("Добавлен пассажир #" + passengers.addPassenger(name, phone));
    }

    private void editPassenger() {
        Passenger passenger = passengers.getPassenger(input.readPassengerId());
        output.println("Нажмите Enter, чтобы оставить текущее значение.");
        String name = input.readWithDefault("Имя пассажира", passenger.fullName());
        String phone = input.readWithDefault("Телефон", passenger.phone());
        passengers.editPassenger(passenger.id(), name, phone);
        output.println("Данные пассажира сохранены.");
    }

    private void deletePassenger() {
        Passenger passenger = passengers.getPassenger(input.readPassengerId());
        view.showPassenger(passenger);
        if (input.confirmDelete()) {
            passengers.deletePassenger(passenger.id());
            output.println("Пассажир удалён.");
        }
    }

    private void createRide() {
        view.showPassengers(passengers.listPassengers());
        long id = rides.createRide(readRideDetails(null));
        output.println("Создан заказ #" + id + " со статусом «Новый».");
    }

    private void editRide() {
        RideOrder current = rides.getRide(input.readRideId());
        view.showRide(current);
        output.println("Нажмите Enter, чтобы оставить текущее значение.");
        view.showPassengers(passengers.listPassengers());
        rides.editRide(current.id(), current.version(), readRideDetails(current));
        output.println("Данные заказа сохранены.");
    }

    private void changeRideStatus() {
        RideOrder current = rides.getRide(input.readRideId());
        view.showRide(current);
        RideStatus nextStatus = readStatus();
        rides.changeStatus(current.id(), current.version(), nextStatus);
        output.println("Установлен статус «" + nextStatus.label() + "».");
    }

    private void deleteRide() {
        RideOrder current = rides.getRide(input.readRideId());
        view.showRide(current);
        if (input.confirmDelete()) {
            rides.deleteRide(current.id(), current.version());
            output.println("Заказ удалён.");
        }
    }

    private RideRequest readRideDetails(RideOrder current) {
        return new RideRequest(
                input.readPassengerId(current == null ? null : current.passenger().id()),
                input.readWithDefault("Адрес подачи", current == null ? null : current.pickupAddress()),
                input.readWithDefault("Адрес назначения", current == null ? null : current.dropoffAddress()),
                input.readPickupTime(current == null ? null : current.pickupAt()),
                input.readFare(current == null ? null : current.fare())
        );
    }

    private RideStatus readStatus() {
        RideStatus[] statuses = RideStatus.values();
        for (int index = 0; index < statuses.length; index++) {
            output.printf("%d. %s%n", index + 1, statuses[index].label());
        }
        int choice = input.readChoice("Выберите статус: ");
        if (choice < 1 || choice > statuses.length) {
            throw new BusinessException("В списке нет статуса с таким номером.");
        }
        return statuses[choice - 1];
    }

    private void exportRides() throws IOException {
        String file = input.readWithDefault("Путь для Excel", "exports/ride-orders.xlsx");
        output.println("Экспорт сохранён: " + reports.exportRides(Path.of(file)));
    }

    private void showTables() {
        output.println("Пассажиры (passengers)");
        view.showPassengers(passengers.listPassengers());
        output.println("Заказы (ride_orders)");
        view.showRides(rides.listRides());
    }

    private void menu(String title, MenuAction action) {
        while (true) {
            output.print(title);
            try {
                int choice = input.readChoice("Выберите действие: ");
                if (choice == 0) {
                    return;
                }
                action.accept(choice);
            } catch (BusinessException | DatabaseException error) {
                output.println("Ошибка: " + error.getMessage());
            } catch (IOException | InvalidPathException error) {
                output.println("Ошибка экспорта: " + error.getMessage());
            }
        }
    }

    private static BusinessException unknownOption() {
        return new BusinessException("Такого пункта меню нет.");
    }

    @FunctionalInterface
    private interface MenuAction {
        void accept(int choice) throws IOException;
    }
}
