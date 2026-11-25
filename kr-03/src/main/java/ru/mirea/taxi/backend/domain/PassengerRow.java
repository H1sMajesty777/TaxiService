package ru.mirea.taxi.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "passengers")
public class PassengerRow {
    @Id
    private Long id;
    @Column(nullable = false, length = 100)
    private String fullName;
    @Column(nullable = false, length = 12)
    private String phone;

    protected PassengerRow() {}

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
}
