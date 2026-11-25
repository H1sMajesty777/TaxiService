import {uniqueId} from "./elements.js";

let accessToken = null;
let refreshPending = null;
let expired = () => {};

export function onExpired(callback) {
    expired = callback;
}

export function useSession(tokens) {
    accessToken = tokens?.accessToken ?? null;
}

async function request(path, {method = "GET", body, token = accessToken} = {}) {
    const headers = {"X-Correlation-ID": uniqueId()};
    if (token) headers.Authorization = "Bearer " + token;
    if (body && !(body instanceof FormData)) {
        headers["Content-Type"] = "application/json";
        body = JSON.stringify(body);
    }
    return fetch(path, {method, headers, body, credentials: "same-origin"});
}

async function parse(response) {
    if (!response.ok) {
        const failure = await response.json().catch(() => null);
        throw new Error(failure?.message || "Ошибка сервера (" + response.status + ").");
    }
    return response.status === 204 ? null : response.json();
}

export async function renew() {
    if (!refreshPending) {
        refreshPending = request("/api/auth/refresh", {method: "POST", body: {clientType: "WEB"}, token: null})
            .then(parse).then(tokens => {
                useSession(tokens);
                return tokens;
            }).finally(() => { refreshPending = null; });
    }
    return refreshPending;
}

export async function auth(path, body) {
    const tokens = await parse(await request("/api/auth/" + path, {method: "POST", body}));
    useSession(tokens);
    return tokens;
}

async function authorized(path, options) {
    const sent = accessToken;
    let response = await request(path, options);
    if (response.status === 401) {
        try {
            if (!accessToken || accessToken === sent) await renew();
        } catch (error) {
            useSession(null);
            expired();
            throw error;
        }
        response = await request(path, options);
    }
    return response;
}

export async function api(path, options) {
    return parse(await authorized(path, options));
}

export async function download(path) {
    const response = await authorized(path);
    if (!response.ok) await parse(response);
    const url = URL.createObjectURL(await response.blob());
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "ride-orders.csv";
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1200);
}
