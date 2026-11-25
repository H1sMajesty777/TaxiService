package ru.mirea.taxi.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.taxi.backend.domain.RideNote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideNoteRepository extends JpaRepository<RideNote, UUID> {
    List<RideNote> findByRidePublicIdOrderByPinnedDescCreatedAtDesc(UUID rideId);
    Optional<RideNote> findByIdAndRidePublicId(UUID id, UUID rideId);
}
