export const app = document.querySelector("#app");
export const modal = document.querySelector("#modal");

export const statuses = {
    NEW: "Новый", CONFIRMED: "Подтверждён", IN_PROGRESS: "В поездке",
    COMPLETED: "Завершён", CANCELLED: "Отменён"
};
export const priorities = {HIGH: "Высокий", NORMAL: "Обычный", LOW: "Низкий"};
export const roles = {VIEWER: "Наблюдатель", DISPATCHER: "Диспетчер", ADMIN: "Администратор"};
export const nextStatuses = {
    NEW: ["NEW", "CONFIRMED", "CANCELLED"],
    CONFIRMED: ["CONFIRMED", "IN_PROGRESS", "CANCELLED"],
    IN_PROGRESS: ["IN_PROGRESS", "COMPLETED"],
    COMPLETED: ["COMPLETED"], CANCELLED: ["CANCELLED"]
};

export function el(tag, className = "", ...children) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    node.append(...children.flat(2).filter(child => child != null && child !== false));
    return node;
}

export function button(text, action, className = "") {
    const result = el("button", className, text);
    result.type = "button";
    result.addEventListener("click", () => run(action));
    return result;
}

export function submit(text) {
    const result = el("button", "primary", text);
    result.type = "submit";
    return result;
}

export function field(label, control, width = "") {
    return el("label", "field " + width, label, control);
}

export function input(name, value = "", properties = {}) {
    const node = document.createElement("input");
    Object.assign(node, {name, value, ...properties});
    return node;
}

export function choice(name, values, selected = "") {
    const node = document.createElement("select");
    node.name = name;
    for (const [key, label] of values) {
        node.add(new Option(label, key, false, key === selected));
    }
    return node;
}

export function check(label, name, checked = false) {
    return el("label", "check", input(name, "", {type: "checkbox", checked}), label);
}

export function controlText(form, name) {
    return form.elements[name].value.trim();
}

export function showForm(title, fields, save, submitLabel = "Сохранить") {
    const heading = el("h2", "", title);
    heading.id = "modal-title";
    const error = el("p", "error");
    error.setAttribute("role", "alert");
    const saveButton = submit(submitLabel);
    const form = el("form", "stack", heading, fields, error,
        el("div", "actions", saveButton, button("Отмена", () => modal.close())));
    modal.replaceChildren(form);
    form.addEventListener("submit", async event => {
        event.preventDefault();
        error.textContent = "";
        saveButton.disabled = true;
        try {
            await save(form);
            modal.close();
        } catch (failure) {
            error.textContent = failure.message;
        } finally {
            saveButton.disabled = false;
        }
    });
    modal.showModal();
}

export async function run(action, target = document.querySelector("#messages")) {
    try {
        await action();
    } catch (failure) {
        if (target) {
            target.className = "error";
            target.textContent = failure.message;
        } else {
            window.alert(failure.message);
        }
    }
}

export function message(text) {
    const area = document.querySelector("#messages");
    if (area) {
        area.className = "notice";
        area.textContent = text;
    }
}

export function title(name, ...controls) {
    return el("header", "titlebar", el("h1", "", name), el("div", "actions", controls));
}

export function table(headings, rows) {
    const data = rows.length ? rows.map(row => el("tr", "", row.map(value => el("td", "", value))))
        : [el("tr", "", Object.assign(el("td", "muted", "Записей нет"), {colSpan: headings.length}))];
    return el("div", "table-scroll", el("table", "",
        el("thead", "", el("tr", "", headings.map(value => el("th", "", value)))),
        el("tbody", "", data)));
}

export function pagination(data, onPage) {
    const back = button("Назад", () => onPage(data.page - 1));
    const next = button("Далее", () => onPage(data.page + 1));
    back.disabled = data.page < 1;
    next.disabled = data.page + 1 >= data.totalPages;
    return el("div", "paging",
        el("span", "muted", "Страница " + (data.page + 1) + " из " + Math.max(1, data.totalPages)
            + " · Записей " + data.totalElements), el("div", "actions", back, next));
}

export function dateTime(value) {
    return value ? value.slice(0, 16).replace("T", " ") : "—";
}

export function amount(value) {
    return new Intl.NumberFormat("ru-RU", {style: "currency", currency: "RUB"}).format(value);
}

export function uniqueId() {
    // На localhost доступен crypto.randomUUID; fallback нужен для локального HTTP на других адресах.
    return globalThis.crypto.randomUUID?.() || Array.from(crypto.getRandomValues(new Uint8Array(16)),
        byte => byte.toString(16).padStart(2, "0")).join("");
}
