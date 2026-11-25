package ru.mirea.taxi.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID actorId;
    @Column(nullable = false, length = 40)
    private String eventType;
    @Column(nullable = false, length = 25)
    private String entityType;
    private UUID entityId;
    private Instant occurredAt;
    @Column(nullable = false, length = 300)
    private String description;
    @Column(nullable = false, length = 64)
    private String correlationId;

    protected AuditEvent() {}

    public AuditEvent(UUID actorId, String eventType, String entityType, UUID entityId,
                      String description, String correlationId) {
        this.actorId = actorId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.occurredAt = Instant.now();
        this.description = description;
        this.correlationId = correlationId;
    }

    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getEventType() { return eventType; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getDescription() { return description; }
    public String getCorrelationId() { return correlationId; }
}
