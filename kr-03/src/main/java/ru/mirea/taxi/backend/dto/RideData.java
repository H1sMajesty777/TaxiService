package ru.mirea.taxi.backend.dto;

import jakarta.validation.constraints.*;
import ru.mirea.taxi.backend.domain.Priority;
import ru.mirea.taxi.backend.domain.Ride;
import ru.mirea.taxi.model.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RideData {
    private RideData() {}

    public record Input(
            @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String externalCode,
            @NotNull @Positive Long passengerId,
            @NotNull UUID tariffId,
            @NotBlank @Size(min = 3, max = 150) String pickupAddress,
            @NotBlank @Size(min = 3, max = 150) String dropoffAddress,
            @NotNull LocalDateTime pickupAt,
            @NotNull @DecimalMin("0.01") @Digits(integer = 7, fraction = 2) BigDecimal fare,
            @NotNull RideStatus status,
            boolean childSeat,
            @Size(max = 16) Set<@NotBlank @Size(max = 40) String> tags,
            @PositiveOrZero Long version
    ) {}

    public record View(UUID id, long number, String externalCode, long passengerId, String passengerName,
                       UUID tariffId, String tariffName, String pickupAddress, String dropoffAddress,
                       LocalDateTime pickupAt, BigDecimal fare, RideStatus status, boolean childSeat,
                       Priority priority, List<String> tags, boolean archived, Instant createdAt, Instant updatedAt,
                       long version) {
        public static View from(Ride ride) {
            return new View(ride.getPublicId(), ride.getId(), ride.getExternalCode(), ride.getPassenger().getId(),
                    ride.getPassenger().getFullName(), ride.getTariff().getId(), ride.getTariff().getName(),
                    ride.getPickupAddress(), ride.getDropoffAddress(), ride.getPickupAt(), ride.getFare(),
                    ride.getStatus(), ride.isChildSeat(), ride.getPriority(), ride.getTags().stream().sorted().toList(),
                    ride.isArchived(), ride.getCreatedAt(), ride.getUpdatedAt(), ride.getVersion());
        }
    }

    public enum BulkAction { ARCHIVE, RESTORE, STATUS, ADD_TAG, REMOVE_TAG }
    public record BulkInput(@NotEmpty @Size(max = 100) Set<@NotNull UUID> ids, @NotNull BulkAction action,
                            RideStatus status, @Size(max = 40) String tag) {}
    public record BulkFailure(UUID id, String error) {}
    public record BulkResult(List<UUID> succeeded, List<BulkFailure> failures) {}
}
