package dev.portfolio.roomreserve.room;

import dev.portfolio.roomreserve.room.dto.RoomResponse;
import dev.portfolio.roomreserve.room.exception.RoomNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<RoomResponse> getAllRooms() {
        return roomRepository.findAllByActiveTrueOrderByIdAsc()
                .stream()
                .map(RoomResponse::from)
                .toList();
    }

    public RoomResponse getRoomById(Long id) {
        return roomRepository.findByIdAndActiveTrue(id)
                .map(RoomResponse::from)
                .orElseThrow(() -> new RoomNotFoundException(id));
    }
}
