package ru.mirea.taxi.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ride_notes")
public class RideNote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id")
    private Ride ride;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private UserAccount author;
    @Column(nullable = false, length = 1500)
    private String body;
    private boolean pinned;
    private Instant createdAt;
    private Instant updatedAt;

    protected RideNote() {}

    public RideNote(Ride ride, UserAccount author, String body, boolean pinned) {
        this.ride = ride;
        this.author = author;
        this.createdAt = Instant.now();
        update(body, pinned);
    }

    public void update(String body, boolean pinned) {
        this.body = body;
        this.pinned = pinned;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UserAccount getAuthor() { return author; }
    public String getBody() { return body; }
    public boolean isPinned() { return pinned; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
