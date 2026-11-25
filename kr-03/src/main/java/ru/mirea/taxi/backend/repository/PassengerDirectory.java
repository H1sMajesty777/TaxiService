package ru.mirea.taxi.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.taxi.backend.domain.PassengerRow;

public interface PassengerDirectory extends JpaRepository<PassengerRow, Long> {}
