
# RoomReserve

REST API for managing meeting room reservations, built with Java 21,
Spring Boot and PostgreSQL.

The application supports JWT authentication, role-based access control,
room availability checks and conflict-free concurrent bookings.

## Tech Stack

- Java 21
- Spring Boot 3.5
- Spring MVC
- Spring Security (JWT, BCrypt)
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Flyway
- Docker / Docker Compose
- Maven
- JUnit 5 / Mockito
- Testcontainers

## Features

- Browse available meeting rooms
- Register and authenticate users
- Create and cancel room reservations
- View personal reservations
- Prevent overlapping reservations
- Handle concurrent booking requests safely
- Restrict access using USER and ADMIN roles
- Validate requests and return appropriate HTTP errors

## Architecture

The application follows a layered architecture:

Controller -> Service -> Repository -> PostgreSQL

Main modules:

- `auth` — registration, login and user management
- `security` — JWT configuration and access rules
- `room` — meeting room catalog
- `booking` — reservation management
- `common` — shared exception handling

Database schema changes are managed by Flyway.

See [Architecture](docs/architecture.md) for details.

## Getting Started

### Requirements

- Java 21
- Docker Desktop with Docker Compose
- Maven 3.9+ (or Maven support in IntelliJ IDEA)

### 1. Clone the repository

```bash
git clone <YOUR_REPOSITORY_URL>
cd roomreserve
```

Replace `<YOUR_REPOSITORY_URL>` with the actual GitHub URL.

### 2. Generate a JWT secret

The application requires a Base64-encoded secret of at least 32 bytes.

Example for PowerShell:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()

$env:APP_JWT_SECRET = [Convert]::ToBase64String($bytes)
```

Keep the secret private. Do not commit it to Git.

### 3. Start the application with Docker

```bash
docker compose up --build
```

Docker Compose starts PostgreSQL and the Spring Boot application.

The API is available at:

http://localhost:8080

### Alternative: Run from IntelliJ IDEA

Start only PostgreSQL:

```bash
docker compose up -d db
```

Configure the `APP_JWT_SECRET` environment variable in the
IntelliJ run configuration.

Run `RoomReserveApplication`.

The default local database connection is:

- Host: localhost
- Port: 5432
- Database: roomreserve
- Username: roomreserve
- Password: roomreserve

These credentials are intended for local development only.

## API Overview

| Method | Endpoint | Access |
|--------|----------|--------|
| GET | /api/rooms | Public |
| GET | /api/rooms/{id} | Public |
| POST | /api/auth/register | Public |
| POST | /api/auth/login | Public |
| POST | /api/bookings | Authenticated |
| GET | /api/bookings/my | Authenticated |
| GET | /api/bookings/{id} | Owner / ADMIN |
| DELETE | /api/bookings/{id} | Owner / ADMIN |
| GET | /api/bookings | ADMIN |

Protected endpoints require:

Authorization: Bearer <access_token>

See [API Documentation](docs/api.md) for request and response examples.

## Booking Conflict Prevention

A simple application-level availability check cannot guarantee
consistency under concurrent requests.

Two transactions may both observe that a room is available and then
attempt to create overlapping reservations.

RoomReserve solves this using a PostgreSQL exclusion constraint
with GiST indexing:

```sql
EXCLUDE USING gist (
    room_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
)
WHERE (status = 'CONFIRMED');
```

This ensures that two confirmed reservations for the same room
cannot overlap, including when requests are processed concurrently.

The application converts PostgreSQL exclusion violations
(SQLSTATE 23P01) into HTTP 409 Conflict responses.

Cancelled reservations do not block availability.

## Authentication and Authorization

Authentication uses signed JWT access tokens.

Passwords are hashed with BCrypt.

Available roles:

- USER: create reservations and manage personal bookings
- ADMIN: view and manage all reservations

New reservations are associated with the authenticated user
identified by the JWT subject.

The client cannot choose another booking owner.

## Testing

The project includes:

- Unit tests with JUnit 5 and Mockito
- REST controller tests with MockMvc
- Integration tests with Testcontainers
- PostgreSQL constraint tests
- Concurrent booking tests
- Authentication and authorization tests

Run all tests:

```bash
mvn test
```

Tests that use Testcontainers require a running Docker environment.

## Database Migrations

Flyway migrations are located in:

`src/main/resources/db/migration`

- V1 — Create rooms table
- V2 — Seed demonstration rooms
- V3 — Create bookings table
- V4 — Prevent overlapping bookings
- V5 — Create users and link bookings to users

## Project Status

The project implements its planned reservation, authentication
and concurrency-control functionality.

It is a portfolio backend project created for practicing
Java and Spring development.

## License

No license has been specified.
