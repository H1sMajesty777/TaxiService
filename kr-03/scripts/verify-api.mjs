import assert from "node:assert/strict";

const base = process.env.TAXI_VERIFY_BASE_URL;
const initialPassword = process.env.TAXI_ADMIN_PASSWORD;
const adminEmail = process.env.TAXI_ADMIN_EMAIL || "admin@taxi.example";
const testPassword = process.env.TAXI_VERIFY_PASSWORD;
if (base !== "http://127.0.0.1:18083" || !initialPassword || !testPassword) {
    throw new Error("Используйте отдельную тестовую базу с backend на 127.0.0.1:18083 и задайте пароли через окружение.");
}
const mark = Date.now().toString(36);
const events = [];

async function request(path, {method = "GET", body, token, expect = 200} = {}) {
    const headers = {"X-Correlation-ID": "taxi-verification-" + mark};
    if (token) headers.Authorization = "Bearer " + token;
    if (body && !(body instanceof FormData)) {
        headers["Content-Type"] = "application/json";
        body = JSON.stringify(body);
    }
    const response = await fetch(base + path, {method, body, headers});
    const format = response.headers.get("content-type") || "";
    const data = response.status === 204 ? null : format.includes("json") ? await response.json() : await response.text();
    assert.equal(response.status, expect, method + " " + path + " · " + (response.ok ? "" : data?.message || ""));
    assert.equal(response.headers.get("x-correlation-id"), headers["X-Correlation-ID"]);
    return data;
}

async function login(email, password) {
    return request("/api/auth/login", {method: "POST", body: {email, password, clientType: "NATIVE"}});
}

async function changePassword(tokens, currentPassword, newPassword) {
    return request("/api/auth/password", {method: "POST", token: tokens.accessToken,
        body: {currentPassword, newPassword, clientType: "NATIVE"}});
}

const profile = (email, name, role, active = true) => ({email, name, role, active});
const token = tokens => tokens.accessToken;
const health = await request("/actuator/health");
assert.equal(health.status, "UP");
await request("/api/rides", {expect: 401});
let admin = await login(adminEmail, initialPassword);
assert.equal(admin.user.role, "ADMIN");
assert.equal(admin.user.mustChangePassword, true);
await request("/api/rides", {expect: 403, token: token(admin)});
const oldAdmin = admin;
admin = await changePassword(admin, initialPassword, testPassword);
await request("/api/auth/me", {expect: 401, token: token(oldAdmin)});
const rotated = await request("/api/auth/refresh", {method: "POST",
    body: {refreshToken: admin.refreshToken, clientType: "NATIVE"}});
await request("/api/auth/refresh", {method: "POST", expect: 401,
    body: {refreshToken: admin.refreshToken, clientType: "NATIVE"}});
admin = rotated;
events.push("вход, временный пароль, отзыв, вращение refresh");

const basic = await request("/api/tariffs", {token: token(admin)});
assert.equal(basic.length, 3);
const tariff = await request("/api/tariffs", {method: "POST", expect: 201, token: token(admin),
    body: {name: "Тест " + mark, description: "Тариф сквозной проверки", active: true}});
const off = await request("/api/tariffs/" + tariff.id, {method: "PUT", token: token(admin),
    body: {name: tariff.name, description: tariff.description, active: false}});
assert.equal(off.active, false);
await request("/api/tariffs/" + tariff.id, {method: "PUT", token: token(admin),
    body: {name: tariff.name, description: tariff.description, active: true}});
const person = (await request("/api/passengers", {token: token(admin)}))[0];

const dispatcherInfo = profile("dispatcher-" + mark + "@example.test", "Тестовый диспетчер", "DISPATCHER");
const viewerInfo = profile("viewer-" + mark + "@example.test", "Тестовый наблюдатель", "VIEWER");
for (const user of [dispatcherInfo, viewerInfo]) {
    const created = await request("/api/admin/users", {method: "POST", expect: 201, token: token(admin),
        body: {profile: user, temporaryPassword: initialPassword}});
    assert.equal(Object.hasOwn(created, "passwordHash"), false);
}
let dispatcher = await login(dispatcherInfo.email, initialPassword);
dispatcher = await changePassword(dispatcher, initialPassword, testPassword);
let viewer = await login(viewerInfo.email, initialPassword);
viewer = await changePassword(viewer, initialPassword, testPassword);
await request("/api/admin/users", {token: token(dispatcher), expect: 403});
await request("/api/admin/audit", {token: token(viewer), expect: 403});
events.push("справочник и разграничение ролей");

const route = {externalCode: "QA-" + mark, passengerId: person.id, tariffId: tariff.id,
    pickupAddress: "ул. Лесная, 5", dropoffAddress: "Казанский вокзал", pickupAt: "2026-12-01T14:30",
    fare: 930.50, status: "NEW", childSeat: true, tags: ["контроль"], version: null};
await request("/api/rides", {method: "POST", body: route, expect: 403, token: token(viewer)});
await request("/api/rides", {method: "POST", body: {...route, fare: 0}, expect: 400, token: token(dispatcher)});
let ride = await request("/api/rides", {method: "POST", body: route, expect: 201, token: token(dispatcher)});
assert.equal(ride.priority, "HIGH");
const ridePath = "/api/rides/" + ride.id;
assert.equal((await request(ridePath, {token: token(viewer)})).id, ride.id);
const revision = ride.version;
ride = await request(ridePath, {method: "PUT", token: token(dispatcher),
    body: {...route, version: revision, status: "CONFIRMED"}});
