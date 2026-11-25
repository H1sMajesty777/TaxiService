package ru.mirea.taxi.backend.filter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.taxi.backend.domain.Priority;
import ru.mirea.taxi.backend.domain.Ride;
import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.model.RideStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public record RideFilter(String q, UUID tariffId, RideStatus status, Boolean childSeat,
                         Priority priority, String tag, Archive archive, Compare fareOp, BigDecimal fare) {
    public enum Archive { ACTIVE, ARCHIVED, ALL }
    public enum Compare { EQ, GE, LE }

    public Specification<Ride> specification() {
        if ((fare == null) != (fareOp == null) || (fare != null && fare.signum() < 0)) {
            throw new BusinessException("Укажите неотрицательную стоимость и способ сравнения.");
        }
        if ((q != null && q.length() > 160) || (tag != null && tag.length() > 40)) {
            throw new BusinessException("Поисковый запрос или тег слишком длинный.");
        }
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            Archive scope = archive == null ? Archive.ACTIVE : archive;
            if (scope != Archive.ALL) {
                predicates.add(builder.equal(root.get("archived"), scope == Archive.ARCHIVED));
            }
            if (q != null && !q.isBlank()) {
                String pattern = QueryFilters.literalLike(q);
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("externalCode")), pattern, '\\'),
                        builder.like(builder.lower(root.get("pickupAddress")), pattern, '\\'),
                        builder.like(builder.lower(root.get("dropoffAddress")), pattern, '\\'),
                        builder.like(builder.lower(root.join("passenger").get("fullName")), pattern, '\\')));
            }
            if (tariffId != null) {
                predicates.add(builder.equal(root.get("tariff").get("id"), tariffId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (childSeat != null) {
                predicates.add(builder.equal(root.get("childSeat"), childSeat));
            }
            if (priority != null) {
                var amount = root.<BigDecimal>get("fare");
                var high = builder.or(builder.isTrue(root.get("childSeat")),
                        builder.greaterThanOrEqualTo(amount, Priority.HIGH_FARE));
                var normal = builder.and(builder.not(high),
                        builder.greaterThanOrEqualTo(amount, Priority.NORMAL_FARE));
                predicates.add(switch (priority) {
                    case HIGH -> high;
                    case NORMAL -> normal;
                    case LOW -> builder.and(builder.not(high), builder.lessThan(amount, Priority.NORMAL_FARE));
                });
            }
            if (tag != null && !tag.isBlank()) {
                predicates.add(builder.isMember(tag.strip().toLowerCase(Locale.ROOT), root.<Set<String>>get("tags")));
            }
            if (fare != null) {
                var amount = root.<BigDecimal>get("fare");
                predicates.add(switch (fareOp) {
                    case EQ -> builder.equal(amount, fare);
                    case GE -> builder.greaterThanOrEqualTo(amount, fare);
                    case LE -> builder.lessThanOrEqualTo(amount, fare);
                });
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
