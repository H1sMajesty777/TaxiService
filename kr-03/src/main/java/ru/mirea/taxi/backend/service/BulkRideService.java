package ru.mirea.taxi.backend.service;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import ru.mirea.taxi.backend.dto.RideData;
import ru.mirea.taxi.backend.error.ApiException;
import ru.mirea.taxi.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BulkRideService {
    private final RideService rides;

    public BulkRideService(RideService rides) {
        this.rides = rides;
    }

    public RideData.BulkResult apply(RideData.BulkInput input, UUID actor) {
        List<UUID> succeeded = new ArrayList<>();
        List<RideData.BulkFailure> failed = new ArrayList<>();
        for (UUID id : input.ids().stream().sorted().toList()) {
            try {
                rides.applyBulk(id, input, actor);
                succeeded.add(id);
            } catch (BusinessException | ApiException error) {
                failed.add(new RideData.BulkFailure(id, error.getMessage()));
            } catch (OptimisticLockingFailureException error) {
                failed.add(new RideData.BulkFailure(id, "Заказ уже изменился."));
            }
        }
        return new RideData.BulkResult(succeeded, failed);
    }
}
