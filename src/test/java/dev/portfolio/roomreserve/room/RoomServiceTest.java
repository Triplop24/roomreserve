package dev.portfolio.roomreserve.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.portfolio.roomreserve.room.dto.RoomResponse;
import dev.portfolio.roomreserve.room.exception.RoomNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private RoomService roomService;

    @Test
    void getAllRoomsReturnsActiveRoomsInRepositoryOrder() {
        Room atlas = room(1L, "Atlas", 4);
        Room orion = room(2L, "Orion", 8);
        when(roomRepository.findAllByActiveTrueOrderByIdAsc())
                .thenReturn(List.of(atlas, orion));

        List<RoomResponse> result = roomService.getAllRooms();

        assertThat(result).extracting(RoomResponse::id)
                .containsExactly(1L, 2L);
        assertThat(result).extracting(RoomResponse::name)
                .containsExactly("Atlas", "Orion");
    }

    @Test
    void getRoomByIdReturnsRoomWhenPresent() {
        when(roomRepository.findByIdAndActiveTrue(1L))
                .thenReturn(Optional.of(room(1L, "Atlas", 4)));

        RoomResponse result = roomService.getRoomById(1L);

        assertThat(result.name()).isEqualTo("Atlas");
        assertThat(result.capacity()).isEqualTo(4);
    }

    @Test
    void getRoomByIdThrowsWhenRoomIsMissingOrInactive() {
        when(roomRepository.findByIdAndActiveTrue(404L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoomById(404L))
                .isInstanceOf(RoomNotFoundException.class)
                .hasMessageContaining("404");
    }

    private Room room(Long id, String name, int capacity) {
        Room room = new Room(name, capacity, "Floor 1", true);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }
}
