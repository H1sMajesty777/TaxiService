package ru.mirea.taxi.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.taxi.backend.domain.AuditEvent;
import ru.mirea.taxi.backend.dto.PageResponse;
import ru.mirea.taxi.backend.filter.CorrelationFilter;
import ru.mirea.taxi.backend.filter.QueryFilters;
import ru.mirea.taxi.backend.repository.AuditRepository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditRepository audit;

    public AuditService(AuditRepository audit) {
        this.audit = audit;
    }

    public void record(UUID actor, String event, String type, UUID target, String description) {
        audit.save(new AuditEvent(actor, event, type, target, description, CorrelationFilter.current()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejectedLogin() {
        record(null, "LOGIN_REJECTED", "ACCOUNT", null, "Отказ во входе");
    }

    @Transactional(readOnly = true)
    public PageResponse<EventView> search(String query, int page, int size, String sort, String direction) {
        var paging = QueryFilters.page(page, size, sort, direction,
                Map.of("time", "occurredAt", "event", "eventType", "entity", "entityType"));
        return PageResponse.from(audit.findAll((root, criteria, builder) -> {
            if (query == null || query.isBlank()) {
                return builder.conjunction();
            }
            String pattern = QueryFilters.literalLike(query);
            return builder.or(
                    builder.like(builder.lower(root.get("eventType")), pattern, '\\'),
                    builder.like(builder.lower(root.get("entityType")), pattern, '\\'),
                    builder.like(builder.lower(root.get("description")), pattern, '\\'),
                    builder.like(builder.lower(root.get("correlationId")), pattern, '\\'));
        }, paging).map(EventView::from));
    }

    public record EventView(UUID id, UUID actorId, String eventType, String entityType, UUID entityId,
                            Instant occurredAt, String description, String correlationId) {
        static EventView from(AuditEvent event) {
            return new EventView(event.getId(), event.getActorId(), event.getEventType(), event.getEntityType(),
                    event.getEntityId(), event.getOccurredAt(), event.getDescription(), event.getCorrelationId());
        }
    }
}
