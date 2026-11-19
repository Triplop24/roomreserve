package dev.portfolio.roomreserve.booking;

import dev.portfolio.roomreserve.booking.dto.BookingResponse;
import dev.portfolio.roomreserve.booking.dto.CreateBookingRequest;
import dev.portfolio.roomreserve.booking.exception.BookingConflictException;
import dev.portfolio.roomreserve.booking.exception.BookingNotFoundException;
import dev.portfolio.roomreserve.booking.exception.InvalidBookingIntervalException;
import dev.portfolio.roomreserve.room.Room;
import dev.portfolio.roomreserve.room.RoomRepository;
import dev.portfolio.roomreserve.room.exception.RoomNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // Проверяем корректность времени
        if (!request.startAt().isBefore(request.endAt())) {
            throw new InvalidBookingIntervalException();
        }

        // Проверяем существование активной комнаты
        Room room = roomRepository
            .findByIdAndActiveTrue(request.roomId())
            .orElseThrow(
                () -> new RoomNotFoundException(request.roomId())
            );

        // Проверяем пересечение с существующими бронями
        boolean overlaps = bookingRepository.existsOverlappingBooking(
            room.getId(),
            BookingStatus.CONFIRMED,
            request.startAt(),
            request.endAt()
        );

        if (overlaps) {
            throw new BookingConflictException();
        }

        // Создаём бронирование
        String email = request.customerEmail()
            .trim()
            .toLowerCase(Locale.ROOT);

        Booking booking = new Booking(
            room,
            email,
            request.startAt(),
            request.endAt()
        );

        Booking savedBooking = bookingRepository.save(booking);

        return BookingResponse.from(savedBooking);
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
}
