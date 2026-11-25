package ru.mirea.taxi.backend.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tariffs")
public class Tariff {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 80, unique = true)
    private String name;
    @Column(nullable = false, length = 300)
    private String description;
    private boolean active;

    protected Tariff() {}

    public Tariff(String name, String description, boolean active) {
        update(name, description, active);
    }

    public void update(String name, String description, boolean active) {
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
}
