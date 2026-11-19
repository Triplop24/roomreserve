package dev.portfolio.roomreserve.booking;

import dev.portfolio.roomreserve.booking.dto.BookingResponse;
import dev.portfolio.roomreserve.booking.dto.CreateBookingRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
        @Valid @RequestBody CreateBookingRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        BookingResponse response = bookingService.create(
            request,
            currentUserId(jwt)
        );

        return ResponseEntity
            .created(URI.create("/api/bookings/" + response.id()))
            .body(response);
    }

    @GetMapping("/my")
    public List<BookingResponse> getMyBookings(
        @AuthenticationPrincipal Jwt jwt
    ) {
        return bookingService.getMyBookings(currentUserId(jwt));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<BookingResponse> getAllBookings(
        @AuthenticationPrincipal Jwt jwt
    ) {
        return bookingService.getAllBookings(currentUserId(jwt));
    }

    @GetMapping("/{id}")
    public BookingResponse getById(
        @PathVariable Long id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return bookingService.getById(
            id,
            currentUserId(jwt)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(
        @PathVariable Long id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        bookingService.cancel(
            id,
            currentUserId(jwt)
        );

        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication required"
            );
        }

        try {
            return Long.valueOf(jwt.getSubject());

        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid token subject",
                ex
            );
        }
    }
}
