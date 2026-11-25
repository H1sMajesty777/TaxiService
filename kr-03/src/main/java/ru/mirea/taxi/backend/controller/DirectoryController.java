package ru.mirea.taxi.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.mirea.taxi.backend.dto.DirectoryData;
import ru.mirea.taxi.backend.service.DirectoryService;

import java.util.List;
import java.util.UUID;

@RestController
public class DirectoryController {
    private final DirectoryService directory;

    public DirectoryController(DirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping("/api/tariffs")
    public List<DirectoryData.TariffView> tariffs() {
        return directory.tariffs();
    }

    @GetMapping("/api/passengers")
    public List<DirectoryData.PassengerView> passengers() {
        return directory.passengers();
    }

    @PostMapping("/api/tariffs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DirectoryData.TariffView create(@Valid @RequestBody DirectoryData.TariffInput body, Authentication user) {
        return directory.create(body, UUID.fromString(user.getName()));
    }

    @PutMapping("/api/tariffs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DirectoryData.TariffView update(@PathVariable UUID id, @Valid @RequestBody DirectoryData.TariffInput input,
                                           Authentication user) {
        return directory.update(id, input, UUID.fromString(user.getName()));
    }
}
