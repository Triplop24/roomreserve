package dev.portfolio.roomreserve.room;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portfolio.roomreserve.common.ApiExceptionHandler;
import dev.portfolio.roomreserve.room.dto.RoomResponse;
import dev.portfolio.roomreserve.room.exception.RoomNotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    private RoomService roomService;

    @InjectMocks
    private RoomController roomController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(roomController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listReturnsRoomsAsJson() throws Exception {
        when(roomService.getAllRooms()).thenReturn(List.of(
                new RoomResponse(1L, "Atlas", 4, "Floor 1")
        ));

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Atlas"));
    }

    @Test
    void missingRoomReturns404ProblemDetail() throws Exception {
        when(roomService.getRoomById(999L))
                .thenThrow(new RoomNotFoundException(999L));

        mockMvc.perform(get("/api/rooms/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Room not found"))
                .andExpect(jsonPath("$.status").value(404));
    }
}
