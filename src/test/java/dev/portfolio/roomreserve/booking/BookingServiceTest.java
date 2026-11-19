package dev.portfolio.roomreserve.booking;

import dev.portfolio.roomreserve.booking.dto.*;
import dev.portfolio.roomreserve.booking.exception.*;
import dev.portfolio.roomreserve.room.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

        when(bookingRepository.save(any(Booking.class)))
            .thenAnswer(invocation -> {
                Booking booking = invocation.getArgument(0);
                ReflectionTestUtils.setField(
                    booking, "id", 10L
                );
                return booking;
            });

        CreateBookingRequest request = new CreateBookingRequest(
            1L, " TEST@EXAMPLE.COM ", START, END
        );

        BookingResponse response = bookingService.create(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.roomId()).isEqualTo(1L);
        assertThat(response.status())
            .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.customerEmail())
            .isEqualTo("test@example.com");

        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void shouldRejectOverlappingBooking() {
        when(roomRepository.findByIdAndActiveTrue(1L))
            .thenReturn(Optional.of(createRoom()));

        when(bookingRepository.existsOverlappingBooking(
            1L, BookingStatus.CONFIRMED, START, END
        )).thenReturn(true);

        CreateBookingRequest request = new CreateBookingRequest(
            1L, "test@example.com", START, END
        );

        assertThatThrownBy(() -> bookingService.create(request))
            .isInstanceOf(BookingConflictException.class);

        verify(bookingRepository, never())
            .save(any(Booking.class));
    }

    @Test
    void shouldRejectInvalidInterval() {
        CreateBookingRequest request = new CreateBookingRequest(
            1L, "test@example.com", END, START
        );

        assertThatThrownBy(() -> bookingService.create(request))
            .isInstanceOf(InvalidBookingIntervalException.class);

        verifyNoInteractions(bookingRepository, roomRepository);
    }

    @Test
    void shouldCancelBooking() {
        Booking booking = new Booking(
            createRoom(), "test@example.com", START, END
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

        assertThatThrownBy(() -> bookingService.getById(999L))
            .isInstanceOf(BookingNotFoundException.class);
    }

    private Room createRoom() {
        Room room = new Room("Meeting Room", 6, "Floor 1", true);
        ReflectionTestUtils.setField(room, "id", 1L);
        return room;
    }
}
