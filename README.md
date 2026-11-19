# RoomReserve

Небольшой REST API для переговорных комнат. Это **стартовая итерация** проекта:
пока есть только каталог комнат, база данных и тесты. Система бронирования и
авторизация специально не реализованы — это следующие коммиты.

**Стек:** Java 21, Spring Boot 3.5, Spring MVC, Spring Data JPA,
Hibernate, PostgreSQL, Flyway, Docker Compose, Maven, JUnit 5, Mockito.

## Быстрый запуск

Нужен Docker с Docker Compose. Один способ поднять всё:

```bash
docker compose up --build
```

После запуска: `http://localhost:8080/api/rooms`.

Если хочешь запускать Java-приложение из IDE, отдельно подними только БД:

```bash
docker compose up -d db
mvn spring-boot:run
```

Нужны Java 21 и Maven 3.9+. Локальное подключение к БД по умолчанию:
`jdbc:postgresql://localhost:5432/roomreserve`, пользователь и пароль `roomreserve`.
Изменить можно через `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

## Пока доступно

```http
GET /api/rooms
GET /api/rooms/{id}
```

В миграциях есть три активные демонстрационные комнаты и одна неактивная.
Неактивная комната не отображается в выдаче. Если комната не найдена,
`GET /api/rooms/{id}` возвращает HTTP 404 (`ProblemDetail`).

Тесты (не требуют работающего PostgreSQL):

```bash
mvn test
```

План следующих четырёх коммитов — в [ROADMAP.md](ROADMAP.md).

> Значения паролей в `compose.yaml` предназначены только для локальной разработки.
