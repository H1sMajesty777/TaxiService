package ru.mirea.taxi.model;

public enum RideStatus {
    NEW("Новый"),
    CONFIRMED("Подтверждён"),
    IN_PROGRESS("В поездке"),
    COMPLETED("Завершён"),
    CANCELLED("Отменён");

    private final String label;

    RideStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canChangeTo(RideStatus nextStatus) {
        return switch (this) {
            case NEW -> nextStatus == CONFIRMED || nextStatus == CANCELLED;
            case CONFIRMED -> nextStatus == IN_PROGRESS || nextStatus == CANCELLED;
            case IN_PROGRESS -> nextStatus == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
