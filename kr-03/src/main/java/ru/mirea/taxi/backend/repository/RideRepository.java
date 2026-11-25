package ru.mirea.taxi.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.mirea.taxi.backend.domain.Ride;

import java.util.Optional;
import java.util.UUID;

public interface RideRepository extends JpaRepository<Ride, Long>, JpaSpecificationExecutor<Ride> {
    Optional<Ride> findByPublicId(UUID publicId);
    Optional<Ride> findByExternalCode(String code);
    boolean existsByExternalCode(String code);
}
