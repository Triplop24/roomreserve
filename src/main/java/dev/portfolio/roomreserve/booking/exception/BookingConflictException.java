package dev.portfolio.roomreserve.booking.exception;

public class BookingConflictException extends RuntimeException {

    public BookingConflictException() {
        super("Room is already booked for this time interval");
    }
}
