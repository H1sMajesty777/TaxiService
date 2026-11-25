package ru.mirea.taxi.mobile.ui;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import ru.mirea.taxi.mobile.net.Models.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ru.mirea.taxi.mobile.net.Gateway.call;

final class RideScreens {
    private final MainActivity activity;
    private final Views view;
    private final RideEditor editor;
    private final RideDocuments documents;
    private final Map<String, String> filters = new HashMap<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private int page;
    private int loadTicket;

    RideScreens(MainActivity activity, Views view) {
        this.activity = activity;
        this.view = view;
        this.editor = new RideEditor(activity, view);
        this.documents = new RideDocuments(activity, view);
        filters.put("archive", "ACTIVE");
    }

    void resetSelection() {
        selected.clear();
        filters.clear();
        filters.put("archive", "ACTIVE");
        page = 0;
    }

    void list() {
        var root = activity.screen("Заказы", activity::home);
        var toolbar = view.row();
        toolbar.addView(view.button("Поиск и фильтры", this::openFilters));
        if (activity.user().canEdit()) {
            toolbar.addView(view.primary("Новый заказ", () -> editor.open(null, () -> {
                page = 0;
                list();
            })));
        }
        root.addView(toolbar);
        var csv = view.row();
        csv.addView(view.button("Экспорт CSV", () -> documents.export(Map.copyOf(filters))));
        if (activity.user().canEdit()) {
            csv.addView(view.button("Импорт CSV", () -> documents.importFile(this::list)));
            csv.addView(view.button("Для выбранных", this::bulk));
        }
        root.addView(csv);
        var count = view.text("", 13, Views.MUTED, false);
        root.addView(count);

        var refresh = new SwipeRefreshLayout(activity);
        var list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        var adapter = new RideAdapter(activity.user().canEdit(),
                () -> count.setText("Выбрано заказов: " + selected.size()));
        list.setAdapter(adapter);
        refresh.addView(list);
        root.addView(refresh, new LinearLayout.LayoutParams(-1, 0, 1));

        var pager = view.row();
        var pageLabel = view.text("Загрузка…", 13, Views.MUTED, false);
        var previous = view.button("←", () -> {});
        previous.setContentDescription("Предыдущая страница");
        var next = view.button("→", () -> {});
        next.setContentDescription("Следующая страница");
        previous.setEnabled(false);
        next.setEnabled(false);
        pager.addView(previous);
        pager.addView(pageLabel, new LinearLayout.LayoutParams(0, -2, 1));
        pager.addView(next);
        root.addView(pager);

        Runnable load = () -> {
            int ticket = ++loadTicket;
            var query = new HashMap<>(filters);
            query.put("page", Integer.toString(page));
            query.put("size", "10");
            refresh.setRefreshing(true);
            activity.task(() -> call(activity.api().rides(query)), result -> {
                if (ticket != loadTicket) return;
                refresh.setRefreshing(false);
                if (result.content().isEmpty() && page > 0) {
                    page--;
                    list();
                    return;
                }
                adapter.setItems(result.content());
                count.setText(activity.user().canEdit() ? "Выбрано заказов: " + selected.size() : "");
                pageLabel.setText(result.totalElements() == 0 ? "Заказы не найдены"
                        : "  " + (result.page() + 1) + " / " + result.totalPages() + "  ·  " + result.totalElements());
                previous.setEnabled(result.page() > 0);
                next.setEnabled(result.page() + 1 < result.totalPages());
            }, error -> {
                if (ticket == loadTicket) {
                    refresh.setRefreshing(false);
                    activity.onFailure(error, pageLabel);
                }
            });
        };
        refresh.setOnRefreshListener(load::run);
        previous.setOnClickListener(item -> {
            page--;
            load.run();
        });
        next.setOnClickListener(item -> {
            page++;
            load.run();
        });
        load.run();
    }

