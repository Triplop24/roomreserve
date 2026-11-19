package dev.portfolio.roomreserve.booking;

import dev.portfolio.roomreserve.booking.dto.*;
import dev.portfolio.roomreserve.booking.exception.*;
import dev.portfolio.roomreserve.common.ApiExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    private MockMvc mockMvc;

    private static final String VALID_REQUEST = """
            {
              "roomId": 1,
              "customerEmail": "test@example.com",
              "startAt": "2026-12-05T10:00:00Z",
              "endAt": "2026-12-05T11:00:00Z"
            }
            """;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new BookingController(bookingService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void shouldReturn201WhenBookingCreated() throws Exception {
        BookingResponse response = createResponse();

        when(bookingService.create(any(CreateBookingRequest.class)))
            .thenReturn(response);

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location", "/api/bookings/10"
            ))
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldReturn409WhenRoomAlreadyBooked() throws Exception {
        when(bookingService.create(any(CreateBookingRequest.class)))
            .thenThrow(new BookingConflictException());

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400ForInvalidEmail() throws Exception {
        String request = VALID_REQUEST.replace(
            "test@example.com", "invalid-email"
        );

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    void shouldReturn200WhenBookingExists() throws Exception {
        when(bookingService.getById(10L))
            .thenReturn(createResponse());

        mockMvc.perform(get("/api/bookings/10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void shouldReturn204WhenBookingCancelled() throws Exception {
        mockMvc.perform(delete("/api/bookings/10"))
            .andExpect(status().isNoContent());

        verify(bookingService).cancel(10L);
    }

    private BookingResponse createResponse() {
        return new BookingResponse(
            10L,
            1L,
            "test@example.com",
            Instant.parse("2026-12-05T10:00:00Z"),
            Instant.parse("2026-12-05T11:00:00Z"),
            BookingStatus.CONFIRMED,
            Instant.parse("2026-11-19T12:00:00Z")
        );
    }
}
