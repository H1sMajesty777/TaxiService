package ru.mirea.taxi.backend.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.taxi.backend.domain.Ride;
import ru.mirea.taxi.backend.dto.PageResponse;
import ru.mirea.taxi.backend.dto.RideData;
import ru.mirea.taxi.backend.error.ApiException;
import ru.mirea.taxi.backend.filter.QueryFilters;
import ru.mirea.taxi.backend.filter.RideFilter;
import ru.mirea.taxi.backend.repository.PassengerDirectory;
import ru.mirea.taxi.backend.repository.RideRepository;
import ru.mirea.taxi.backend.repository.TariffRepository;
import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.model.RideRequest;
import ru.mirea.taxi.model.RideStatus;
import ru.mirea.taxi.service.RideRequestRules;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RideService {
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "pickupAt", "pickupAt", "fare", "fare", "code", "externalCode", "updatedAt", "updatedAt");
    private final RideRepository rides;
    private final PassengerDirectory passengers;
    private final TariffRepository tariffs;
    private final AuditService audit;

    public RideService(RideRepository rides, PassengerDirectory passengers, TariffRepository tariffs,
                       AuditService audit) {
        this.rides = rides;
        this.passengers = passengers;
        this.tariffs = tariffs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<RideData.View> list(RideFilter filter, int page, int size, String sort, String direction) {
        var pageable = QueryFilters.page(page, size, sort, direction, SORT_FIELDS);
        return PageResponse.from(rides.findAll(filter.specification(), pageable).map(RideData.View::from));
    }

    @Transactional(readOnly = true)
    public List<RideData.View> export(RideFilter filter, String sort, String direction) {
        var sortOrder = QueryFilters.page(0, 100, sort, direction, SORT_FIELDS).getSort();
        return rides.findAll(filter.specification(), sortOrder).stream().map(RideData.View::from).toList();
    }

    @Transactional(readOnly = true)
    public RideData.View get(UUID id) {
        return RideData.View.from(find(id));
    }

    @Transactional
    public RideData.View create(RideData.Input input, UUID actor) {
        if (input.status() != RideStatus.NEW) {
            throw new BusinessException("Новый заказ начинается со статуса NEW.");
        }
        Ride ride = newRide(input);
        audit.record(actor, "RIDE_CREATED", "RIDE", ride.getPublicId(), ride.getExternalCode());
        return RideData.View.from(ride);
    }

    @Transactional
    public RideData.View update(UUID id, RideData.Input input, UUID actor) {
        Ride ride = find(id);
        checkVersion(ride, input.version());
        requireActive(ride);
        applyDetails(ride, input);
        rides.flush();
        audit.record(actor, "RIDE_UPDATED", "RIDE", id, ride.getExternalCode());
        return RideData.View.from(ride);
    }

    @Transactional
    public RideData.View archive(UUID id, long version, boolean archive, UUID actor) {
        Ride ride = find(id);
        checkVersion(ride, version);
        setArchived(ride, archive);
        rides.flush();
        audit.record(actor, archive ? "RIDE_ARCHIVED" : "RIDE_RESTORED", "RIDE", id, ride.getExternalCode());
        return RideData.View.from(ride);
    }

    @Transactional
    public void permanentlyDelete(UUID id, long version, UUID actor) {
        Ride ride = find(id);
        checkVersion(ride, version);
        if (!ride.isArchived()) {
            throw new BusinessException("Сначала поместите заказ в архив.");
        }
        rides.delete(ride);
        rides.flush();
        audit.record(actor, "RIDE_DELETED", "RIDE", id, ride.getExternalCode());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyBulk(UUID id, RideData.BulkInput input, UUID actor) {
        Ride ride = find(id);
        switch (input.action()) {
            case ARCHIVE -> setArchived(ride, true);
            case RESTORE -> setArchived(ride, false);
            case STATUS -> {
                requireActive(ride);
                checkStatus(ride, input.status());
                ride.setStatus(input.status());
            }
            case ADD_TAG, REMOVE_TAG -> {
                requireActive(ride);
                String tag = normalizeTag(input.tag());
                boolean add = input.action() == RideData.BulkAction.ADD_TAG;
                if (add && !ride.getTags().contains(tag) && ride.getTags().size() >= 16) {
                    throw new BusinessException("Допустимо не больше 16 тегов.");
                }
                ride.changeTag(tag, add);
            }
        }
        rides.flush();
        audit.record(actor, "BULK_" + input.action(), "RIDE", id, ride.getExternalCode());
    }

    Ride find(UUID id) {
        return rides.findByPublicId(id).orElseThrow(() -> ApiException.notFound("Заказ не найден."));
    }

    Ride newRide(RideData.Input input) {
        String code = input.externalCode() == null
                ? "TX-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT)
                : input.externalCode().toUpperCase(Locale.ROOT);
        Ride ride = new Ride(code, input.status());
        applyDetails(ride, input);
        return rides.saveAndFlush(ride);
    }

    void applyDetails(Ride ride, RideData.Input input) {
        checkStatus(ride, input.status());
        RideRequest request = validateRequest(input);
        var passenger = passengers.findById(request.passengerId())
                .orElseThrow(() -> new BusinessException("Пассажир не найден."));
        var tariff = tariffs.findById(input.tariffId())
                .orElseThrow(() -> new BusinessException("Тариф не найден."));
        if (!tariff.isActive() && (ride.getTariff() == null || !tariff.getId().equals(ride.getTariff().getId()))) {
            throw new BusinessException("Для нового заказа или смены тарифа выберите действующий тариф.");
        }
        ride.updateDetails(request, passenger, tariff, input.childSeat(), normalizeTags(input.tags()), input.status());
    }

    static RideRequest validateRequest(RideData.Input input) {
        return RideRequestRules.validate(new RideRequest(input.passengerId(), input.pickupAddress(),
                input.dropoffAddress(), input.pickupAt(), input.fare()));
    }

    static Set<String> normalizeTags(Set<String> tags) {
        Set<String> normalized = new LinkedHashSet<>();
        if (tags != null) {
            tags.forEach(tag -> normalized.add(normalizeTag(tag)));
        }
        if (normalized.size() > 16) {
            throw new BusinessException("Допустимо не больше 16 тегов.");
        }
        return normalized;
    }

    static void checkStatus(Ride ride, RideStatus target) {
        if (target == null || (ride.getStatus() != target && !ride.getStatus().canChangeTo(target))) {
            throw new BusinessException("Выбранный переход между статусами запрещён.");
        }
    }

    static void requireActive(Ride ride) {
        if (ride.isArchived()) {
            throw new BusinessException("Сначала восстановите заказ из архива.");
        }
    }

    private static String normalizeTag(String tag) {
        if (tag == null || tag.isBlank() || tag.length() > 40 || tag.indexOf('|') >= 0
                || tag.indexOf(',') >= 0 || tag.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException("Тег должен содержать от 1 до 40 символов, без запятой и вертикальной черты.");
        }
        return tag.strip().toLowerCase(Locale.ROOT);
    }

    private static void setArchived(Ride ride, boolean archive) {
        if (ride.isArchived() == archive) {
            throw new BusinessException(archive ? "Заказ уже в архиве." : "Заказ уже активен.");
        }
        ride.setArchived(archive);
    }

    private static void checkVersion(Ride ride, Long version) {
        if (version == null) {
            throw new BusinessException("Передайте версию заказа.");
        }
        if (ride.getVersion() != version) {
            throw ApiException.conflict("Данные изменились. Обновите карточку заказа.");
        }
    }
}
