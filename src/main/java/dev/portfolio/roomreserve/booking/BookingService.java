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

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final AppUserRepository userRepository;

    public BookingService(
        BookingRepository bookingRepository,
        RoomRepository roomRepository,
        AppUserRepository userRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BookingResponse create(
        CreateBookingRequest request,
        Long currentUserId
    ) {
        if (!request.startAt().isBefore(request.endAt())) {
            throw new InvalidBookingIntervalException();
        }

        AppUser user = getCurrentUser(currentUserId);

        Room room = roomRepository
            .findByIdAndActiveTrue(request.roomId())
            .orElseThrow(
                () -> new RoomNotFoundException(request.roomId())
            );

        boolean overlaps = bookingRepository.existsOverlappingBooking(
            room.getId(),
            BookingStatus.CONFIRMED,
            request.startAt(),
            request.endAt()
        );

        if (overlaps) {
            throw new BookingConflictException();
        }

        Booking booking = new Booking(
            room,
            user,
            request.startAt(),
            request.endAt()
        );

        try {
            Booking saved = bookingRepository.saveAndFlush(booking);
            return BookingResponse.from(saved);

        } catch (DataIntegrityViolationException ex) {
            if (isExclusionViolation(ex)) {
                throw new BookingConflictException();
            }

            throw ex;
        }
    }

    public BookingResponse getById(
        Long bookingId,
        Long currentUserId
    ) {
        AppUser currentUser = getCurrentUser(currentUserId);

        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(
                () -> new BookingNotFoundException(bookingId)
            );

        checkBookingAccess(booking, currentUser);

        return BookingResponse.from(booking);
    }

    public List<BookingResponse> getMyBookings(Long currentUserId) {
        getCurrentUser(currentUserId);

        return bookingRepository
            .findByUser_IdOrderByCreatedAtDesc(currentUserId)
            .stream()
            .map(BookingResponse::from)
            .toList();
    }

    public List<BookingResponse> getAllBookings(Long currentUserId) {
        AppUser currentUser = getCurrentUser(currentUserId);

        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Admin role is required"
            );
        }

        return bookingRepository
            .findAllByOrderByCreatedAtDesc()
            .stream()
            .map(BookingResponse::from)
            .toList();
    }

    @Transactional
    public void cancel(
        Long bookingId,
        Long currentUserId
    ) {
        AppUser currentUser = getCurrentUser(currentUserId);

        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(
                () -> new BookingNotFoundException(bookingId)
            );

        checkBookingAccess(booking, currentUser);

        booking.cancel();
    }

    private AppUser getCurrentUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user not found"
                )
            );
    }

    private void checkBookingAccess(
        Booking booking,
        AppUser currentUser
    ) {
        if (currentUser.getRole() == UserRole.ADMIN) {
            return;
        }

        if (booking.getUser() == null
            || !booking.getUser().getId().equals(currentUser.getId())) {

            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You cannot access another user's booking"
            );
        }
    }

    private boolean isExclusionViolation(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof SQLException sqlException
                && "23P01".equals(sqlException.getSQLState())) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}
