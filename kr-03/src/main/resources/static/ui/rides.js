import {api, download} from "./api.js";
import {
    el, field, input, choice, check, button, submit, showForm, controlText, title, table, pagination,
    message, run, amount, dateTime, statuses, priorities, nextStatuses
} from "./elements.js";

const settings = {};
const selected = new Set();
const all = (label, values) => [["", label], ...values];
const archiveOptions = [["ACTIVE", "Активные"], ["ARCHIVED", "Архив"], ["ALL", "Все"]];
const filterKeys = ["q", "archive", "status", "tariffId", "childSeat", "priority", "tag", "fareOp", "fare", "sort", "dir"];
const uuidPath = id => "/api/rides/" + id;

export function clearRideState() {
    selected.clear();
    for (const key of filterKeys) delete settings[key];
}

export async function renderRides(root, user, goTo) {
    const canEdit = user.role !== "VIEWER";
    const tariffs = await api("/api/tariffs");
    const match = new URLSearchParams();
    const filters = el("form", "panel stack",
        el("div", "fields",
            field("Поиск", input("q", settings.q, {placeholder: "Адрес, код или пассажир"}), "wide"),
            field("Состояние", choice("archive", archiveOptions, settings.archive ?? "ACTIVE")),
            field("Статус", choice("status", all("Любой", Object.entries(statuses)), settings.status)),
            field("Тариф", choice("tariffId", all("Любой", tariffs.map(tariff => [tariff.id, tariff.name])),
                settings.tariffId)),
            field("Детское кресло", choice("childSeat", [["", "Любое"], ["true", "Есть"], ["false", "Нет"]],
                settings.childSeat)),
            field("Приоритет", choice("priority", all("Любой", Object.entries(priorities)), settings.priority)),
            field("Тег", input("tag", settings.tag)),
            field("Сравнение", choice("fareOp", [["", "Без ограничения"], ["GE", "Не меньше"], ["LE", "Не больше"],
                ["EQ", "Равно"]], settings.fareOp)),
            field("Стоимость, ₽", input("fare", settings.fare, {type: "number", min: "0", step: ".01"})),
            field("Сортировка", choice("sort", [["pickupAt", "Время подачи"], ["fare", "Стоимость"], ["code", "Код"],
                ["updatedAt", "Изменение"]], settings.sort ?? "pickupAt")),
            field("Порядок", choice("dir", [["desc", "По убыванию"], ["asc", "По возрастанию"]], settings.dir ?? "desc"))),
        el("div", "actions", submit("Применить"), button("Сбросить", () => {
            clearRideState();
            return goTo("rides");
        })));
    const results = el("section", "panel");
    let mostRecent = 0;
    const count = el("span", "muted");

    root.replaceChildren(
        title("Заказы",
            canEdit && button("Новый заказ", () => rideForm(null, tariffs, () => refresh(0)), "primary"),
            canEdit && button("Импорт CSV", () => importForm(() => refresh(0))),
            button("Экспорт CSV", () => download("/api/rides/export?" + match))),
        filters,
        canEdit && el("div", "actions", button("Массовая операция", () => bulkForm(() => refresh(0))), count),
        results);

    filters.addEventListener("submit", event => {
        event.preventDefault();
        selected.clear();
        run(() => refresh(0));
    });
    await refresh(0);

    async function refresh(page) {
        const ticket = ++mostRecent;
        for (const key of filterKeys) {
            const value = controlText(filters, key);
            settings[key] = value;
            if (value) match.set(key, value);
            else match.delete(key);
        }
        const query = new URLSearchParams(match);
        query.set("page", page);
        query.set("size", "10");
        const data = await api("/api/rides?" + query);
        if (ticket !== mostRecent) return;
        count.textContent = "Выбрано " + selected.size;
        const rows = data.content.map(ride => {
            const mark = input("mark", "", {type: "checkbox", checked: selected.has(ride.id)});
            mark.setAttribute("aria-label", "Выбрать " + ride.externalCode);
            mark.addEventListener("change", () => {
                if (mark.checked) selected.add(ride.id);
                else selected.delete(ride.id);
                count.textContent = "Выбрано " + selected.size;
            });
            const cells = [ride.externalCode, ride.passengerName, ride.pickupAddress + " → " + ride.dropoffAddress,
                dateTime(ride.pickupAt), amount(ride.fare), statuses[ride.status],
                button("Открыть", () => goTo("detail", ride.id))];
            return canEdit ? [mark, ...cells] : cells;
        });
        const columns = ["Код", "Пассажир", "Маршрут", "Подача · МСК", "Стоимость", "Статус", "Карточка"];
        results.replaceChildren(table(canEdit ? ["Выбор", ...columns] : columns, rows), pagination(data, refresh));
    }
}

