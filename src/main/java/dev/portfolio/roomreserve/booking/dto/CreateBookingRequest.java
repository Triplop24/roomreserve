package dev.portfolio.roomreserve.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record CreateBookingRequest(

    @NotNull
    @Positive
    Long roomId,

    @NotNull
    Instant startAt,

    @NotNull
    Instant endAt

) {
}
