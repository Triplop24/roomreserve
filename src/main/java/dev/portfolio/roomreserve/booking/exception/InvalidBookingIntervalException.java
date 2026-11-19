package dev.portfolio.roomreserve.booking.exception;

public class InvalidBookingIntervalException extends RuntimeException {

    public InvalidBookingIntervalException() {
        super("Start time must be earlier than end time");
    }
}
