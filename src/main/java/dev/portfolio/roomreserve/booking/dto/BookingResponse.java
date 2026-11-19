package dev.portfolio.roomreserve.booking.dto;

import dev.portfolio.roomreserve.booking.Booking;
import dev.portfolio.roomreserve.booking.BookingStatus;

import java.time.Instant;

public record BookingResponse(
    Long id,
    Long roomId,
    String customerEmail,
    Instant startAt,
    Instant endAt,
    BookingStatus status,
    Instant createdAt
) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
            booking.getId(),
            booking.getRoom().getId(),
            booking.getCustomerEmail(),
            booking.getStartAt(),
            booking.getEndAt(),
            booking.getStatus(),
            booking.getCreatedAt()
        );
    }
}
