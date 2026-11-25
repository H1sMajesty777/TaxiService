import {api, auth, renew, onExpired, useSession} from "./ui/api.js";
import {app, button, el, field, input, roles, run, submit, message, showForm} from "./ui/elements.js";
import {clearRideState, renderRides, renderDetail} from "./ui/rides.js";
import {accounts, analytics, audit, tariffs} from "./ui/directory.js";

let user = null;
let navigation = 0;
onExpired(() => loginScreen("Сессия истекла. Войдите заново."));

function loginScreen(initialMessage = "") {
    user = null;
    navigation++;
    clearRideState();
    const info = el("p", "error", initialMessage);
    info.setAttribute("role", "alert");
    const action = submit("Войти");
    const form = el("form", "",
        field("Логин", input("email", "", {type: "email", required: true, autocomplete: "username"})),
        field("Пароль", input("password", "", {type: "password", required: true, autocomplete: "current-password"})),
        info, action);
    form.onsubmit = event => {
        event.preventDefault();
        run(async () => {
            action.disabled = true;
            try {
                const body = Object.fromEntries(new FormData(form));
                const tokens = await auth("login", {...body, clientType: "WEB"});
                await enter(tokens.user);
            } finally {
                action.disabled = false;
            }
        }, info);
    };
    app.replaceChildren(el("main", "login-frame", el("section", "login-card",
        el("h1", "", "Служба такси"), form)));
}

async function enter(profile) {
    user = profile;
    if (user.mustChangePassword) {
        const info = el("p", "error");
        const form = passwordFields();
        const action = submit("Сменить пароль");
        const passwordForm = el("form", "", form, info, action, button("Выйти", logout));
        passwordForm.onsubmit = event => {
            event.preventDefault();
            run(async () => {
                action.disabled = true;
                try {
                    await changePassword(passwordForm, true);
                } finally {
                    action.disabled = false;
                }
            }, info);
        };
        app.replaceChildren(el("main", "login-frame", el("section", "login-card",
            el("h1", "", "Установите новый пароль"), el("p", "muted", "Учётная запись «" + user.email + "»"),
            passwordForm)));
        return;
    }
    await navigate("rides");
}

async function navigate(page, id) {
    if (!user || user.mustChangePassword) return;
    const revision = ++navigation;
    const views = [
        ["rides", "Заказы"], ["analytics", "Аналитика"], ["tariffs", "Тарифы"],
        ...(user.role === "ADMIN" ? [["users", "Пользователи"], ["audit", "Аудит"]] : [])
    ];
    const links = views.map(([view, caption]) => {
        const link = button(caption, () => navigate(view));
        if (view === page || (view === "rides" && page === "detail")) link.setAttribute("aria-current", "page");
        return link;
    });
    const info = el("p", "notice");
    info.id = "messages";
    info.setAttribute("role", "status");
    const pane = el("section", "stack", el("p", "loading", "Загрузка…"));
    app.replaceChildren(el("div", "shell",
        el("aside", "sidebar", el("p", "brand", "Служба такси"),
            el("p", "account", user.name, el("br"), roles[user.role]),
            el("nav", "nav", links, button("Сменить пароль", () =>
                showForm("Смена пароля", passwordFields(), form => changePassword(form, false))),
                button("Выйти", logout))),
        el("main", "main stack", info, pane)));
    const tasks = {
        rides: () => renderRides(pane, user, navigate),
        detail: () => renderDetail(pane, id, user, navigate),
        analytics: () => analytics(pane),
        tariffs: () => tariffs(pane, user, () => navigate("tariffs")),
        users: () => accounts(pane),
        audit: () => audit(pane)
    };
    await run(async () => {
        try {
            await tasks[page]();
        } catch (error) {
            if (revision === navigation) pane.replaceChildren();
            throw error;
        }
    }, info);
}

function passwordFields() {
    return el("div", "stack",
        field("Текущий пароль", input("currentPassword", "", {
            type: "password", required: true, autocomplete: "current-password"
        })),
        field("Новый пароль", input("newPassword", "", {
            type: "password", required: true, minLength: 12, maxLength: 72, autocomplete: "new-password"
        })));
}

async function changePassword(form, firstLogin) {
    const body = Object.fromEntries(new FormData(form));
    const tokens = await auth("password", {...body, clientType: "WEB"});
    user = tokens.user;
    if (firstLogin) await navigate("rides");
    message("Пароль изменён.");
}

async function logout() {
    try {
        await api("/api/auth/logout", {method: "POST", body: {clientType: "WEB"}});
    } finally {
        useSession(null);
        loginScreen();
    }
}

try {
    const tokens = await renew();
    await enter(tokens.user);
} catch {
    loginScreen();
}
