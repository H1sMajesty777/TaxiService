package ru.mirea.taxi.backend.domain;

import jakarta.persistence.*;
import ru.mirea.taxi.model.RideRequest;
import ru.mirea.taxi.model.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "ride_orders")
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId;
    @Column(nullable = false, unique = true, length = 40, updatable = false)
    private String externalCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id")
    private PassengerRow passenger;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tariff_id")
    private Tariff tariff;
    @Column(length = 150, nullable = false)
    private String pickupAddress;
    @Column(length = 150, nullable = false)
    private String dropoffAddress;
    @Column(nullable = false)
    private LocalDateTime pickupAt;
    @Column(nullable = false, precision = 9, scale = 2)
    private BigDecimal fare;
    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private RideStatus status;
    private boolean childSeat;
    private boolean archived;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;
    @ElementCollection
    @CollectionTable(name = "ride_tags", joinColumns = @JoinColumn(name = "ride_id"))
    @Column(name = "tag", length = 40, nullable = false)
    private Set<String> tags = new LinkedHashSet<>();

    protected Ride() {}

    public Ride(String externalCode, RideStatus initialStatus) {
        this.publicId = UUID.randomUUID();
        this.externalCode = externalCode;
        this.status = initialStatus;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void updateDetails(RideRequest request, PassengerRow passenger, Tariff tariff, boolean childSeat,
                              Set<String> newTags, RideStatus status) {
        this.pickupAddress = request.pickupAddress();
        this.dropoffAddress = request.dropoffAddress();
        this.pickupAt = request.pickupAt();
        this.fare = request.fare();
        this.passenger = passenger;
        this.tariff = tariff;
        this.childSeat = childSeat;
        this.status = status;
        this.tags.clear();
        this.tags.addAll(newTags);
        markUpdated();
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
        markUpdated();
    }

    public void setStatus(RideStatus status) {
        this.status = status;
        markUpdated();
    }

    public void changeTag(String tag, boolean add) {
        if (add) {
            tags.add(tag);
        } else {
            tags.remove(tag);
        }
        markUpdated();
    }

    private void markUpdated() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public String getExternalCode() { return externalCode; }
    public PassengerRow getPassenger() { return passenger; }
    public Tariff getTariff() { return tariff; }
    public String getPickupAddress() { return pickupAddress; }
    public String getDropoffAddress() { return dropoffAddress; }
    public LocalDateTime getPickupAt() { return pickupAt; }
    public BigDecimal getFare() { return fare; }
    public RideStatus getStatus() { return status; }
    public boolean isChildSeat() { return childSeat; }
    public boolean isArchived() { return archived; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public Set<String> getTags() { return Set.copyOf(tags); }
    public Priority getPriority() { return Priority.calculate(childSeat, fare); }
}
