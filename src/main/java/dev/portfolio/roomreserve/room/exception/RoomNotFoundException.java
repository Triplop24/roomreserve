package dev.portfolio.roomreserve.room.exception;

public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(Long roomId) {
        super("Room with id " + roomId + " was not found");
    }
}
