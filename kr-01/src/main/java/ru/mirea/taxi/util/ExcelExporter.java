package ru.mirea.taxi.util;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.mirea.taxi.exception.BusinessException;
import ru.mirea.taxi.model.RideOrder;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

public final class ExcelExporter {
    private static final List<String> HEADERS = List.of(
            "ID заказа", "ID пассажира", "Пассажир", "Адрес подачи",
            "Адрес назначения", "Подача (МСК)", "Стоимость, ₽", "Статус"
    );
    private static final List<Integer> COLUMN_WIDTHS = List.of(13, 15, 30, 37, 37, 23, 17, 20);

    public Path writeRides(Path file, List<RideOrder> rides) throws IOException {
        Path destination = file.toAbsolutePath().normalize();
        if (destination.getFileName() == null
                || !destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException("Имя файла экспорта должно оканчиваться на .xlsx.");
        }

        Path parent = destination.getParent();
        Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(parent, "ride-orders-", ".tmp");
        try {
            try (var workbook = new XSSFWorkbook()) {
                fillSheet(workbook, rides);
                try (var output = Files.newOutputStream(temporaryFile)) {
                    workbook.write(output);
                }
            }
            try {
                Files.move(temporaryFile, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return destination;
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static void fillSheet(XSSFWorkbook workbook, List<RideOrder> rides) {
        var sheet = workbook.createSheet("Заказы поездок");
        var header = sheet.createRow(0);
        var headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy hh:mm"));

        CellStyle moneyStyle = workbook.createCellStyle();
        moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        for (int column = 0; column < HEADERS.size(); column++) {
            var cell = header.createCell(column);
            cell.setCellValue(HEADERS.get(column));
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(column, COLUMN_WIDTHS.get(column) * 256);
        }

        int rowIndex = 1;
        for (RideOrder ride : rides) {
            var row = sheet.createRow(rowIndex++);
            // Excel округляет длинные числовые идентификаторы, поэтому сохраняем их как текст.
            row.createCell(0).setCellValue(Long.toString(ride.id()));
            row.createCell(1).setCellValue(Long.toString(ride.passenger().id()));
            row.createCell(2).setCellValue(ride.passenger().fullName());
            row.createCell(3).setCellValue(ride.pickupAddress());
            row.createCell(4).setCellValue(ride.dropoffAddress());

            var pickupTime = row.createCell(5);
            pickupTime.setCellValue(ride.pickupAt());
            pickupTime.setCellStyle(dateStyle);

            var fare = row.createCell(6);
            fare.setCellValue(ride.fare().doubleValue());
            fare.setCellStyle(moneyStyle);

            row.createCell(7).setCellValue(ride.status().label());
        }

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, HEADERS.size() - 1));
    }
}
