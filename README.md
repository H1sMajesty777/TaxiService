# Служба такси

Учебный проект команды ЭФБО-09-24 по дисциплине «Программирование корпоративных систем». Вариант 34 РТУ МИРЭА. Основная сущность — заказ поездки.

| Работа | Приложение | Отчёт и материалы для защиты |
| --- | --- | --- |
| 1 | Консоль на Java, PostgreSQL и JDBC | [Запуск и сверка с заданием](kr-01/README.md) · [Модель, правила и разбор кода](kr-01/docs/README.md) |
| 2 | Настольное приложение на JavaFX, прямое JDBC-подключение | [Отчёт по JavaFX](kr-02/README.md) |
| 3 | Backend Spring Boot, REST API и веб-интерфейс | [Отчёт по серверу и веб-клиенту](kr-03/README.md) · [Справочник API](kr-03/docs/API.md) |
| 4 | Android-приложение на Java, REST-клиент | [Отчёт по Android](kr-04/README.md) |

[Общая архитектура и сквозной сценарий защиты](docs/ARCHITECTURE.md) · [Работа в VS Code](docs/VS-CODE.md) · [Комплект для сдачи](docs/SUBMISSION.md)

## Быстрый запуск на Windows

Откройте папку репозитория в VS Code. Для КР 1–3 понадобятся JDK 21, Maven 3.9+ и запущенный Docker Desktop. Для Android — Gradle Wrapper из проекта, Android SDK 35 и эмулятор. Из корня репозитория:

```powershell
.\scripts\Initialize-Local.ps1
docker compose up -d --build --wait
docker compose ps
```

Первый скрипт создаёт файл `.env` со случайным ключом JWT и временным паролем администратора. Существующий `.env` сохраняется. Веб-клиент доступен по адресу [127.0.0.1:8081](http://127.0.0.1:8081/), документация — [Swagger UI](http://127.0.0.1:8081/swagger-ui.html), состояние сервера — [health](http://127.0.0.1:8081/actuator/health). Для входа откройте локальный `.env` и возьмите `TAXI_ADMIN_EMAIL` и `TAXI_ADMIN_PASSWORD`. При первом входе приложение попросит выбрать новый пароль.

Если нужны только КР 1 и КР 2, достаточно `docker compose up -d --wait postgres`. JavaFX и консоль можно запускать отдельно при работающей базе.

```powershell
.\scripts\Use-TaxiEnvironment.ps1
mvn -q -f kr-01/pom.xml compile exec:java
```

В другом терминале:

```powershell
.\scripts\Use-TaxiEnvironment.ps1
mvn -q -pl kr-01 install
mvn -q -f kr-02/pom.xml javafx:run
```

Для Android при запущенном backend:

```powershell
cd kr-04
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK появится в `kr-04/app/build/outputs/apk/debug/app-debug.apk`. Эмулятор подключается к Windows по адресу `http://10.0.2.2:8081/`. В Android Studio откройте папку `kr-04`, запустите эмулятор API 35 и конфигурацию `app`. В VS Code доступны задачи сборки и запуска. Команда `docker compose down` в корне остановит сервер и PostgreSQL. Записи сохранятся в именованном томе.

## Условия работ

[КР № 1](https://online-edu.mirea.ru/mod/assign/view.php?id=933003) · [КР № 2](https://online-edu.mirea.ru/mod/assign/view.php?id=933004) · [КР № 3](https://online-edu.mirea.ru/mod/assign/view.php?id=942907) · [КР № 4](https://online-edu.mirea.ru/mod/assign/view.php?id=942913) · [Таблица вариантов](https://docs.google.com/spreadsheets/d/1QdwecvQTjZvihlrla9GPZ0ef8bX3uT5KtpK5e2TTAVQ/edit)
