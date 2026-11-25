package ru.mirea.taxi.mobile.net;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Models {
    private Models() {}

    public record Login(String email, String password, String clientType) {}
    public record Refresh(String clientType, String refreshToken) {}
    public record PasswordChange(String currentPassword, String newPassword, String clientType) {}
    public record Session(String accessToken, String refreshToken, Profile user) {}
    public record Profile(String id, String email, String name, String role,
                          boolean active, boolean mustChangePassword) {
        public boolean admin() { return "ADMIN".equals(role); }
        public boolean canEdit() { return admin() || "DISPATCHER".equals(role); }
    }
    public record ProfileInput(String email, String name, String role, boolean active) {}
    public record AccountInput(ProfileInput profile, String temporaryPassword) {}
    public record TemporaryPassword(String temporaryPassword) {}
    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
    public record Ride(String id, long number, String externalCode, long passengerId, String passengerName,
                       String tariffId, String tariffName, String pickupAddress, String dropoffAddress,
                       String pickupAt, BigDecimal fare, String status, boolean childSeat, String priority,
                       List<String> tags, boolean archived, String createdAt, String updatedAt, long version) {}
    public record RideInput(String externalCode, long passengerId, String tariffId, String pickupAddress,
                            String dropoffAddress, String pickupAt, BigDecimal fare, String status,
                            boolean childSeat, Set<String> tags, Long version) {}
    public record Tariff(String id, String name, String description, boolean active) {}
    public record TariffInput(String name, String description, boolean active) {}
    public record Passenger(long id, String fullName) {}
    public record Note(String id, String authorId, String author, String body, boolean pinned,
                       String createdAt, String updatedAt) {}
    public record NoteInput(String body, boolean pinned) {}
    public record Summary(long total, long active, long archived, long childSeat, BigDecimal averageFare,
                          Map<String, Long> byTariff, Map<String, Long> byStatus, Map<String, Long> byPriority) {}
    public record BulkInput(Set<String> ids, String action, String status, String tag) {}
    public record BulkFailure(String id, String error) {}
    public record BulkResult(List<String> succeeded, List<BulkFailure> failures) {}
    public record CsvError(long row, String error) {}
    public record CsvPreview(int valid, int invalid, List<CsvError> errors) {}
    public record CsvApplied(int inserted, int updated) {}
    public record Audit(String id, String actorId, String eventType, String entityType, String entityId,
                        String occurredAt, String description, String correlationId) {}
}
