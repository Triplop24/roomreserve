package dev.portfolio.roomreserve.booking;

import dev.portfolio.roomreserve.auth.AppUser;
import dev.portfolio.roomreserve.auth.AppUserRepository;
import dev.portfolio.roomreserve.auth.UserRole;
import dev.portfolio.roomreserve.booking.dto.BookingResponse;
import dev.portfolio.roomreserve.booking.dto.CreateBookingRequest;
import dev.portfolio.roomreserve.booking.exception.BookingConflictException;
import dev.portfolio.roomreserve.booking.exception.BookingNotFoundException;
import dev.portfolio.roomreserve.booking.exception.InvalidBookingIntervalException;
import dev.portfolio.roomreserve.room.Room;
import dev.portfolio.roomreserve.room.RoomRepository;
import dev.portfolio.roomreserve.room.exception.RoomNotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ADMIN_ID = 3L;

    private static final Instant START =
        Instant.parse("2030-01-15T10:00:00Z");

    private static final Instant END =
        Instant.parse("2030-01-15T11:00:00Z");

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void shouldCreateBookingForAuthenticatedUser() {
        AppUser user = createUser(USER_ID, UserRole.USER);

        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(user));

        when(roomRepository.findByIdAndActiveTrue(1L))
            .thenReturn(Optional.of(createRoom()));

        when(bookingRepository.saveAndFlush(any(Booking.class)))
            .thenAnswer(invocation -> {
                Booking booking = invocation.getArgument(0);
                ReflectionTestUtils.setField(
                    booking, "id", 10L
                );
                return booking;
            });

        BookingResponse response =
            bookingService.create(createRequest(), USER_ID);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.roomId()).isEqualTo(1L);
        assertThat(response.status())
            .isEqualTo(BookingStatus.CONFIRMED);

        ArgumentCaptor<Booking> captor =
            ArgumentCaptor.forClass(Booking.class);

        verify(bookingRepository).saveAndFlush(captor.capture());

        Booking saved = captor.getValue();

        assertThat(saved.getUser().getId()).isEqualTo(USER_ID);
        assertThat(saved.getCustomerEmail())
            .isEqualTo(user.getEmail());
    }

    @Test
    void shouldRejectInvalidInterval() {
        CreateBookingRequest request =
            new CreateBookingRequest(1L, END, START);

        assertThatThrownBy(
            () -> bookingService.create(request, USER_ID)
        ).isInstanceOf(InvalidBookingIntervalException.class);

        verifyNoInteractions(
            bookingRepository,
            roomRepository,
            userRepository
        );
    }

    @Test
    void shouldRejectOverlappingBooking() {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(
                createUser(USER_ID, UserRole.USER)
            ));

        when(roomRepository.findByIdAndActiveTrue(1L))
            .thenReturn(Optional.of(createRoom()));

        when(bookingRepository.existsOverlappingBooking(
            1L, BookingStatus.CONFIRMED, START, END
        )).thenReturn(true);

        assertThatThrownBy(
            () -> bookingService.create(createRequest(), USER_ID)
        ).isInstanceOf(BookingConflictException.class);

        verify(bookingRepository, never())
            .saveAndFlush(any(Booking.class));
    }

    @Test
    void shouldHandlePostgresExclusionViolation() {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(
                createUser(USER_ID, UserRole.USER)
            ));

        when(roomRepository.findByIdAndActiveTrue(1L))
            .thenReturn(Optional.of(createRoom()));

        SQLException sqlException =
            new SQLException("Exclusion violation", "23P01");

        DataIntegrityViolationException exception =
            new DataIntegrityViolationException(
                "Database constraint violation",
                sqlException
            );

        when(bookingRepository.saveAndFlush(any(Booking.class)))
            .thenThrow(exception);

        assertThatThrownBy(
            () -> bookingService.create(createRequest(), USER_ID)
        ).isInstanceOf(BookingConflictException.class);
    }

    @Test
    void shouldPropagateOtherDatabaseErrors() {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(
                createUser(USER_ID, UserRole.USER)
            ));

        when(roomRepository.findByIdAndActiveTrue(1L))
            .thenReturn(Optional.of(createRoom()));

        DataIntegrityViolationException exception =
            new DataIntegrityViolationException(
                "Other database error",
                new SQLException("Unique violation", "23505")
            );

        when(bookingRepository.saveAndFlush(any(Booking.class)))
            .thenThrow(exception);

        assertThatThrownBy(
            () -> bookingService.create(createRequest(), USER_ID)
        ).isSameAs(exception);
    }

    @Test
    void shouldRejectInactiveOrMissingRoom() {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(
                createUser(USER_ID, UserRole.USER)
            ));

        when(roomRepository.findByIdAndActiveTrue(1L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> bookingService.create(createRequest(), USER_ID)
        ).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void shouldRejectMissingAuthenticatedUser() {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.empty());

        ResponseStatusException exception =
            catchThrowableOfType(
                () -> bookingService.create(
                    createRequest(), USER_ID
                ),
                ResponseStatusException.class
            );

        assertThat(exception.getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAllowOwnerToReadBooking() {
        AppUser user = createUser(USER_ID, UserRole.USER);
        Booking booking = createBooking(user);

        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(user));

        when(bookingRepository.findById(10L))
            .thenReturn(Optional.of(booking));

        BookingResponse response =
            bookingService.getById(10L, USER_ID);

        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    void shouldRejectAccessToAnotherUsersBooking() {
        AppUser currentUser = createUser(USER_ID, UserRole.USER);
        AppUser owner = createUser(OTHER_USER_ID, UserRole.USER);

        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(currentUser));

        when(bookingRepository.findById(10L))
            .thenReturn(Optional.of(createBooking(owner)));

        ResponseStatusException exception =
            catchThrowableOfType(
                () -> bookingService.getById(10L, USER_ID),
                ResponseStatusException.class
            );

        assertThat(exception.getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldAllowAdminToReadAnyBooking() {
        AppUser admin = createUser(ADMIN_ID, UserRole.ADMIN);
        AppUser owner = createUser(OTHER_USER_ID, UserRole.USER);

        when(userRepository.findById(ADMIN_ID))
            .thenReturn(Optional.of(admin));

        when(bookingRepository.findById(10L))
            .thenReturn(Optional.of(createBooking(owner)));

        BookingResponse response =
            bookingService.getById(10L, ADMIN_ID);

        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    void shouldReturnOnlyCurrentUsersBookings() {
        AppUser user = createUser(USER_ID, UserRole.USER);

        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(user));

        when(bookingRepository.findByUser_IdOrderByCreatedAtDesc(
            USER_ID
        )).thenReturn(List.of(createBooking(user)));

        List<BookingResponse> result =
            bookingService.getMyBookings(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(10L);

        verify(bookingRepository)
            .findByUser_IdOrderByCreatedAtDesc(USER_ID);
    }

    @Test
    void shouldRejectAllBookingsForRegularUser() {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(
                createUser(USER_ID, UserRole.USER)
            ));

        ResponseStatusException exception =
            catchThrowableOfType(
                () -> bookingService.getAllBookings(USER_ID),
                ResponseStatusException.class
            );

        assertThat(exception.getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldAllowAdminToReadAllBookings() {
        AppUser admin = createUser(ADMIN_ID, UserRole.ADMIN);
        AppUser owner = createUser(USER_ID, UserRole.USER);

        when(userRepository.findById(ADMIN_ID))
            .thenReturn(Optional.of(admin));

        when(bookingRepository.findAllByOrderByCreatedAtDesc())
            .thenReturn(List.of(createBooking(owner)));

        List<BookingResponse> result =
            bookingService.getAllBookings(ADMIN_ID);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldAllowOwnerToCancelBooking() {
        AppUser user = createUser(USER_ID, UserRole.USER);
        Booking booking = createBooking(user);

        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(user));

        when(bookingRepository.findById(10L))
            .thenReturn(Optional.of(booking));

        bookingService.cancel(10L, USER_ID);

        assertThat(booking.getStatus())
            .isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void shouldPreventCancellingAnotherUsersBooking() {
        AppUser currentUser = createUser(USER_ID, UserRole.USER);
        AppUser owner = createUser(OTHER_USER_ID, UserRole.USER);
        Booking booking = createBooking(owner);

        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(currentUser));

        when(bookingRepository.findById(10L))
            .thenReturn(Optional.of(booking));

        ResponseStatusException exception =
            catchThrowableOfType(
                () -> bookingService.cancel(10L, USER_ID),
                ResponseStatusException.class
            );

        assertThat(exception.getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(booking.getStatus())
            .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void shouldAllowAdminToCancelAnyBooking() {
        AppUser admin = createUser(ADMIN_ID, UserRole.ADMIN);
        AppUser owner = createUser(USER_ID, UserRole.USER);
        Booking booking = createBooking(owner);

        when(userRepository.findById(ADMIN_ID))
            .thenReturn(Optional.of(admin));

        when(bookingRepository.findById(10L))
            .thenReturn(Optional.of(booking));

        bookingService.cancel(10L, ADMIN_ID);

        assertThat(booking.getStatus())
            .isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void shouldThrowWhenBookingNotFound() {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(
                createUser(USER_ID, UserRole.USER)
            ));

        when(bookingRepository.findById(999L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> bookingService.getById(999L, USER_ID)
        ).isInstanceOf(BookingNotFoundException.class);
    }

    private CreateBookingRequest createRequest() {
        return new CreateBookingRequest(1L, START, END);
    }

    private AppUser createUser(Long id, UserRole role) {
        AppUser user = new AppUser(
            "user" + id + "@example.com",
            "hashed-password"
        );

        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);

        return user;
    }

    private Room createRoom() {
        Room room = new Room(
            "Meeting Room",
            6,
            "Floor 1",
            true
        );

        ReflectionTestUtils.setField(room, "id", 1L);

        return room;
    }

    private Booking createBooking(AppUser owner) {
        Booking booking = new Booking(
            createRoom(),
            owner,
            START,
            END
        );

        ReflectionTestUtils.setField(booking, "id", 10L);

        return booking;
    }
}
