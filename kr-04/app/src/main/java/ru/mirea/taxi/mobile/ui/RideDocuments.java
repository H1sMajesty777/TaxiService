package ru.mirea.taxi.mobile.ui;

import android.app.AlertDialog;
import android.net.Uri;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static ru.mirea.taxi.mobile.net.Gateway.call;

final class RideDocuments {
    private static final int MAX_CSV = 512 * 1024;
    private final MainActivity activity;
    private final Views view;

    RideDocuments(MainActivity activity, Views view) {
        this.activity = activity;
        this.view = view;
    }

    void importFile(Runnable reload) {
        new AlertDialog.Builder(activity).setTitle("Режим импорта")
                .setItems(new String[]{"Только новые заказы", "Обновлять по внешнему коду"}, (dialog, index) -> {
                    String mode = index == 0 ? "NEW_ONLY" : "UPSERT";
                    activity.chooseCsv(false, uri -> preview(uri, mode, reload));
                }).setNegativeButton("Отмена", null).show();
    }

    private void preview(Uri uri, String mode, Runnable reload) {
        activity.task(() -> {
            byte[] bytes = read(uri);
            return new PreparedCsv(bytes, call(activity.api().preview(mode, part(bytes))));
        }, prepared -> {
            var preview = prepared.preview();
            var message = new StringBuilder("Корректных строк: " + preview.valid() + "\nОшибок: " + preview.invalid());
            for (var error : preview.errors().subList(0, Math.min(10, preview.errors().size()))) {
                message.append("\nСтрока ").append(error.row()).append(": ").append(error.error());
            }
            if (preview.invalid() == 0) {
                view.confirm("Применить импорт", message.toString(), () -> activity.task(
                        () -> call(activity.api().apply(mode, part(prepared.bytes()))), applied -> {
                            reload.run();
                            view.message("Импорт выполнен", "Создано: " + applied.inserted()
                                    + ". Обновлено: " + applied.updated() + ".");
                        }));
            } else {
                view.message("Исправьте файл", message.toString());
            }
        });
    }

    void export(Map<String, String> filters) {
        activity.chooseCsv(true, uri -> activity.task(() -> {
            try (var response = call(activity.api().export(filters));
                 var output = activity.getContentResolver().openOutputStream(uri, "wt")) {
                if (response == null || output == null) throw new IOException("Файл не открывается");
                try (var input = response.byteStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
            }
            return null;
        }, ignored -> activity.notifyUser("CSV сохранён.")));
    }

    private byte[] read(Uri uri) throws IOException {
        try (var source = activity.getContentResolver().openInputStream(uri)) {
            if (source == null) throw new IOException("Файл не открывается");
            var result = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) {
                if (result.size() + count > MAX_CSV) throw new IllegalArgumentException("CSV должен быть не больше 512 КБ.");
                result.write(buffer, 0, count);
            }
            return result.toByteArray();
        }
    }

    private static MultipartBody.Part part(byte[] bytes) {
        return MultipartBody.Part.createFormData("file", "ride-orders.csv",
                RequestBody.create(bytes, MediaType.get("text/csv")));
    }

    private record PreparedCsv(byte[] bytes, ru.mirea.taxi.mobile.net.Models.CsvPreview preview) {}
}
