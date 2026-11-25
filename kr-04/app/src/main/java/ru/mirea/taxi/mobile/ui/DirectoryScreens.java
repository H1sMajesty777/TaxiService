package ru.mirea.taxi.mobile.ui;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.LinearLayout;
import ru.mirea.taxi.mobile.net.Models.*;

import java.util.List;
import java.util.Map;

import static ru.mirea.taxi.mobile.net.Gateway.call;

final class DirectoryScreens {
    private final MainActivity activity;
    private final Views view;
    private String query = "";
    private String auditSort = "time";
    private String auditOrder = "desc";

    DirectoryScreens(MainActivity activity, Views view) {
        this.activity = activity;
        this.view = view;
    }

    void analytics() {
        var content = body("Аналитика");
        activity.task(() -> call(activity.api().summary()), stats -> {
            content.addView(metric("Все заказы", Long.toString(stats.total())));
            content.addView(metric("Активные / архивные", stats.active() + " / " + stats.archived()));
            content.addView(metric("Детское кресло", Long.toString(stats.childSeat())));
            content.addView(metric("Средний чек", TaxiText.amount(stats.averageFare())));
            group(content, "По тарифу", stats.byTariff(), Map.of());
            group(content, "По статусу", stats.byStatus(), TaxiText.STATUSES);
            group(content, "По приоритету", stats.byPriority(), TaxiText.PRIORITIES);
        });
    }

    void tariffs() {
        var content = body("Тарифы");
        if (activity.user().admin()) {
            content.addView(view.primary("Новый тариф", () -> tariffForm(null)));
        }
        activity.task(() -> call(activity.api().tariffs()), tariffs -> {
            if (tariffs.isEmpty()) content.addView(view.text("Тарифов пока нет.", 15, Views.MUTED, false));
            for (Tariff tariff : tariffs) {
                var card = view.card();
                card.addView(view.text(tariff.name(), 19, Views.TEXT, true));
                card.addView(view.text(tariff.description(), 15, Views.TEXT, false));
                card.addView(view.text(tariff.active() ? "Доступен" : "Отключён", 14, Views.MUTED, false));
                if (activity.user().admin()) {
                    var actions = view.row();
                    actions.addView(view.button("Изменить", () -> tariffForm(tariff)));
                    actions.addView(view.button(tariff.active() ? "Отключить" : "Включить", () -> activity.task(
                            () -> call(activity.api().updateTariff(tariff.id(), new TariffInput(
                                    tariff.name(), tariff.description(), !tariff.active()))), saved -> tariffs())));
                    card.addView(actions);
                }
                content.addView(card);
            }
        });
    }

