package ru.mirea.taxi.mobile.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import ru.mirea.taxi.mobile.net.Gateway;
import ru.mirea.taxi.mobile.net.Models.Profile;
import ru.mirea.taxi.mobile.net.TaxiApi;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    static final int TEXT = InputType.TYPE_CLASS_TEXT;
    static final int PASSWORD = TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    private static final int OPEN_CSV = 601;
    private static final int SAVE_CSV = 602;
    private final ExecutorService network = Executors.newFixedThreadPool(3);

    private Gateway gateway;
    private Views view;
    private RideScreens rides;
    private DirectoryScreens directory;
    private Profile user;
    private int generation;
    private Runnable backAction;
    private Consumer<Uri> documentAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gateway = new Gateway(this);
        view = new Views(this);
        rides = new RideScreens(this, view);
        directory = new DirectoryScreens(this, view);
        var start = screen("Служба такси", null);
        start.addView(view.text("Проверка сессии…", 16, Views.MUTED, false));
        task(gateway::restore, profile -> {
            if (profile == null) login(null);
            else signedIn(profile);
        }, error -> login(readable(error)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (backAction != null) {
            backAction.run();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        generation++;
        network.shutdown();
        super.onDestroy();
    }

    LinearLayout screen(String title, Runnable back) {
        generation++;
        backAction = back;
        var root = view.column();
        root.setBackgroundColor(Views.BACKGROUND);
        int space = view.dp(14);
        root.setPadding(space, space, space, space);
        root.setOnApplyWindowInsetsListener((content, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var area = insets.getInsets(WindowInsets.Type.systemBars());
                content.setPadding(space + area.left, space + area.top, space + area.right, space + area.bottom);
            }
            return insets;
        });
        var heading = view.row();
        TextView label = view.text(title, 24, Views.TEXT, true);
        heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        if (back != null) heading.addView(view.button("Назад", back));
        root.addView(heading);
        setContentView(root);
        return root;
    }

    private void login(String initialError) {
        user = null;
        rides.resetSelection();
        var root = screen("Служба такси", null);
        var form = view.card();
        var login = view.field(form, "Логин", "", TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        var password = view.field(form, "Пароль", "", PASSWORD);
        var error = view.text(initialError == null ? "" : initialError, 14, Views.ERROR, false);
        form.addView(error);
        var action = view.primary("Войти", () -> {});
        action.setOnClickListener(pressed -> {
            if (Views.value(login).isEmpty() || password.getText().length() == 0) {
                error.setText("Введите логин и пароль.");
                return;
            }
            action.setEnabled(false);
            String email = Views.value(login);
            String secret = password.getText().toString();
            task(() -> gateway.login(email, secret), this::signedIn, failure -> {
                action.setEnabled(true);
                error.setText(readable(failure));
            });
        });
        form.addView(action);
        root.addView(view.scroll(form), new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void signedIn(Profile profile) {
        user = profile;
        if (profile.mustChangePassword()) password(true);
        else home();
    }

    void home() {
        var root = screen("Служба такси", null);
        var menu = view.card();
        menu.addView(view.text(user.name(), 20, Views.TEXT, true));
        menu.addView(view.text(user.email() + " · " + TaxiText.ROLES.getOrDefault(user.role(), user.role()),
                14, Views.MUTED, false));
        menu.addView(view.primary("Заказы", rides::list));
        menu.addView(view.button("Аналитика", directory::analytics));
        menu.addView(view.button("Тарифы", directory::tariffs));
        if (user.admin()) {
            menu.addView(view.button("Пользователи", () -> directory.users(0)));
            menu.addView(view.button("Журнал аудита", () -> directory.audit(0)));
        }
        menu.addView(view.button("Сменить пароль", () -> password(false)));
        menu.addView(view.button("Выйти", this::logout));
        root.addView(view.scroll(menu), new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void password(boolean required) {
        var root = screen(required ? "Установите новый пароль" : "Смена пароля", required ? null : this::home);
        var form = view.card();
        var current = view.field(form, "Текущий пароль", "", PASSWORD);
        var next = view.field(form, "Новый пароль", "", PASSWORD);
        var error = view.text("", 14, Views.ERROR, false);
        form.addView(error);
        var save = view.primary("Сохранить пароль", () -> {});
        save.setOnClickListener(pressed -> {
            String oldSecret = current.getText().toString();
            String newSecret = next.getText().toString();
            try {
                if (oldSecret.isEmpty()) throw new IllegalArgumentException("Введите текущий пароль.");
                TaxiText.password(newSecret);
            } catch (IllegalArgumentException invalid) {
                error.setText(invalid.getMessage());
                return;
            }
            save.setEnabled(false);
            task(() -> gateway.changePassword(oldSecret, newSecret), profile -> {
                signedIn(profile);
                notifyUser("Пароль изменён.");
            }, failure -> {
                save.setEnabled(true);
                onFailure(failure, error);
            });
        });
        form.addView(save);
        if (required) form.addView(view.button("Выйти", this::logout));
        root.addView(view.scroll(form), new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void logout() {
        screen("Завершение сессии", null);
        task(() -> {
            gateway.logout();
            return null;
        }, ignored -> login(null), error -> login("Локальная сессия завершена. Сервер недоступен для отзыва."));
    }

    TaxiApi api() { return gateway.api(); }
    Profile user() { return user; }

    <T> void task(Callable<T> request, Consumer<T> done) {
        task(request, done, failure -> onFailure(failure, null));
    }

    <T> void task(Callable<T> request, Consumer<T> done, Consumer<Exception> failed) {
        int screen = generation;
        network.execute(() -> {
            try {
                T result = request.call();
                runOnUiThread(() -> {
                    if (screen == generation && !isFinishing()) done.accept(result);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (screen == generation && !isFinishing()) failed.accept(error);
                });
            }
        });
    }

    void onFailure(Exception failure, TextView target) {
        if (failure instanceof Gateway.ApiFailure api && api.status() == 401 && user != null) {
            gateway.clear();
            login("Сессия завершена. Войдите снова.");
        } else if (target != null) {
            target.setText(readable(failure));
        } else {
            view.message("Операция не выполнена", readable(failure));
        }
    }

    private static String readable(Exception failure) {
        if (failure instanceof ConnectException || failure instanceof SocketTimeoutException) {
            return "Нет связи с сервером. Проверьте запуск backend и его адрес.";
        }
        if (failure instanceof IOException && !(failure instanceof Gateway.ApiFailure)) {
            return "Не удалось выполнить операцию. Проверьте сеть и доступ к файлу.";
        }
        return failure.getMessage() == null ? "Проверьте данные и повторите действие." : failure.getMessage();
    }

    void notifyUser(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("deprecation")
    void chooseCsv(boolean save, Consumer<Uri> action) {
        documentAction = action;
        var intent = new Intent(save ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(save ? "text/csv" : "*/*");
        if (save) intent.putExtra(Intent.EXTRA_TITLE, "ride-orders.csv");
        startActivityForResult(intent, save ? SAVE_CSV : OPEN_CSV);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == OPEN_CSV || request == SAVE_CSV) {
            var action = documentAction;
            documentAction = null;
            if (result == RESULT_OK && data != null && data.getData() != null && action != null) {
                action.accept(data.getData());
            }
        }
    }
}
