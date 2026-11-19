package dev.portfolio.roomreserve.common;

import dev.portfolio.roomreserve.room.exception.RoomNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RoomNotFoundException.class)
    public ProblemDetail handleRoomNotFound(RoomNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage()
        );
        problem.setTitle("Room not found");
        return problem;
    }
}
