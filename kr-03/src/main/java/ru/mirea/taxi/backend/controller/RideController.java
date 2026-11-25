package ru.mirea.taxi.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.mirea.taxi.backend.dto.PageResponse;
import ru.mirea.taxi.backend.dto.RideData;
import ru.mirea.taxi.backend.filter.RideFilter;
import ru.mirea.taxi.backend.service.BulkRideService;
import ru.mirea.taxi.backend.service.CsvRideService;
import ru.mirea.taxi.backend.service.NoteService;
import ru.mirea.taxi.backend.service.RideService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rides")
public class RideController {
    private static final String DISPATCH = "hasAnyRole('ADMIN','DISPATCHER')";
    private final RideService rides;
    private final BulkRideService bulk;
    private final CsvRideService csv;
    private final NoteService notes;

    public RideController(RideService rides, BulkRideService bulk, CsvRideService csv, NoteService notes) {
        this.rides = rides;
        this.bulk = bulk;
        this.csv = csv;
        this.notes = notes;
    }

    @GetMapping
    public PageResponse<RideData.View> list(@ModelAttribute RideFilter filter,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "pickupAt") String sort, @RequestParam(defaultValue = "desc") String dir) {
        return rides.list(filter, page, size, sort, dir);
    }

    @GetMapping("/{id}")
    public RideData.View get(@PathVariable UUID id) {
        return rides.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(DISPATCH)
    public RideData.View create(@Valid @RequestBody RideData.Input input, Authentication user) {
        return rides.create(input, actor(user));
    }

    @PutMapping("/{id}")
    @PreAuthorize(DISPATCH)
    public RideData.View update(@PathVariable UUID id, @Valid @RequestBody RideData.Input input, Authentication user) {
        return rides.update(id, input, actor(user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(DISPATCH)
    public RideData.View archive(@PathVariable UUID id, @RequestParam long version, Authentication user) {
        return rides.archive(id, version, true, actor(user));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize(DISPATCH)
    public RideData.View restore(@PathVariable UUID id, @RequestParam long version, Authentication user) {
        return rides.archive(id, version, false, actor(user));
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void permanentlyDelete(@PathVariable UUID id, @RequestParam long version, Authentication user) {
        rides.permanentlyDelete(id, version, actor(user));
    }

    @PostMapping("/bulk")
    @PreAuthorize(DISPATCH)
    public RideData.BulkResult bulk(@Valid @RequestBody RideData.BulkInput input, Authentication user) {
        return bulk.apply(input, actor(user));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@ModelAttribute RideFilter filter,
            @RequestParam(defaultValue = "pickupAt") String sort, @RequestParam(defaultValue = "desc") String dir) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ride-orders.csv")
                .body(csv.export(filter, sort, dir));
    }

    @PostMapping(path = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(DISPATCH)
    public CsvRideService.Preview preview(@RequestPart MultipartFile file,
            @RequestParam(defaultValue = "NEW_ONLY") CsvRideService.ImportMode mode) throws IOException {
        return csv.preview(file.getBytes(), mode);
    }

    @PostMapping(path = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(DISPATCH)
    public CsvRideService.Applied apply(@RequestPart MultipartFile file, Authentication user,
            @RequestParam(defaultValue = "NEW_ONLY") CsvRideService.ImportMode mode) throws IOException {
        return csv.apply(file.getBytes(), mode, actor(user));
    }

    @GetMapping("/{id}/notes")
    public List<NoteService.View> listNotes(@PathVariable UUID id) {
        return notes.forRide(id);
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(DISPATCH)
    public NoteService.View addNote(@PathVariable UUID id, @Valid @RequestBody NoteService.Input input,
                                    Authentication user) {
        return notes.create(id, input, actor(user));
    }

    @PutMapping("/{id}/notes/{noteId}")
    @PreAuthorize(DISPATCH)
    public NoteService.View updateNote(@PathVariable UUID id, @PathVariable UUID noteId,
            @Valid @RequestBody NoteService.Input input, Authentication user) {
        return notes.update(id, noteId, input, actor(user));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(DISPATCH)
    public void deleteNote(@PathVariable UUID id, @PathVariable UUID noteId, Authentication user) {
        notes.delete(id, noteId, actor(user));
    }

    private static UUID actor(Authentication user) {
        return UUID.fromString(user.getName());
    }
}
