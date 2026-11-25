package ru.mirea.taxi.backend.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.taxi.backend.domain.RideNote;
import ru.mirea.taxi.backend.domain.Role;
import ru.mirea.taxi.backend.domain.UserAccount;
import ru.mirea.taxi.backend.error.ApiException;
import ru.mirea.taxi.backend.repository.RideNoteRepository;
import ru.mirea.taxi.backend.repository.UserAccountRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NoteService {
    private final RideService rides;
    private final RideNoteRepository notes;
    private final UserAccountRepository users;
    private final AuditService audit;

    public NoteService(RideService rides, RideNoteRepository notes, UserAccountRepository users, AuditService audit) {
        this.rides = rides;
        this.notes = notes;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<View> forRide(UUID rideId) {
        rides.find(rideId);
        return notes.findByRidePublicIdOrderByPinnedDescCreatedAtDesc(rideId).stream().map(View::from).toList();
    }

    @Transactional
    public View create(UUID rideId, Input input, UUID actor) {
        var ride = rides.find(rideId);
        var note = notes.save(new RideNote(ride, account(actor), input.body().strip(), input.pinned()));
        audit.record(actor, "NOTE_CREATED", "NOTE", note.getId(), "Заметка к " + ride.getExternalCode());
        return View.from(note);
    }

    @Transactional
    public View update(UUID rideId, UUID noteId, Input input, UUID actor) {
        var note = findOwnedNote(rideId, noteId, actor);
        note.update(input.body().strip(), input.pinned());
        audit.record(actor, "NOTE_UPDATED", "NOTE", noteId, "Заметка обновлена");
        return View.from(note);
    }

    @Transactional
    public void delete(UUID rideId, UUID noteId, UUID actor) {
        notes.delete(findOwnedNote(rideId, noteId, actor));
        audit.record(actor, "NOTE_DELETED", "NOTE", noteId, "Заметка удалена");
    }

    private RideNote findOwnedNote(UUID rideId, UUID noteId, UUID actor) {
        var note = notes.findByIdAndRidePublicId(noteId, rideId)
                .orElseThrow(() -> ApiException.notFound("Заметка не найдена."));
        if (!note.getAuthor().getId().equals(actor) && account(actor).getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Изменить заметку может её автор или администратор.");
        }
        return note;
    }

    private UserAccount account(UUID actor) {
        return users.findById(actor).orElseThrow(() -> ApiException.notFound("Автор не найден."));
    }

    public record Input(@NotBlank @Size(max = 1500) String body, boolean pinned) {}

    public record View(UUID id, UUID authorId, String author, String body, boolean pinned, Instant createdAt,
                       Instant updatedAt) {
        static View from(RideNote note) {
            return new View(note.getId(), note.getAuthor().getId(), note.getAuthor().getName(),
                    note.getBody(), note.isPinned(), note.getCreatedAt(), note.getUpdatedAt());
        }
    }
}
