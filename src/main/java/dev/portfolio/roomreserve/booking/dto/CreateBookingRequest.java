package dev.portfolio.roomreserve.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateBookingRequest(

    @NotNull
    Long roomId,

    @NotBlank
    @Email
    String customerEmail,

    @NotNull
    Instant startAt,

    @NotNull
    Instant endAt
) {
}
