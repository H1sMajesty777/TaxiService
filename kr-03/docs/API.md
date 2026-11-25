# REST API диспетчерской

Локальный сервер `http://127.0.0.1:8081`. Ответы обычно возвращаются в JSON. Swagger доступен по `/swagger-ui.html`. Вход, обновление и выход находятся под `/api/auth`. Остальным методам нужен `Authorization: Bearer <accessToken>`, а изменения учитывают права текущего пользователя.

## Методы

| Метод и адрес | Задача | Параметры |
| --- | --- | --- |
| `POST /api/auth/login` | Вход | JSON `email`, `password`, `clientType` (`WEB` или `NATIVE`) |
| `POST /api/auth/refresh` | Обмен refresh-токена на новую пару | JSON `clientType`, для NATIVE также `refreshToken`. WEB отправляет cookie |
| `POST /api/auth/logout` | Отзыв refresh-сессии | Как у refresh |
| `GET /api/auth/me` | Профиль | Access-токен |
| `POST /api/auth/password` | Смена пароля | JSON `currentPassword`, `newPassword`, `clientType` |
| `GET /api/rides` | Страница заказов | Фильтры из таблицы ниже |
| `GET /api/rides/{id}` | Карточка | Публичный UUID |
| `POST /api/rides` | Создать | JSON заказа. Исходный статус `NEW` |
| `PUT /api/rides/{id}` | Изменить | JSON заказа и `version` из карточки |
| `DELETE /api/rides/{id}?version=n` | В архив | Текущая версия |
| `POST /api/rides/{id}/restore?version=n` | Восстановить | Текущая версия |
| `DELETE /api/rides/{id}/permanent?version=n` | Удалить окончательно | Архивная запись и права администратора |
| `POST /api/rides/bulk` | Массовое действие | JSON `ids`, `action`, для смены статуса — `status`, для тега — `tag` |
| `GET /api/rides/export` | Экспорт CSV | Те же фильтры и сортировка |
| `POST /api/rides/import/preview` | Предварительная проверка CSV | Multipart `file` и параметр `mode` |
| `POST /api/rides/import/apply` | Записать проверенный CSV | Тот же Multipart и режим |
| `GET, POST /api/rides/{id}/notes` | Список и создание заметок | Для POST JSON `body` и `pinned` |
| `PUT, DELETE /api/rides/{id}/notes/{noteId}` | Изменение и удаление заметки | Для PUT те же поля |
| `GET, POST /api/tariffs` | Тарифы | Для POST поля `name`, `description`, `active` |
| `PUT /api/tariffs/{id}` | Обновить тариф | Одно поле `active` управляет доступностью, тело содержит остальные данные тарифа |
| `GET /api/passengers` | Пассажиры для формы | ID и ФИО |
| `GET /api/analytics/summary` | Статистика | Сводка по всем данным |
| `GET, POST /api/admin/users` | Список и создание учётных записей | GET `page` и `size`. POST `profile` и `temporaryPassword` |
| `PUT /api/admin/users/{id}` | Изменить профиль | Поля `email`, `name`, `role`, `active` |
| `POST /api/admin/users/{id}/reset-password` | Назначить временный пароль | JSON `temporaryPassword` |
| `GET /api/admin/audit` | Журнал аудита | `q`, `page`, `size`, `sort` (`time`, `event`, `entity`), `dir` |

## Параметры выборки заказов

| Параметр | Пример и результат |
| --- | --- |
| `q` | `Киевский` в адресе, имени либо коде |
| `tariffId` | UUID тарифа |
| `status` | `CONFIRMED` |
| `childSeat` | `true` или `false` |
| `priority` | `LOW`, `NORMAL` или `HIGH` |
| `tag` | `вокзал` |
| `archive` | `ACTIVE` по умолчанию, `ARCHIVED` или `ALL` |
| `fareOp` и `fare` | `fareOp=GE&fare=600`, также поддерживаются `EQ` и `LE` |
| `sort` и `dir` | `sort=fare&dir=asc`. Поля `pickupAt`, `fare`, `code`, `updatedAt` |
| `page` и `size` | `page=0&size=10`. Максимальный размер 100 |

У ответа страницы поля `content`, `page`, `size`, `totalElements`, `totalPages`. `GET /api/rides/export` по тем же фильтрам возвращает CSV без деления на страницы.

## Примеры

Тело создания заказа с демонстрационным пассажиром и тарифом «Комфорт»:

```json
{
  "externalCode": "DEMO-WEB-01",
  "passengerId": 1,
  "tariffId": "00000000-0000-4000-a000-000000000002",
  "pickupAddress": "ул. Лесная, 5",
  "dropoffAddress": "Казанский вокзал",
  "pickupAt": "2026-12-01T09:30",
  "fare": 940.00,
  "status": "NEW",
  "childSeat": true,
  "tags": ["вокзал", "семья"],
  "version": null
}
```

В ответе появятся UUID `id`, числовой `number`, вычисленный `priority: HIGH` и `version: 0`. Для передачи версии `PUT` читает карточку по UUID. При обновлении с `version: 0` после уже сохранённой версии `1` ответ имеет вид:

```json
{
  "status": 409,
  "code": "CONFLICT",
  "message": "Данные изменились. Обновите карточку заказа.",
  "correlationId": "demo-request-42"
}
```

Если передать `X-Correlation-ID: demo-request-42` в запросе, то же значение будет в заголовке ответа и в событиях успешного изменения. Если заголовок отсутствует, сервер назначает ID. Коды основных отказов — 400 для неверного ввода, 401 для недействующей авторизации, 403 для недостатка полномочий, 404 для отсутствующей сущности и 409 для конфликтов.

Попробуйте [пример CSV](../examples/ride-import.csv). Загрузите его через «Импорт CSV» и выберите NEW_ONLY. После проверки одной корректной строки подтвердите применение. Найдите `DEMO-TRAIN-01` в списке. Повтор в NEW_ONLY сообщит о дубликате. В UPSERT изменение стоимости в CSV обновит запись по коду. Для массового запроса `action` может быть `ARCHIVE`, `RESTORE`, `STATUS`, `ADD_TAG` или `REMOVE_TAG`. Ответ отдельно перечисляет выполненные UUID и ошибки.

## Автоматическая проверка

Из корня проекта, после сборки Docker и запуска базы:

```powershell
docker compose exec -T postgres createdb -U taxi taxi_verification
docker compose run -d --no-deps --name mirea-taxi-verification-backend -p 127.0.0.1:18083:8081 -e TAXI_DB_URL=jdbc:postgresql://postgres:5432/taxi_verification backend
Invoke-RestMethod http://127.0.0.1:18083/actuator/health
.\scripts\Use-TaxiEnvironment.ps1
$env:TAXI_VERIFY_BASE_URL = 'http://127.0.0.1:18083'
$env:TAXI_VERIFY_PASSWORD = 'Local-QA-Example49'
node kr-03/scripts/verify-api.mjs
```

Скрипт предназначен для свежей отдельной тестовой базы и меняет её временный пароль администратора. Обычная база `taxi` остаётся доступной демонстрационному приложению. После прогона тестовый сервер можно остановить командой `docker stop mirea-taxi-verification-backend`.
