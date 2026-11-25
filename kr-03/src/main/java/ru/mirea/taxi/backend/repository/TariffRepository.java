package ru.mirea.taxi.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.taxi.backend.domain.Tariff;

import java.util.UUID;

public interface TariffRepository extends JpaRepository<Tariff, UUID> {}