    private void openFilters() {
        activity.task(() -> call(activity.api().tariffs()), tariffs -> {
            var fields = view.column();
            var query = view.field(fields, "Адрес, пассажир или код", filters.getOrDefault("q", ""), MainActivity.TEXT);
            var archive = view.select(fields, "Состояние", List.of(
                    new Views.Option<>("ACTIVE", "Активные"), new Views.Option<>("ARCHIVED", "Архив"),
                    new Views.Option<>("ALL", "Все")), filters.get("archive"));
            var status = view.select(fields, "Статус", labelChoices(TaxiText.STATUSES), filters.getOrDefault("status", ""));
            var availableTariffs = new ArrayList<Views.Option<String>>();
            availableTariffs.add(new Views.Option<>("", "Любой"));
            tariffs.forEach(tariff -> availableTariffs.add(new Views.Option<>(tariff.id(), tariff.name())));
            var tariff = view.select(fields, "Тариф", availableTariffs, filters.getOrDefault("tariffId", ""));
            var seat = view.select(fields, "Детское кресло", List.of(new Views.Option<>("", "Любое"),
                    new Views.Option<>("true", "Есть"), new Views.Option<>("false", "Нет")), filters.get("childSeat"));
            var priority = view.select(fields, "Приоритет", labelChoices(TaxiText.PRIORITIES), filters.get("priority"));
            var tag = view.field(fields, "Тег", filters.getOrDefault("tag", ""), MainActivity.TEXT);
            var priceOperator = view.select(fields, "Сравнение стоимости", List.of(new Views.Option<>("", "Без фильтра"),
                    new Views.Option<>("GE", "Не меньше"), new Views.Option<>("LE", "Не больше"),
                    new Views.Option<>("EQ", "Равно")), filters.get("fareOp"));
            var fare = view.field(fields, "Стоимость", filters.getOrDefault("fare", ""),
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            var sort = view.select(fields, "Сортировка", List.of(new Views.Option<>("pickupAt", "Дата подачи"),
                    new Views.Option<>("fare", "Стоимость"), new Views.Option<>("code", "Код"),
                    new Views.Option<>("updatedAt", "Дата изменения")), filters.get("sort"));
            var direction = view.select(fields, "Направление", List.of(new Views.Option<>("desc", "По убыванию"),
                    new Views.Option<>("asc", "По возрастанию")), filters.get("dir"));
            var dialog = view.form("Поиск и фильтры", fields);
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Сбросить", (window, which) -> {
                resetSelection();
                list();
            });
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Применить");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                if (priceOperator.value().isEmpty() != Views.value(fare).isEmpty()) {
                    fare.setError("Укажите стоимость и способ сравнения.");
                    return;
                }
                resetSelection();
                put("q", Views.value(query));
                put("archive", archive.value());
                put("status", status.value());
                put("tariffId", tariff.value());
                put("childSeat", seat.value());
                put("priority", priority.value());
                put("tag", Views.value(tag));
                put("fareOp", priceOperator.value());
                put("fare", Views.value(fare).replace(',', '.'));
                put("sort", sort.value());
                put("dir", direction.value());
                dialog.dismiss();
                list();
            });
        });
    }

    void detail(String id) {
        var root = activity.screen("Карточка заказа", this::list);
        var body = view.column();
        root.addView(view.scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
        activity.task(() -> new Detail(call(activity.api().ride(id)), call(activity.api().notes(id))),
                result -> fillDetail(body, result));
    }

    private void fillDetail(LinearLayout body, Detail detail) {
        Ride ride = detail.ride();
        var card = view.card();
        card.addView(view.text(ride.externalCode() + " · " + TaxiText.status(ride.status()), 20, Views.TEXT, true));
        card.addView(view.text(ride.pickupAddress() + " → " + ride.dropoffAddress(), 17, Views.TEXT, false));
        card.addView(view.text(ride.passengerName() + " · " + ride.tariffName(), 15, Views.MUTED, false));
        card.addView(view.text(TaxiText.time(ride.pickupAt()) + " МСК · " + TaxiText.amount(ride.fare()), 15, Views.TEXT, true));
        card.addView(view.text("Приоритет: " + TaxiText.PRIORITIES.get(ride.priority()) + " · Детское кресло: "
                + (ride.childSeat() ? "да" : "нет"), 14, Views.MUTED, false));
        card.addView(view.text("Теги: " + (ride.tags().isEmpty() ? "—" : String.join(", ", ride.tags())),
                14, Views.MUTED, false));
        if (ride.archived()) card.addView(view.text("Заказ в архиве", 14, Views.ERROR, true));
        if (activity.user().canEdit()) {
            if (!ride.archived()) {
                card.addView(view.primary("Изменить", () -> editor.open(ride, () -> detail(ride.id()))));
            }
            card.addView(view.button(ride.archived() ? "Восстановить" : "В архив", () ->
                    activity.task(() -> ride.archived() ? call(activity.api().restore(ride.id(), ride.version()))
                            : call(activity.api().archive(ride.id(), ride.version())),
                            changed -> detail(ride.id()))));
        }
        if (activity.user().admin() && ride.archived()) {
            card.addView(view.button("Удалить окончательно", () -> view.confirm("Удаление",
                    "Удалить заказ и его заметки?", () -> activity.task(
                            () -> call(activity.api().deleteRide(ride.id(), ride.version())), ignored -> list()))));
        }
        body.addView(card);

        var notes = view.card();
        notes.addView(view.text("Заметки", 19, Views.TEXT, true));
        if (activity.user().canEdit()) {
            notes.addView(view.button("Добавить заметку", () -> editor.note(ride.id(), null, () -> detail(ride.id()))));
        }
        if (detail.notes().isEmpty()) notes.addView(view.text("Заметок пока нет.", 14, Views.MUTED, false));
        for (var note : detail.notes()) {
            notes.addView(view.text((note.pinned() ? "Закреплена · " : "") + note.author() + " · "
                    + TaxiText.time(note.createdAt()) + " UTC", 13, Views.MUTED, true));
            notes.addView(view.text(note.body(), 15, Views.TEXT, false));
            if (activity.user().admin() || (activity.user().canEdit() && activity.user().id().equals(note.authorId()))) {
                var actions = view.row();
                actions.addView(view.button("Изменить", () -> editor.note(ride.id(), note, () -> detail(ride.id()))));
                actions.addView(view.button("Удалить", () -> view.confirm("Заметка", "Удалить заметку?", () ->
                        activity.task(() -> call(activity.api().deleteNote(ride.id(), note.id())),
                                ignored -> detail(ride.id())))));
                notes.addView(actions);
            }
        }
        body.addView(notes);
    }

    private void bulk() {
        if (selected.isEmpty()) {
            view.message("Массовая операция", "Выберите заказы в списке.");
            return;
        }
        var fields = view.column();
        var action = view.select(fields, "Действие", List.of(new Views.Option<>("ARCHIVE", "В архив"),
                new Views.Option<>("RESTORE", "Восстановить"), new Views.Option<>("STATUS", "Сменить статус"),
                new Views.Option<>("ADD_TAG", "Добавить тег"), new Views.Option<>("REMOVE_TAG", "Удалить тег")),
                "ARCHIVE");
        var status = view.select(fields, "Статус", labelChoices(TaxiText.STATUSES), "");
        var tag = view.field(fields, "Тег", "", MainActivity.TEXT);
        var dialog = view.form("Выбрано заказов: " + selected.size(), fields);
        dialog.show();
        var submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        submit.setOnClickListener(button -> {
            String op = action.value();
            if (op.equals("STATUS") && status.value().isEmpty()) {
                view.message("Массовая операция", "Выберите новый статус.");
                return;
            }
            if ((op.equals("ADD_TAG") || op.equals("REMOVE_TAG")) && Views.value(tag).isEmpty()) {
                tag.setError("Введите тег.");
                return;
            }
            submit.setEnabled(false);
            var input = new BulkInput(Set.copyOf(selected), op, blankToNull(status.value()), blankToNull(Views.value(tag)));
            activity.task(() -> call(activity.api().bulk(input)), result -> {
                dialog.dismiss();
                selected.clear();
                list();
                var message = new StringBuilder("Выполнено: " + result.succeeded().size()
                        + "\nОшибок: " + result.failures().size());
                for (var failure : result.failures()) message.append("\n").append(failure.id()).append(" — ").append(failure.error());
                view.message("Результат", message.toString());
            }, error -> {
                submit.setEnabled(true);
                activity.onFailure(error, null);
            });
        });
    }

    static List<Views.Option<String>> labelChoices(Map<String, String> labels) {
        var options = new ArrayList<Views.Option<String>>();
        options.add(new Views.Option<>("", "Любой"));
        labels.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry ->
                options.add(new Views.Option<>(entry.getKey(), entry.getValue())));
        return options;
    }

    private void put(String key, String value) {
        if (value != null && !value.isEmpty()) filters.put(key, value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private record Detail(Ride ride, List<Note> notes) {}

    private final class RideAdapter extends RecyclerView.Adapter<RideAdapter.Holder> {
        private final boolean editable;
        private final Runnable onSelection;
        private List<Ride> items = List.of();

        RideAdapter(boolean editable, Runnable onSelection) {
            this.editable = editable;
            this.onSelection = onSelection;
        }

        void setItems(List<Ride> fresh) {
            items = fresh;
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            var container = view.column();
            container.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new Holder(container);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            Ride ride = items.get(position);
            var card = view.card();
            card.addView(view.text(ride.externalCode() + " · " + TaxiText.status(ride.status()), 17, Views.TEXT, true));
            card.addView(view.text(ride.pickupAddress() + " → " + ride.dropoffAddress(), 15, Views.TEXT, false));
            card.addView(view.text(ride.passengerName() + " · " + TaxiText.time(ride.pickupAt()) + " · "
                    + TaxiText.amount(ride.fare()), 13, Views.MUTED, false));
            var actions = view.row();
            if (editable) {
                var mark = view.check(actions, "Выбрать", selected.contains(ride.id()));
                mark.setContentDescription("Выбрать " + ride.externalCode());
                mark.setOnCheckedChangeListener((widget, checked) -> {
                    if (checked) selected.add(ride.id());
                    else selected.remove(ride.id());
                    onSelection.run();
                });
            }
            actions.addView(view.button("Открыть", () -> detail(ride.id())));
            card.addView(actions);
            holder.container.removeAllViews();
            holder.container.addView(card);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private final class Holder extends RecyclerView.ViewHolder {
            private final LinearLayout container;

            private Holder(LinearLayout container) {
                super(container);
                this.container = container;
            }
        }
    }
}
