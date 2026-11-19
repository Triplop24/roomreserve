package dev.portfolio.roomreserve.common;

import dev.portfolio.roomreserve.booking.exception.BookingConflictException;
import dev.portfolio.roomreserve.booking.exception.BookingNotFoundException;
import dev.portfolio.roomreserve.booking.exception.InvalidBookingIntervalException;
import dev.portfolio.roomreserve.room.exception.RoomNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RoomNotFoundException.class)
    public ProblemDetail handleRoomNotFound(RoomNotFoundException ex) {
        return problem(
            HttpStatus.NOT_FOUND,
            "Room not found",
            ex.getMessage()
        );
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ProblemDetail handleBookingNotFound(BookingNotFoundException ex) {
        return problem(
            HttpStatus.NOT_FOUND,
            "Booking not found",
            ex.getMessage()
        );
    }

    @ExceptionHandler(BookingConflictException.class)
    public ProblemDetail handleBookingConflict(BookingConflictException ex) {
        return problem(
            HttpStatus.CONFLICT,
            "Booking conflict",
            ex.getMessage()
        );
    }

    @ExceptionHandler(InvalidBookingIntervalException.class)
    public ProblemDetail handleInvalidInterval(
        InvalidBookingIntervalException ex
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid booking interval",
            ex.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(
        MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(error -> error.getField() + ": "
                + error.getDefaultMessage())
            .orElse("Invalid request");

        return problem(
            HttpStatus.BAD_REQUEST,
            "Validation failed",
            message
        );
    }

    private ProblemDetail problem(
        HttpStatus status,
        String title,
        String detail
    ) {
        ProblemDetail problem =
            ProblemDetail.forStatusAndDetail(status, detail);

        problem.setTitle(title);
        return problem;
    }
}
