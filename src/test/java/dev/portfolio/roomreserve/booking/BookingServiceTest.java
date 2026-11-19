
package dev.portfolio.roomreserve.booking;

import dev.portfolio.roomreserve.booking.dto.BookingResponse;
import dev.portfolio.roomreserve.booking.dto.CreateBookingRequest;
import dev.portfolio.roomreserve.booking.exception.BookingConflictException;
import dev.portfolio.roomreserve.booking.exception.BookingNotFoundException;
import dev.portfolio.roomreserve.booking.exception.InvalidBookingIntervalException;
import dev.portfolio.roomreserve.room.Room;
import dev.portfolio.roomreserve.room.RoomRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Instant START =
        Instant.parse("2026-12-05T10:00:00Z");

    private static final Instant END =
        Instant.parse("2026-12-05T11:00:00Z");

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void shouldCreateBooking() {
        Room room = createRoom();

        when(roomRepository.findByIdAndActiveTrue(1L))
            .thenReturn(Optional.of(room));

        when(bookingRepository.saveAndFlush(any(Booking.class)))
            .thenAnswer(invocation -> {
                Booking booking = invocation.getArgument(0);

                ReflectionTestUtils.setField(
                    booking, "id", 10L
                );

                return booking;
            });

        BookingResponse response = bookingService.create(
            createRequest()
        );

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.roomId()).isEqualTo(1L);
        assertThat(response.status())
            .isEqualTo(BookingStatus.CONFIRMED);

        assertThat(response.customerEmail())
            .isEqualTo("test@example.com");

        verify(bookingRepository)
            .saveAndFlush(any(Booking.class));
    }

    @Test
    void shouldRejectOverlappingBooking() {
        when(roomRepository.findByIdAndActiveTrue(1L))
            .thenReturn(Optional.of(createRoom()));

        when(bookingRepository.existsOverlappingBooking(
            1L,
            BookingStatus.CONFIRMED,
            START,
            END
        )).thenReturn(true);

        assertThatThrownBy(
            () -> bookingService.create(createRequest())
        ).isInstanceOf(BookingConflictException.class);

        verify(bookingRepository, never())
            .saveAndFlush(any(Booking.class));
    }

    @Test
    void shouldRejectInvalidInterval() {
        CreateBookingRequest request = new CreateBookingRequest(
            1L,
            "test@example.com",
            END,
            START
        );

        assertThatThrownBy(
            () -> bookingService.create(request)
        ).isInstanceOf(InvalidBookingIntervalException.class);

        verifyNoInteractions(
            bookingRepository,
            roomRepository
        );
    }

    @Test
    void shouldCancelBooking() {
        Booking booking = new Booking(
            createRoom(),
            "test@example.com",
            START,
            END
        );

        when(bookingRepository.findById(10L))
            .thenReturn(Optional.of(booking));

        bookingService.cancel(10L);

        assertThat(booking.getStatus())
            .isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void shouldThrowWhenBookingNotFound() {
        when(bookingRepository.findById(999L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> bookingService.getById(999L)
        ).isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void shouldHandlePostgresExclusionViolation() {
        when(roomRepository.findByIdAndActiveTrue(1L))
            .thenReturn(Optional.of(createRoom()));

        SQLException sqlException = new SQLException(
            "Conflicting booking",
            "23P01"
        );

        DataIntegrityViolationException dbException =
            new DataIntegrityViolationException(
                "Exclusion constraint violated",
                sqlException
            );

        when(bookingRepository.saveAndFlush(any(Booking.class)))
            .thenThrow(dbException);

        assertThatThrownBy(
            () -> bookingService.create(createRequest())
        ).isInstanceOf(BookingConflictException.class);

        verify(bookingRepository)
            .saveAndFlush(any(Booking.class));
    }

    @Test
    void shouldPropagateOtherDatabaseErrors() {
        when(roomRepository.findByIdAndActiveTrue(1L))
            .thenReturn(Optional.of(createRoom()));

        SQLException sqlException = new SQLException(
            "Unexpected database error",
            "23505"
        );

        DataIntegrityViolationException dbException =
            new DataIntegrityViolationException(
                "Database error",
                sqlException
            );

        when(bookingRepository.saveAndFlush(any(Booking.class)))
            .thenThrow(dbException);

        assertThatThrownBy(
            () -> bookingService.create(createRequest())
        ).isSameAs(dbException);
    }

    private CreateBookingRequest createRequest() {
        return new CreateBookingRequest(
            1L,
            " TEST@EXAMPLE.COM ",
            START,
            END
        );
    }

    private Room createRoom() {
        Room room = new Room(
            "Meeting Room",
            6,
            "Floor 1",
            true
        );

        ReflectionTestUtils.setField(
            room, "id", 1L
        );

        return room;
    }
}