export async function renderDetail(root, id, user, goTo) {
    const [ride, notes, tariffs] = await Promise.all([
        api(uuidPath(id)), api(uuidPath(id) + "/notes"), api("/api/tariffs")
    ]);
    const canEdit = user.role !== "VIEWER";
    const reopen = () => goTo("detail", id);
    const route = el("section", "panel stack", el("h2", "", ride.pickupAddress + " → " + ride.dropoffAddress),
        el("p", "muted", "Создан " + dateTime(ride.createdAt) + " UTC · Изменён " + dateTime(ride.updatedAt) + " UTC"),
        table(["Пассажир", "Тариф", "Подача · МСК", "Стоимость", "Статус", "Приоритет"], [
            [ride.passengerName, ride.tariffName, dateTime(ride.pickupAt), amount(ride.fare),
                statuses[ride.status], priorities[ride.priority]]
        ]),
        el("p", "", "Детское кресло: " + (ride.childSeat ? "да" : "нет")),
        el("p", "", "Теги: " + (ride.tags.join(", ") || "—")),
        ride.archived && el("p", "notice", "Заказ в архиве"));
    const controls = [button("К списку", () => goTo("rides"))];
    if (canEdit && !ride.archived) {
        controls.push(button("Изменить", () => rideForm(ride, tariffs, reopen), "primary"));
    }
    if (canEdit) {
        controls.push(button(ride.archived ? "Восстановить" : "В архив", async () => {
            const suffix = ride.archived ? "/restore" : "";
            await api(uuidPath(id) + suffix + "?version=" + ride.version,
                {method: ride.archived ? "POST" : "DELETE"});
            await reopen();
            message(ride.archived ? "Заказ восстановлен." : "Заказ перемещён в архив.");
        }));
    }
    if (user.role === "ADMIN" && ride.archived) {
        controls.push(button("Удалить окончательно", async () => {
            if (!window.confirm("Окончательно удалить " + ride.externalCode + " и его заметки?")) return;
            await api(uuidPath(id) + "/permanent?version=" + ride.version, {method: "DELETE"});
            await goTo("rides");
            message("Заказ удалён.");
        }, "danger"));
    }
    const notePanel = el("section", "panel stack", el("div", "titlebar", el("h2", "", "Заметки"),
        canEdit && button("Добавить заметку", () => noteForm(id, null, reopen))));
    if (notes.length === 0) notePanel.append(el("p", "muted", "Заметок пока нет."));
    for (const note of notes) {
        const canChange = user.role === "ADMIN" || (canEdit && user.id === note.authorId);
        notePanel.append(el("article", "panel stack",
            el("p", "muted", (note.pinned ? "Закреплена · " : "") + note.author + " · " + dateTime(note.createdAt) + " UTC"),
            el("p", "note", note.body),
            canChange && el("div", "actions", button("Изменить", () => noteForm(id, note, reopen)),
                button("Удалить", async () => {
                    if (!window.confirm("Удалить заметку?")) return;
                    await api(uuidPath(id) + "/notes/" + note.id, {method: "DELETE"});
                    await reopen();
                }, "danger"))));
    }
    root.replaceChildren(title("Заказ " + ride.externalCode, controls), route, notePanel);
}

