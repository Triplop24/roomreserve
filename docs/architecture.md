
# RoomReserve Architecture

## Overview

RoomReserve is a REST API implemented as a modular monolith.

The application uses a layered architecture with Spring Boot,
Spring Data JPA and PostgreSQL.

## Component Structure

```text
                 HTTP Client
                      |
                      v
              Spring Security
                 JWT Filter
                      |
                      v
                Controllers
                      |
                      v
                  Services
                Business Logic
                      |
                      v
                Repositories
               Spring Data JPA
                      |
                      v
                 PostgreSQL
```

## Modules

### auth

Responsible for user registration and authentication.

Components:
- AppUser
- AppUserRepository
- AuthService
- AuthController
- UserRole

Passwords are stored as BCrypt hashes.

Successful authentication produces a signed JWT.

### security

Contains the Spring Security configuration.

Responsibilities:
- Stateless authentication
- JWT signature verification
- Token expiration validation
- Role extraction
- HTTP endpoint authorization

### room

Provides the meeting room catalog.

Only active rooms are available through the public API.

### booking

Implements reservation management.

Responsibilities:
- Validate reservation intervals
- Verify room availability
- Create reservations
- Retrieve reservations
- Cancel reservations
- Check booking ownership
- Handle concurrent reservation conflicts

### common

Contains shared HTTP exception handling.

## Database Schema

### rooms

| Column | Type |
|--------|------|
| id | BIGINT |
| name | VARCHAR |
| capacity | INTEGER |
| location | VARCHAR |
| is_active | BOOLEAN |

### app_users

| Column | Type |
|--------|------|
| id | BIGINT |
| email | VARCHAR |
| password_hash | VARCHAR |
| role | VARCHAR |
| created_at | TIMESTAMPTZ |

### bookings

| Column | Type |
|--------|------|
| id | BIGINT |
| room_id | BIGINT |
| user_id | BIGINT |
| customer_email | VARCHAR |
| start_at | TIMESTAMPTZ |
| end_at | TIMESTAMPTZ |
| status | VARCHAR |
| created_at | TIMESTAMPTZ |

Relationships:

- One room has many bookings
- One user has many bookings
- Every new booking has a registered owner

The nullable `user_id` column supports legacy bookings created
before authentication was introduced.

## Booking Lifecycle

A reservation has two possible statuses:

- CONFIRMED
- CANCELLED

New reservations are created with CONFIRMED status.

Cancellation changes the status to CANCELLED without deleting
the database record.

Cancelled reservations do not prevent new reservations
for the same time interval.

## Concurrent Booking Protection

Consider two requests for the same room and time interval.

Without database-level protection:

1. Transaction A checks availability.
2. Transaction B checks availability.
3. Both observe that the room is available.
4. Both attempt to create a reservation.
5. Two conflicting reservations may be created.

This is a race condition.

### Solution

The application uses a PostgreSQL exclusion constraint.

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
ADD CONSTRAINT no_overlapping_confirmed_bookings
EXCLUDE USING gist (
    room_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
)
WHERE (status = 'CONFIRMED');
```

The constraint prevents overlapping confirmed reservations
for the same room.

The `[)` interval includes its starting point but excludes
its ending point.

Therefore:

- 10:00–11:00 and 10:30–11:30 conflict.
- 10:00–11:00 and 11:00–12:00 do not conflict.

### Transaction Boundaries

Booking creation and cancellation use Spring's
`@Transactional` annotation.

During creation:

1. Validate the requested interval.
2. Resolve the authenticated user.
3. Find the active room.
4. Perform an application-level availability check.
5. Create the reservation.
6. Persist using `saveAndFlush()`.

The application-level check produces a convenient business
error for ordinary conflicts.

The PostgreSQL constraint provides the final consistency
guarantee under concurrent requests.

The service recognizes PostgreSQL SQLSTATE 23P01
and maps it to a booking conflict.

## Security Model

The API uses stateless JWT authentication.

JWT claims include:

- iss — token issuer
- sub — user ID
- iat — issued-at timestamp
- exp — expiration timestamp
- roles — user roles

JWTs are signed using HS256.

The application validates token signatures, issuer and expiration.

### Authorization

USER:
- Create reservations
- Read personal reservations
- Cancel personal reservations

ADMIN:
- Read all reservations
- Read reservations belonging to other users
- Cancel any reservation

The authenticated user ID comes from the validated JWT.

The client cannot specify the owner of a reservation.

## Testing Strategy

Unit tests verify business logic using Mockito.

Controller tests verify request validation and HTTP responses.

Integration tests use PostgreSQL through Testcontainers.

Concurrency tests verify that conflicting reservations
cannot both be confirmed.

Security integration tests verify:
- Registration
- Password hashing
- Login
- JWT validation
- Authentication requirements
- Booking ownership
- Administrative permissions
