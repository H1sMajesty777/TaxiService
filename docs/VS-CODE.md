# VS Code для Java-проектов

Откройте корень репозитория командой `code .` или через File → Open Folder. Репозиторий включает настройки, задачи и конфигурации запуска в [каталоге .vscode](../.vscode).

На компьютере настроены JDK 21 и Maven 3.9.16 из пользовательского каталога `%LOCALAPPDATA%\MireaTaxiTools`. Конфигурация VS Code указывает на JDK для Java Language Server, JavaSE-21 для Maven-проектов, JDK для Gradle и полный путь к `mvn.cmd`. В интегрированном терминале VS Code JDK 21 и Maven имеют приоритет в PATH. Установлены Extension Pack for Java, Gradle for Java и Spring Boot Tools. Установленный Android SDK расположен в `%LOCALAPPDATA%\Android\Sdk`.

Если VS Code был открыт во время настройки, выполните через палитру `Developer: Reload Window`, затем откройте новый терминал. Проверьте версии:

```powershell
java -version
mvn -v
```

В палитре команд доступны:

- `Java: Configure Java Runtime` показывает JDK проектов.
- `Tasks: Run Task` открывает задачи «Такси: PostgreSQL», «Такси: JavaFX», «Такси: backend и PostgreSQL» и «Такси: Android тесты и APK».
- `Run and Debug` позволяет запускать КР 1, КР 2 и КР 3 с точками останова. Сначала поднимите PostgreSQL и создайте `.env`.

## Где ставить первые точки останова

| Проект | Место | Что посмотреть |
| --- | --- | --- |
| КР 1 | `RideOrderService.createRide` | Данные формы до проверки и после неё, возвращаемый ID |
| КР 2 | `DispatchController.refilter`, `RideForm.readRequest` | Предикат списка и DTO, построенный из полей |
| КР 3 | `RideService.update`, `AuthService.refresh` | Версию сущности, текущего пользователя, транзакцию и новый токен |
| КР 4 | `RideScreens.list`, `Gateway.retry` | Параметры страницы, сетевой вызов и повтор запроса |

Запускайте только один Spring Boot на порту 8081. Для отладки локального Java-процесса оставьте PostgreSQL в Docker, остановите backend командой `docker compose stop backend` и выберите конфигурацию КР 3. Для Android запуск и отладка на эмуляторе доступны также в Android Studio. VS Code собирает Android через Gradle Wrapper.

На другом компьютере установите JDK 21, Maven и рекомендованные расширения, затем выберите JDK через `Java: Configure Java Runtime`. Настройки с абсолютными путями сохранены в пользовательском профиле этого компьютера. Копия исходных настроек VS Code находится рядом с `settings.json` с расширением `.pre-mirea-java.bak`.

[Официальное руководство VS Code по Java-проектам](https://code.visualstudio.com/docs/java/java-project)