assert.ok(ride.version > revision);
await request(ridePath, {method: "PUT", token: token(dispatcher), expect: 409,
    body: {...route, version: revision}});
const filtered = await request("/api/rides?" + new URLSearchParams({
    q: "Лесная", tariffId: tariff.id, status: "CONFIRMED", childSeat: "true", priority: "HIGH",
    tag: "контроль", fareOp: "GE", fare: "900", size: "1", sort: "fare", dir: "asc"
}), {token: token(viewer)});
assert.equal(filtered.content[0].id, ride.id);
assert.equal(filtered.totalElements, 1);
await request("/api/rides?size=101", {token: token(viewer), expect: 400});
events.push("CRUD, конфликт версии, комбинированный фильтр, сортировка и пагинация");

let note = await request(ridePath + "/notes", {method: "POST", expect: 201, token: token(dispatcher),
    body: {body: "Пассажир с ребёнком", pinned: true}});
assert.equal(note.pinned, true);
note = await request(ridePath + "/notes/" + note.id, {method: "PUT", token: token(dispatcher),
    body: {body: "Нужно детское кресло", pinned: false}});
assert.equal(note.body, "Нужно детское кресло");
await request(ridePath + "/notes/" + note.id, {method: "DELETE", expect: 204, token: token(dispatcher)});
const bulk = await request("/api/rides/bulk", {method: "POST", token: token(dispatcher),
    body: {ids: [ride.id, "00000000-0000-4000-a000-000000000000"], action: "ADD_TAG", tag: "проверено"}});
assert.equal(bulk.succeeded.length, 1);
assert.equal(bulk.failures.length, 1);
ride = await request(ridePath, {token: token(admin)});
ride = await request(ridePath + "?version=" + ride.version, {method: "DELETE", token: token(dispatcher)});
assert.equal(ride.archived, true);
assert.equal((await request("/api/rides?archive=ARCHIVED&q=" + route.externalCode,
    {token: token(admin)})).content.length, 1);
ride = await request(ridePath + "/restore?version=" + ride.version, {method: "POST", token: token(dispatcher)});
assert.equal(ride.archived, false);
events.push("заметки, массовый частичный успех, архив и восстановление");

const sampleCode = "CSV-" + mark.toUpperCase();
function file(fare) {
    const form = new FormData();
    const rows = [
        "externalCode,passengerId,tariffId,pickupAddress,dropoffAddress,pickupAt,fare,status,childSeat,tags",
        [sampleCode, person.id, tariff.id, "Тестовая улица", "Тестовый вокзал", "2026-12-02T12:00",
            fare, "NEW", "false", "импорт"].join(",")
    ];
    form.append("file", new Blob([rows.join("\r\n")], {type: "text/csv"}), "verification.csv");
    return form;
}
const invalid = await request("/api/rides/import/preview", {method: "POST", token: token(dispatcher), body: file("bad")});
assert.equal(invalid.invalid, 1);
const preview = await request("/api/rides/import/preview", {method: "POST", token: token(dispatcher), body: file("600")});
assert.equal(preview.valid, 1);
const createdCsv = await request("/api/rides/import/apply", {method: "POST", token: token(dispatcher), body: file("600")});
assert.equal(createdCsv.inserted, 1);
const updatedCsv = await request("/api/rides/import/apply?mode=UPSERT",
    {method: "POST", token: token(dispatcher), body: file("750.50")});
assert.equal(updatedCsv.updated, 1);
const exported = await request("/api/rides/export?q=" + sampleCode, {token: token(viewer)});
assert.equal(exported.includes(sampleCode), true);
assert.equal(exported.match(/\r\n/g).length, 2);
const summary = await request("/api/analytics/summary", {token: token(viewer)});
assert.ok(summary.total >= 12);
const audit = await request("/api/admin/audit?q=CSV_IMPORTED", {token: token(admin)});
assert.ok(audit.content.some(row => row.correlationId === "taxi-verification-" + mark));
events.push("CSV preview, импорт и upsert, фильтр экспорта, аналитика и аудит");

ride = await request(ridePath + "?version=" + ride.version, {method: "DELETE", token: token(dispatcher)});
await request(ridePath + "/permanent?version=" + ride.version, {method: "DELETE", token: token(dispatcher), expect: 403});
await request(ridePath + "/permanent?version=" + ride.version, {method: "DELETE", token: token(admin), expect: 204});
await request(ridePath, {token: token(viewer), expect: 404});
await request("/api/admin/users/" + viewer.user.id, {method: "PUT",
    token: token(admin), body: {...viewerInfo, active: false}});
await request("/api/auth/me", {token: token(viewer), expect: 401});
await request("/api/auth/logout", {method: "POST", expect: 204,
    body: {clientType: "NATIVE", refreshToken: dispatcher.refreshToken}});
await request("/api/auth/refresh", {method: "POST", expect: 401,
    body: {clientType: "NATIVE", refreshToken: dispatcher.refreshToken}});
events.push("физическое удаление по роли, отключение аккаунта, выход");
console.log("PASS — " + events.join("; ") + ".");
