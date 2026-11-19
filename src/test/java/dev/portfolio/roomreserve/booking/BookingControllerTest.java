package dev.portfolio.roomreserve.booking;

import dev.portfolio.roomreserve.booking.dto.BookingResponse;
import dev.portfolio.roomreserve.booking.dto.CreateBookingRequest;
import dev.portfolio.roomreserve.booking.exception.BookingConflictException;
import dev.portfolio.roomreserve.common.ApiExceptionHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
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
              "startAt": "2030-01-15T10:00:00Z",
              "endAt": "2030-01-15T11:00:00Z"
            }
            """;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                new BookingController(bookingService)
            )
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver()
            )
            .setControllerAdvice(
                new ApiExceptionHandler()
            )
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateBooking() throws Exception {
        authenticateAs(1L);

        when(bookingService.create(
            any(CreateBookingRequest.class),
            eq(1L)
        )).thenReturn(createResponse());

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                "/api/bookings/10"
            ))
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.status")
                .value("CONFIRMED"));

        verify(bookingService).create(
            any(CreateBookingRequest.class),
            eq(1L)
        );
    }

    @Test
    void shouldReturn409ForBookingConflict() throws Exception {
        authenticateAs(1L);

        when(bookingService.create(
            any(CreateBookingRequest.class),
            eq(1L)
        )).thenThrow(new BookingConflictException());

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400ForInvalidRequest() throws Exception {
        authenticateAs(1L);

        String invalidRequest = """
                {
                  "roomId": -1,
                  "startAt": "2030-01-15T10:00:00Z",
                  "endAt": "2030-01-15T11:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    void shouldReturnBookingById() throws Exception {
        authenticateAs(1L);

        when(bookingService.getById(10L, 1L))
            .thenReturn(createResponse());

        mockMvc.perform(get("/api/bookings/10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10));

        verify(bookingService).getById(10L, 1L);
    }

    @Test
    void shouldReturnCurrentUsersBookings() throws Exception {
        authenticateAs(1L);

        when(bookingService.getMyBookings(1L))
            .thenReturn(List.of(createResponse()));

        mockMvc.perform(get("/api/bookings/my"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void shouldCancelBooking() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(delete("/api/bookings/10"))
            .andExpect(status().isNoContent());

        verify(bookingService).cancel(10L, 1L);
    }

    @Test
    void shouldRejectRequestWithoutAuthentication() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/bookings/10"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(bookingService);
    }

    private void authenticateAs(Long userId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject(userId.toString())
            .claim("roles", List.of("USER"))
            .build();

        JwtAuthenticationToken authentication =
            new JwtAuthenticationToken(jwt);

        SecurityContext context =
            SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private BookingResponse createResponse() {
        return new BookingResponse(
            10L,
            1L,
            "user1@example.com",
            Instant.parse("2030-01-15T10:00:00Z"),
            Instant.parse("2030-01-15T11:00:00Z"),
            BookingStatus.CONFIRMED,
            Instant.parse("2026-11-19T12:00:00Z")
        );
    }
}
