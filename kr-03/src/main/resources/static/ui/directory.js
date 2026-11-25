import {api} from "./api.js";
import {
    el, title, button, controlText, table, field, input, check, choice, showForm, pagination, submit,
    dateTime, amount, roles, statuses, priorities, run, message
} from "./elements.js";

export async function analytics(root) {
    const stats = await api("/api/analytics/summary");
    const cards = [
        ["Все заказы", stats.total], ["Активные", stats.active], ["В архиве", stats.archived],
        ["С детским креслом", stats.childSeat], ["Средняя стоимость", amount(stats.averageFare)]
    ];
    root.replaceChildren(title("Аналитика"),
        el("section", "summary", cards.map(([name, value]) =>
            el("div", "panel metric", el("span", "muted", name), el("strong", "", String(value))))),
        el("section", "summary",
            groups("По тарифу", stats.byTariff), groups("По статусу", stats.byStatus, statuses),
            groups("По приоритету", stats.byPriority, priorities)));
}

function groups(title, data, labels = {}) {
    const rows = Object.entries(data).sort(([left], [right]) => left.localeCompare(right, "ru"));
    return el("section", "panel stack", el("h2", "", title),
        rows.length ? rows.map(([key, value]) =>
            el("div", "group", el("span", "", labels[key] ?? key), el("strong", "", String(value))))
            : el("p", "muted", "Пока нет данных."));
}

export async function tariffs(root, user, reload) {
    const items = await api("/api/tariffs");
    const admin = user.role === "ADMIN";
    root.replaceChildren(title("Тарифы", admin && button("Новый тариф", () => tariffForm(null, reload), "primary")),
        el("section", "panel", table(admin ? ["Название", "Описание", "Доступность", "Действия"]
            : ["Название", "Описание", "Доступность"], items.map(tariff => {
            const row = [tariff.name, tariff.description, tariff.active ? "Доступен" : "Отключён"];
            if (admin) {
                row.push(el("div", "actions", button("Изменить", () => tariffForm(tariff, reload)),
                    button(tariff.active ? "Отключить" : "Включить", async () => {
                        await api("/api/tariffs/" + tariff.id, {
                            method: "PUT", body: {...tariff, active: !tariff.active}
                        });
                        await reload();
                        message("Доступность тарифа обновлена.");
                    })));
            }
            return row;
        }))));
}

function tariffForm(tariff, reload) {
    showForm(tariff ? "Изменить тариф" : "Новый тариф", el("div", "stack",
        field("Название", input("name", tariff?.name, {required: true, maxLength: 80})),
        field("Описание", input("description", tariff?.description, {required: true, maxLength: 300})),
        check("Доступен для новых заказов", "active", tariff?.active ?? true)), async form => {
        const body = Object.fromEntries(new FormData(form));
        body.active = form.elements.active.checked;
        await api("/api/tariffs" + (tariff ? "/" + tariff.id : ""), {method: tariff ? "PUT" : "POST", body});
        await reload();
        message("Тариф сохранён.");
    });
}

export async function accounts(root) {
    const results = el("section", "panel");
    root.replaceChildren(title("Учётные записи", button("Новый пользователь", () => accountForm(null, show), "primary")), results);
    await show(0);

    async function show(page = 0) {
        const data = await api("/api/admin/users?page=" + page + "&size=15");
        const rows = data.content.map(user => [user.name, user.email, roles[user.role],
            user.active ? "Активна" : "Отключена", user.mustChangePassword ? "Временный пароль" : "Пароль установлен",
            el("div", "actions", button("Изменить", () => accountForm(user, () => show(page))),
                button("Сброс пароля", () => resetPassword(user, () => show(page))))]);
        results.replaceChildren(table(["Имя", "Логин", "Роль", "Состояние", "Вход", "Действия"], rows),
            pagination(data, show));
    }
}

function accountForm(user, reload) {
    const fields = el("div", "stack",
        field("Имя", input("name", user?.name, {required: true, maxLength: 100})),
        field("Логин (email)", input("email", user?.email, {required: true, type: "email", maxLength: 120})),
        field("Роль", choice("role", Object.entries(roles), user?.role)),
        check("Учётная запись активна", "active", user?.active ?? true),
        !user && field("Временный пароль", input("password", "", {
            type: "password", minLength: 12, maxLength: 72, required: true, autocomplete: "new-password"
        })));
    showForm(user ? "Изменить учётную запись" : "Новая учётная запись", fields, async form => {
        const profile = {
            name: controlText(form, "name"), email: controlText(form, "email"),
            role: controlText(form, "role"), active: form.elements.active.checked
        };
        if (user) await api("/api/admin/users/" + user.id, {method: "PUT", body: profile});
        else await api("/api/admin/users", {
            method: "POST", body: {profile, temporaryPassword: form.elements.password.value}
        });
        await reload();
        message("Учётная запись сохранена.");
    });
}

function resetPassword(user, reload) {
    showForm("Временный пароль для " + user.email, field("Временный пароль", input("password", "", {
            type: "password", minLength: 12, maxLength: 72, required: true, autocomplete: "new-password"
        })), async form => {
        await api("/api/admin/users/" + user.id + "/reset-password",
            {method: "POST", body: {temporaryPassword: form.elements.password.value}});
        await reload();
        message("Временный пароль установлен. Действовавшие сессии отозваны.");
    });
}

export async function audit(root) {
    const controls = el("form", "panel stack",
        el("div", "fields",
            field("Поиск по событию, описанию или ID запроса", input("q", ""), "wide"),
            field("Сортировка", choice("sort", [["time", "Время"], ["event", "Событие"], ["entity", "Сущность"]])),
            field("Порядок", choice("dir", [["desc", "По убыванию"], ["asc", "По возрастанию"]]))),
        el("div", "actions", submit("Найти")));
    const results = el("section", "panel");
    root.replaceChildren(title("Журнал аудита"), controls, results);
    controls.onsubmit = event => {
        event.preventDefault();
        run(() => show(0));
    };
    await show(0);

    async function show(page) {
        const query = new URLSearchParams(new FormData(controls));
        query.set("page", page);
        const data = await api("/api/admin/audit?" + query);
        const rows = data.content.map(event => [dateTime(event.occurredAt), event.eventType, event.entityType,
            event.description, event.actorId ?? "—", event.correlationId]);
        results.replaceChildren(table(["Время UTC", "Событие", "Сущность", "Описание", "ID пользователя", "ID запроса"], rows),
            pagination(data, show));
    }
}
