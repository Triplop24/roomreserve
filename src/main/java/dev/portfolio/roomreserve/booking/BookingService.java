
package dev.portfolio.roomreserve.booking;

import dev.portfolio.roomreserve.booking.dto.BookingResponse;
import dev.portfolio.roomreserve.booking.dto.CreateBookingRequest;
import dev.portfolio.roomreserve.booking.exception.BookingConflictException;
import dev.portfolio.roomreserve.booking.exception.BookingNotFoundException;
import dev.portfolio.roomreserve.booking.exception.InvalidBookingIntervalException;
import dev.portfolio.roomreserve.room.Room;
import dev.portfolio.roomreserve.room.RoomRepository;
import dev.portfolio.roomreserve.room.exception.RoomNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;

    public BookingService(
        BookingRepository bookingRepository,
        RoomRepository roomRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {

        // Validate booking interval
        if (!request.startAt().isBefore(request.endAt())) {
            throw new InvalidBookingIntervalException();
        }

        // Find active room
        Room room = roomRepository
            .findByIdAndActiveTrue(request.roomId())
            .orElseThrow(
                () -> new RoomNotFoundException(request.roomId())
            );

        // Check existing booking conflicts
        boolean overlaps = bookingRepository.existsOverlappingBooking(
            room.getId(),
            BookingStatus.CONFIRMED,
            request.startAt(),
            request.endAt()
        );

        if (overlaps) {
            throw new BookingConflictException();
        }

        // Normalize customer email
        String email = request.customerEmail()
            .trim()
            .toLowerCase(Locale.ROOT);

        // Create booking
        Booking booking = new Booking(
            room,
            email,
            request.startAt(),
            request.endAt()
        );

        try {
            // Flush to detect database constraint violations
            Booking savedBooking = bookingRepository.saveAndFlush(booking);

            return BookingResponse.from(savedBooking);

        } catch (DataIntegrityViolationException ex) {

            if (isExclusionViolation(ex)) {
                throw new BookingConflictException();
            }

            throw ex;
        }
    }

    public BookingResponse getById(Long id) {

        Booking booking = bookingRepository.findById(id)
            .orElseThrow(
                () -> new BookingNotFoundException(id)
            );

        return BookingResponse.from(booking);
    }

    @Transactional
    public void cancel(Long id) {

        Booking booking = bookingRepository.findById(id)
            .orElseThrow(
                () -> new BookingNotFoundException(id)
            );

        booking.cancel();
    }

    /**
     * PostgreSQL SQLSTATE 23P01:
     * exclusion_violation.
     */
    private boolean isExclusionViolation(Throwable exception) {

        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof SQLException sqlException) {

                if ("23P01".equals(sqlException.getSQLState())) {
                    return true;
                }
            }

            cause = cause.getCause();
        }

        return false;
    }
}
