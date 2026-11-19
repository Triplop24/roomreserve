package dev.portfolio.roomreserve.room.dto;

import dev.portfolio.roomreserve.room.Room;

public record RoomResponse(Long id, String name, int capacity, String location) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getCapacity(),
                room.getLocation()
        );
    }
}