    private void tariffForm(Tariff tariff) {
        var fields = view.column();
        var name = view.field(fields, "Название", tariff == null ? "" : tariff.name(), MainActivity.TEXT);
        var description = view.field(fields, "Описание", tariff == null ? "" : tariff.description(),
                MainActivity.TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        var active = view.check(fields, "Доступен", tariff == null || tariff.active());
        var error = view.text("", 14, Views.ERROR, false);
        fields.addView(error);
        var dialog = view.form(tariff == null ? "Новый тариф" : "Редактирование тарифа", fields);
        dialog.show();
        var save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setOnClickListener(pressed -> {
            String title = Views.value(name);
            String details = Views.value(description);
            if (title.isEmpty() || title.length() > 80 || details.isEmpty() || details.length() > 300) {
                error.setText("Укажите название до 80 и описание до 300 символов.");
                return;
            }
            var input = new TariffInput(title, details, active.isChecked());
            save.setEnabled(false);
            activity.task(() -> tariff == null ? call(activity.api().createTariff(input))
                    : call(activity.api().updateTariff(tariff.id(), input)), result -> {
                dialog.dismiss();
                tariffs();
            }, failure -> {
                save.setEnabled(true);
                activity.onFailure(failure, error);
            });
        });
    }

    void users(int page) {
        if (!activity.user().admin()) {
            activity.home();
            return;
        }
        var content = body("Пользователи");
        content.addView(view.primary("Новый пользователь", () -> accountForm(null, page)));
        activity.task(() -> call(activity.api().users(page, 10)), result -> {
            for (Profile user : result.content()) {
                var card = view.card();
                card.addView(view.text(user.name(), 18, Views.TEXT, true));
                card.addView(view.text(user.email() + " · " + TaxiText.ROLES.getOrDefault(user.role(), user.role()),
                        15, Views.MUTED, false));
                card.addView(view.text((user.active() ? "Активен" : "Отключён")
                        + (user.mustChangePassword() ? " · Временный пароль" : ""), 14, Views.TEXT, false));
                var buttons = view.row();
                buttons.addView(view.button("Изменить", () -> accountForm(user, page)));
                if (!activity.user().id().equals(user.id())) {
                    buttons.addView(view.button("Сбросить пароль", () -> resetPassword(user, page)));
                }
                card.addView(buttons);
                content.addView(card);
            }
            content.addView(pager(result, () -> users(page - 1), () -> users(page + 1)));
        });
    }

    private void accountForm(Profile user, int page) {
        var fields = view.column();
        var name = view.field(fields, "Имя", user == null ? "" : user.name(), MainActivity.TEXT);
        var login = view.field(fields, "Логин", user == null ? "" : user.email(),
                MainActivity.TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        var role = view.select(fields, "Роль", List.of(new Views.Option<>("VIEWER", "Наблюдатель"),
                new Views.Option<>("DISPATCHER", "Диспетчер"), new Views.Option<>("ADMIN", "Администратор")),
                user == null ? "VIEWER" : user.role());
        var active = view.check(fields, "Активен", user == null || user.active());
        var temporary = user == null ? view.field(fields, "Временный пароль", "", MainActivity.PASSWORD) : null;
        var error = view.text("", 14, Views.ERROR, false);
        fields.addView(error);
        var dialog = view.form(user == null ? "Новый пользователь" : "Изменить профиль", fields);
        dialog.show();
        var save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setOnClickListener(pressed -> {
            String email = Views.value(login);
            String fullName = Views.value(name);
            String secret = temporary == null ? null : temporary.getText().toString();
            try {
                if (fullName.isEmpty() || fullName.length() > 100
                        || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() || email.length() > 120) {
                    throw new IllegalArgumentException("Укажите имя и корректный email.");
                }
                if (secret != null) TaxiText.password(secret);
            } catch (IllegalArgumentException invalid) {
                error.setText(invalid.getMessage());
                return;
            }
            var input = new ProfileInput(email, fullName, role.value(), active.isChecked());
            save.setEnabled(false);
            activity.task(() -> user == null ? call(activity.api().createUser(new AccountInput(input, secret)))
                    : call(activity.api().updateUser(user.id(), input)), result -> {
                dialog.dismiss();
                users(page);
            }, failure -> {
                save.setEnabled(true);
                activity.onFailure(failure, error);
            });
        });
    }

    private void resetPassword(Profile profile, int page) {
        var fields = view.column();
        var password = view.field(fields, "Временный пароль", "", MainActivity.PASSWORD);
        var error = view.text("", 14, Views.ERROR, false);
        fields.addView(error);
        var dialog = view.form("Новый пароль · " + profile.email(), fields);
        dialog.show();
        var save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setOnClickListener(pressed -> {
            String secret = password.getText().toString();
            try {
                TaxiText.password(secret);
            } catch (IllegalArgumentException invalid) {
                error.setText(invalid.getMessage());
                return;
            }
            save.setEnabled(false);
            activity.task(() -> call(activity.api().resetPassword(profile.id(), new TemporaryPassword(secret))),
                    result -> {
                        dialog.dismiss();
                        users(page);
                        activity.notifyUser("Временный пароль установлен.");
                    }, failure -> {
                        save.setEnabled(true);
                        activity.onFailure(failure, error);
                    });
        });
    }

    void audit(int page) {
        if (!activity.user().admin()) {
            activity.home();
            return;
        }
        var content = body("Журнал аудита");
        var controls = view.card();
        var input = view.field(controls, "Поиск по описанию, событию или ID запроса", query, MainActivity.TEXT);
        var sort = view.select(controls, "Поле сортировки", List.of(new Views.Option<>("time", "Время"),
                new Views.Option<>("event", "Событие"), new Views.Option<>("entity", "Сущность")), auditSort);
        var direction = view.select(controls, "Порядок", List.of(new Views.Option<>("desc", "По убыванию"),
                new Views.Option<>("asc", "По возрастанию")), auditOrder);
        controls.addView(view.button("Найти", () -> {
            query = Views.value(input);
            auditSort = sort.value();
            auditOrder = direction.value();
            audit(0);
        }));
        content.addView(controls);
        String selectedQuery = query;
        String selectedSort = auditSort;
        String selectedDirection = auditOrder;
        activity.task(() -> call(activity.api().audit(selectedQuery, selectedSort, selectedDirection, page, 10)), result -> {
            for (Audit event : result.content()) {
                var card = view.card();
                card.addView(view.text(event.eventType() + " · " + event.entityType(), 17, Views.TEXT, true));
                card.addView(view.text(event.description(), 15, Views.TEXT, false));
                card.addView(view.text(TaxiText.time(event.occurredAt()) + " UTC · " + event.correlationId(),
                        13, Views.MUTED, false));
                if (event.actorId() != null) card.addView(view.text("Пользователь " + event.actorId(), 13, Views.MUTED, false));
                content.addView(card);
            }
            content.addView(pager(result, () -> audit(page - 1), () -> audit(page + 1)));
        });
    }

    private LinearLayout body(String title) {
        var root = activity.screen(title, activity::home);
        var body = view.column();
        root.addView(view.scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
        return body;
    }

    private LinearLayout metric(String label, String value) {
        var card = view.card();
        card.addView(view.text(label, 14, Views.MUTED, false));
        card.addView(view.text(value, 24, Views.TEXT, true));
        return card;
    }

    private void group(LinearLayout content, String title, Map<String, Long> entries, Map<String, String> labels) {
        var card = view.card();
        card.addView(view.text(title, 18, Views.TEXT, true));
        entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> card.addView(view.text(
                labels.getOrDefault(entry.getKey(), entry.getKey()) + " · " + entry.getValue(), 15, Views.TEXT, false)));
        if (entries.isEmpty()) card.addView(view.text("Нет данных", 14, Views.MUTED, false));
        content.addView(card);
    }

    private LinearLayout pager(Page<?> page, Runnable previous, Runnable next) {
        var row = view.row();
        var back = view.button("Назад", previous);
        var forward = view.button("Далее", next);
        back.setEnabled(page.page() > 0);
        forward.setEnabled(page.page() + 1 < page.totalPages());
        row.addView(back);
        row.addView(view.text("  " + (page.page() + 1) + " / " + Math.max(1, page.totalPages()),
                14, Views.MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(forward);
        return row;
    }
}