async function rideForm(ride, tariffs, reload) {
    const passengers = await api("/api/passengers");
    const tariffOptions = tariffs.filter(tariff => tariff.active || tariff.id === ride?.tariffId)
        .map(tariff => [tariff.id, tariff.name + (tariff.active ? "" : " · отключён")]);
    const options = nextStatuses[ride?.status ?? "NEW"].filter(status => ride || status === "NEW");
    const fields = el("div", "fields",
        !ride && field("Код (можно оставить пустым)", input("externalCode", "", {maxLength: 40}), "wide"),
        field("Пассажир", choice("passengerId", passengers.map(p => [p.id, "#" + p.id + " · " + p.fullName]),
            ride?.passengerId), "wide"),
        field("Тариф", choice("tariffId", tariffOptions, ride?.tariffId)),
        field("Откуда", input("pickupAddress", ride?.pickupAddress, {required: true, minLength: 3, maxLength: 150})),
        field("Куда", input("dropoffAddress", ride?.dropoffAddress, {required: true, minLength: 3, maxLength: 150})),
        field("Подача · МСК", input("pickupAt", ride?.pickupAt?.slice(0, 16), {type: "datetime-local", required: true})),
        field("Стоимость, ₽", input("fare", ride?.fare, {type: "number", required: true, min: ".01", step: ".01"})),
        field("Статус", choice("status", options.map(value => [value, statuses[value]]), ride?.status)),
        field("Теги через запятую", input("tags", ride?.tags.join(", ")), "wide"),
        check("Детское кресло", "childSeat", ride?.childSeat));
    showForm(ride ? "Редактировать заказ" : "Новый заказ", fields, async form => {
        if (!controlText(form, "passengerId") || !controlText(form, "tariffId")) {
            throw new Error("Выберите пассажира и действующий тариф.");
        }
        const body = Object.fromEntries(new FormData(form));
        body.externalCode = body.externalCode?.trim() || null;
        body.passengerId = Number(body.passengerId);
        body.pickupAddress = body.pickupAddress.trim();
        body.dropoffAddress = body.dropoffAddress.trim();
        body.tags = body.tags.split(",").map(tag => tag.trim()).filter(Boolean);
        body.childSeat = form.elements.childSeat.checked;
        body.version = ride?.version ?? null;
        await api(ride ? uuidPath(ride.id) : "/api/rides", {method: ride ? "PUT" : "POST", body});
        await reload();
        message("Заказ сохранён.");
    });
}

function noteForm(rideId, note, reload) {
    const body = el("textarea");
    Object.assign(body, {name: "body", value: note?.body ?? "", required: true, maxLength: 1500});
    showForm(note ? "Изменить заметку" : "Новая заметка",
        el("div", "stack", field("Текст заметки", body), check("Закрепить", "pinned", note?.pinned)), async form => {
            await api(uuidPath(rideId) + "/notes" + (note ? "/" + note.id : ""), {
                method: note ? "PUT" : "POST", body: {
                    body: controlText(form, "body"), pinned: form.elements.pinned.checked
                }
            });
            await reload();
        });
}

function bulkForm(reload) {
    if (!selected.size) throw new Error("Выберите один или несколько заказов.");
    const fields = el("div", "stack",
        field("Действие", choice("action", [["ARCHIVE", "В архив"], ["RESTORE", "Восстановить"],
            ["STATUS", "Сменить статус"], ["ADD_TAG", "Добавить тег"], ["REMOVE_TAG", "Удалить тег"]])),
        field("Статус для смены", choice("status", Object.entries(statuses))),
        field("Тег для добавления или удаления", input("tag", "", {maxLength: 40})));
    showForm("Выбрано заказов: " + selected.size, fields, async form => {
        const body = Object.fromEntries(new FormData(form));
        const result = await api("/api/rides/bulk", {method: "POST", body: {...body, ids: [...selected]}});
        selected.clear();
        await reload();
        const issues = result.failures.map(item => item.id + " — " + item.error);
        message("Выполнено: " + result.succeeded.length + ". Ошибки: " + issues.length +
            (issues.length ? ". " + issues.join(" · ") : "."));
    });
}

function importForm(reload) {
    const file = input("file", "", {type: "file", accept: ".csv,text/csv", required: true});
    const mode = choice("mode", [["NEW_ONLY", "Только новые"], ["UPSERT", "Создать или обновить по коду"]]);
    const result = el("div", "stack");
    let checked = null;
    file.onchange = mode.onchange = () => { checked = null; result.replaceChildren(); };
    showForm("Импорт заказов", el("div", "stack", field("CSV-файл", file), field("Режим", mode), result),
        async () => {
            const payload = new FormData();
            payload.append("file", file.files[0]);
            const query = "?mode=" + mode.value;
            if (checked !== file.files[0]) {
                const preview = await api("/api/rides/import/preview" + query, {method: "POST", body: payload});
                result.replaceChildren(el("p", "notice", "Корректных: " + preview.valid + ", ошибок: " + preview.invalid),
                    ...preview.errors.map(item => el("p", "error", "Строка " + item.row + ": " + item.error)));
                if (preview.invalid) throw new Error("Исправьте ошибки и выберите исправленный файл.");
                checked = file.files[0];
                throw new Error("Проверка завершена. Нажмите кнопку ещё раз, чтобы применить импорт.");
            }
            const applied = await api("/api/rides/import/apply" + query, {method: "POST", body: payload});
            await reload();
            message("Создано " + applied.inserted + ", обновлено " + applied.updated + ".");
        }, "Проверить / применить");
}
