package ru.mirea.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.mirea.taxi.backend.domain.PassengerRow;
import ru.mirea.taxi.backend.domain.Tariff;
import java.util.UUID;

public final class DirectoryData {
    private DirectoryData() {}

    public record TariffInput(@NotBlank @Size(max = 80) String name,
                              @NotBlank @Size(max = 300) String description, boolean active) {}

    public record TariffView(UUID id, String name, String description, boolean active) {
        public static TariffView from(Tariff tariff) {
            return new TariffView(tariff.getId(), tariff.getName(), tariff.getDescription(), tariff.isActive());
        }
    }

    public record PassengerView(long id, String fullName) {
        public static PassengerView from(PassengerRow passenger) {
            return new PassengerView(passenger.getId(), passenger.getFullName());
        }
    }
}
