package ru.mirea.taxi.backend.service;

import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.taxi.backend.domain.Ride;
import ru.mirea.taxi.backend.dto.RideData;
import ru.mirea.taxi.backend.filter.RideFilter;
import ru.mirea.taxi.backend.repository.PassengerDirectory;
import ru.mirea.taxi.backend.repository.RideRepository;
import ru.mirea.taxi.backend.repository.TariffRepository;
import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.model.RideStatus;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CsvRideService {
    private static final String[] HEADERS = {"externalCode", "passengerId", "tariffId", "pickupAddress",
            "dropoffAddress", "pickupAt", "fare", "status", "childSeat", "tags"};
    private static final int MAX_ROWS = 1000;

    private final RideRepository orders;
    private final RideService rides;
    private final TariffRepository tariffs;
    private final PassengerDirectory passengers;
    private final Validator validator;
    private final AuditService audit;

    public CsvRideService(RideRepository orders, RideService rides, TariffRepository tariffs,
                          PassengerDirectory passengers, Validator validator, AuditService audit) {
        this.orders = orders;
        this.rides = rides;
        this.tariffs = tariffs;
        this.passengers = passengers;
        this.validator = validator;
        this.audit = audit;
    }

    public byte[] export(RideFilter filter, String sort, String direction) {
        var rows = rides.export(filter, sort, direction);
        var buffer = new StringWriter();
        buffer.append('\uFEFF');
        try (var csv = new CSVPrinter(buffer, CSVFormat.RFC4180.builder().setHeader(HEADERS).get())) {
            for (var row : rows) {
                csv.printRecord(row.externalCode(), row.passengerId(), row.tariffId(),
                        spreadsheetText(row.pickupAddress()), spreadsheetText(row.dropoffAddress()),
                        row.pickupAt(), row.fare(), row.status(), row.childSeat(),
                        spreadsheetText(String.join("|", row.tags())));
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return buffer.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public Preview preview(byte[] file, ImportMode mode) {
        return check(file, mode).preview();
    }

    @Transactional
    public Applied apply(byte[] file, ImportMode mode, UUID actor) {
        Check check = check(file, mode);
        if (check.preview().invalid() != 0) {
            throw new BusinessException("Исправьте ошибки в CSV и повторите предварительную проверку.");
        }
        int inserted = 0;
        int updated = 0;
        for (RideData.Input row : check.validRows()) {
            var existing = orders.findByExternalCode(row.externalCode());
            if (existing.isPresent()) {
                if (mode != ImportMode.UPSERT) {
                    throw new BusinessException("Код уже используется. Обновите проверку файла.");
                }
                RideService.requireActive(existing.get());
                rides.applyDetails(existing.get(), row);
                updated++;
            } else {
                rides.newRide(row);
                inserted++;
            }
        }
        orders.flush();
        audit.record(actor, "CSV_IMPORTED", "RIDE", null, "Создано " + inserted + ", обновлено " + updated);
        return new Applied(inserted, updated);
    }

    private Check check(byte[] file, ImportMode mode) {
        List<RideData.Input> valid = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        var format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).get();
        try {
            String source = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(file)).toString();
            if (source.startsWith("\uFEFF")) {
                source = source.substring(1);
            }
            try (var csv = CSVParser.parse(source, format)) {
                if (!csv.getHeaderNames().equals(List.of(HEADERS))) {
                    throw new BusinessException("Заголовки CSV должны совпадать с экспортом.");
                }
                for (CSVRecord record : csv) {
                    long row = record.getRecordNumber() + 1;
                    if (record.getRecordNumber() > MAX_ROWS) {
                        throw new BusinessException("Один файл должен содержать не больше 1000 строк данных.");
                    }
                    try {
                        RideData.Input input = parse(record);
                        validate(input, mode, codes);
                        valid.add(input);
                    } catch (BusinessException error) {
                        errors.add(new RowError(row, error.getMessage()));
                    } catch (IllegalArgumentException | java.time.DateTimeException error) {
                        errors.add(new RowError(row, "Проверьте ID, дату, сумму, статус и логический признак."));
                    }
                }
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException error) {
            throw new BusinessException("CSV не читается. Проверьте кодировку UTF-8 и кавычки.");
        }
        if (valid.isEmpty() && errors.isEmpty()) {
            throw new BusinessException("В файле нет строк с заказами.");
        }
        return new Check(new Preview(valid.size(), errors.size(), List.copyOf(errors)), valid);
    }

    private void validate(RideData.Input input, ImportMode mode, Set<String> codes) {
        var violations = validator.validate(input);
        if (!violations.isEmpty()) {
            throw new BusinessException("Некорректное поле «" + violations.iterator().next().getPropertyPath() + "».");
        }
        if (!codes.add(input.externalCode())) {
            throw new BusinessException("Код заказа повторяется внутри файла.");
        }
        RideService.validateRequest(input);
        RideService.normalizeTags(input.tags());
        if (!passengers.existsById(input.passengerId())) {
            throw new BusinessException("Пассажир не найден.");
        }
        var tariff = tariffs.findById(input.tariffId()).orElseThrow(() -> new BusinessException("Тариф не найден."));
        var existing = orders.findByExternalCode(input.externalCode());
        if (existing.isPresent()) {
            if (mode == ImportMode.NEW_ONLY) {
                throw new BusinessException("Код уже используется в базе.");
            }
            RideService.requireActive(existing.get());
            RideService.checkStatus(existing.get(), input.status());
        }
        if (!tariff.isActive() && existing.map(Ride::getTariff).filter(item -> item.getId().equals(tariff.getId()))
                .isEmpty()) {
            throw new BusinessException("Тариф отключён.");
        }
    }

    private static RideData.Input parse(CSVRecord row) {
        if (!row.isConsistent() || row.get("externalCode").isBlank()) {
            throw new BusinessException("Неверное число полей или пустой код заказа.");
        }
        String seat = row.get("childSeat").strip();
        if (!seat.equalsIgnoreCase("true") && !seat.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("expected boolean");
        }
        String tagField = fromSpreadsheet(row.get("tags"));
        Set<String> tags = tagField.isBlank() ? Set.of() : new LinkedHashSet<>(List.of(tagField.split("\\|", -1)));
        return new RideData.Input(row.get("externalCode").strip().toUpperCase(Locale.ROOT),
                Long.valueOf(row.get("passengerId")), UUID.fromString(row.get("tariffId")),
                fromSpreadsheet(row.get("pickupAddress")), fromSpreadsheet(row.get("dropoffAddress")),
                LocalDateTime.parse(row.get("pickupAt")), new BigDecimal(row.get("fare")),
                RideStatus.valueOf(row.get("status")), Boolean.parseBoolean(seat), tags, null);
    }

    private static String spreadsheetText(String value) {
        return dangerousStart(value) ? "'" + value : value;
    }

    private static String fromSpreadsheet(String value) {
        if (value.startsWith("'") && dangerousStart(value.substring(1))) {
            return value.substring(1);
        }
        return value;
    }

    private static boolean dangerousStart(String value) {
        String trimmed = value.stripLeading();
        return !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0;
    }

    public enum ImportMode { NEW_ONLY, UPSERT }
    public record RowError(long row, String error) {}
    public record Preview(int valid, int invalid, List<RowError> errors) {}
    public record Applied(int inserted, int updated) {}
    private record Check(Preview preview, List<RideData.Input> validRows) {}
}
