
# RoomReserve REST API

Base URL:

`http://localhost:8080`

All request and response bodies use JSON.

Protected endpoints require an access token:

```http
Authorization: Bearer <access_token>
```

## Authentication

### Register

`POST /api/auth/register`

Request:

```json
{
  "email": "user@example.com",
  "password": "TestPassword123!"
}
```

Response: `201 Created`

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

New accounts receive the USER role.

Possible errors:
- 400 — Invalid request
- 409 — Email already registered

### Login

`POST /api/auth/login`

Request:

```json
{
  "email": "user@example.com",
  "password": "TestPassword123!"
}
```

Response: `200 OK`

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Possible errors:
- 400 — Invalid request
- 401 — Invalid credentials

## Rooms

### List active rooms

`GET /api/rooms`

Authentication: Not required.

Response: `200 OK`

Returns an array of active rooms.

### Get room by ID

`GET /api/rooms/{id}`

Authentication: Not required.

Response: `200 OK`

Returns the selected active room.

Possible errors:
- 404 — Room not found

## Bookings

### Create booking

`POST /api/bookings`

Authentication: Required.

Request:

```json
{
  "roomId": 1,
  "startAt": "2030-05-01T10:00:00Z",
  "endAt": "2030-05-01T11:00:00Z"
}
```

Response: `201 Created`

```json
{
  "id": 10,
  "roomId": 1,
  "customerEmail": "user@example.com",
  "startAt": "2030-05-01T10:00:00Z",
  "endAt": "2030-05-01T11:00:00Z",
  "status": "CONFIRMED",
  "createdAt": "2030-04-20T12:00:00Z"
}
```

The owner and email are determined from the
authenticated user, not from the request body.

Possible errors:
- 400 — Invalid interval or request body
- 401 — Missing or invalid access token
- 404 — Room not found
- 409 — Room already booked for the requested interval

### List personal bookings

`GET /api/bookings/my`

Authentication: Required.

Response: `200 OK`

Returns an array of bookings owned by the
authenticated user, ordered by creation time descending.

### Get booking by ID

`GET /api/bookings/{id}`

Authentication: Required.

Access: Owner or ADMIN.

Response: `200 OK`

Possible errors:
- 401 — Authentication required
- 403 — User does not own the booking
- 404 — Booking not found

### Cancel booking

`DELETE /api/bookings/{id}`

Authentication: Required.

Access: Owner or ADMIN.

Response: `204 No Content`

The reservation is not deleted.

Its status changes to CANCELLED.

Possible errors:
- 401 — Authentication required
- 403 — User does not own the booking
- 404 — Booking not found

### List all bookings

`GET /api/bookings`

Authentication: Required.

Access: ADMIN only.

Response: `200 OK`

Returns all bookings ordered by creation time descending.

Possible errors:
- 401 — Authentication required
- 403 — Administrator role required

## Booking Rules

The following conditions apply:

1. The room must exist and be active.
2. The start time must be earlier than the end time.
3. Only CONFIRMED bookings block availability.
4. Overlapping confirmed bookings are prohibited.
5. Adjacent intervals are allowed.
6. Reservations belong to authenticated users.

Date-time values must include timezone information.

Example:

`2030-05-01T10:00:00Z`

## HTTP Status Codes

| Status | Meaning |
|--------|---------|
| 200 | Successful request |
| 201 | Resource created |
| 204 | Successful cancellation |
| 400 | Invalid request |
| 401 | Authentication required or failed |
| 403 | Insufficient permissions |
| 404 | Resource not found |
| 409 | Booking conflict or duplicate registration |

## Example Workflow

1. Register a user.
2. Obtain an access token.
3. List available rooms.
4. Create a booking using the token.
5. List personal bookings.
6. Cancel the booking.
7. Confirm its status is CANCELLED.

Access tokens expire after 3600 seconds.

After expiration, log in again to obtain a new token.
