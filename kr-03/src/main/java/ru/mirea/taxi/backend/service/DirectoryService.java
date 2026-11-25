package ru.mirea.taxi.backend.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.taxi.backend.domain.Tariff;
import ru.mirea.taxi.backend.dto.DirectoryData;
import ru.mirea.taxi.backend.error.ApiException;
import ru.mirea.taxi.backend.repository.PassengerDirectory;
import ru.mirea.taxi.backend.repository.TariffRepository;

import java.util.List;
import java.util.UUID;

@Service
public class DirectoryService {
    private final TariffRepository tariffs;
    private final PassengerDirectory passengers;
    private final AuditService audit;

    public DirectoryService(TariffRepository tariffs, PassengerDirectory passengers, AuditService audit) {
        this.tariffs = tariffs;
        this.passengers = passengers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DirectoryData.TariffView> tariffs() {
        return tariffs.findAll(Sort.by("name")).stream().map(DirectoryData.TariffView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DirectoryData.PassengerView> passengers() {
        return passengers.findAll(Sort.by("fullName")).stream().map(DirectoryData.PassengerView::from).toList();
    }

    @Transactional
    public DirectoryData.TariffView create(DirectoryData.TariffInput input, UUID actor) {
        Tariff tariff = tariffs.save(new Tariff(input.name().strip(), input.description().strip(), input.active()));
        audit.record(actor, "TARIFF_CREATED", "TARIFF", tariff.getId(), tariff.getName());
        return DirectoryData.TariffView.from(tariff);
    }

    @Transactional
    public DirectoryData.TariffView update(UUID id, DirectoryData.TariffInput input, UUID actor) {
        Tariff tariff = tariffs.findById(id).orElseThrow(() -> ApiException.notFound("Тариф не найден."));
        tariff.update(input.name().strip(), input.description().strip(), input.active());
        audit.record(actor, "TARIFF_UPDATED", "TARIFF", id, tariff.getName());
        return DirectoryData.TariffView.from(tariff);
    }
}
