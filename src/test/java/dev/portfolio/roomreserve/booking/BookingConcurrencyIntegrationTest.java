package dev.portfolio.roomreserve.booking;

import dev.portfolio.roomreserve.auth.AppUser;
import dev.portfolio.roomreserve.auth.AppUserRepository;
import dev.portfolio.roomreserve.booking.dto.CreateBookingRequest;
import dev.portfolio.roomreserve.booking.exception.BookingConflictException;
import dev.portfolio.roomreserve.room.Room;
import dev.portfolio.roomreserve.room.RoomRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Testcontainers
class BookingConcurrencyIntegrationTest {

    private static final Instant START =
        Instant.parse("2030-01-15T10:00:00Z");

    private static final Instant END =
        Instant.parse("2030-01-15T11:00:00Z");

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("roomreserve_test")
            .withUsername("roomreserve")
            .withPassword("roomreserve");

    @DynamicPropertySource
    static void configureDatabase(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.datasource.url",
            postgres::getJdbcUrl
        );

        registry.add(
            "spring.datasource.username",
            postgres::getUsername
        );

        registry.add(
            "spring.datasource.password",
            postgres::getPassword
        );
    }

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldAllowOnlyOneConcurrentBooking()
        throws Exception {

        Room room = createRoom();

        AppUser firstUser = createUser();
        AppUser secondUser = createUser();

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Result> first = executor.submit(
                () -> createBooking(
                    room.getId(),
                    firstUser.getId(),
                    ready,
                    start
                )
            );

            Future<Result> second = executor.submit(
                () -> createBooking(
                    room.getId(),
                    secondUser.getId(),
                    ready,
                    start
                )
            );

            assertThat(ready.await(10, TimeUnit.SECONDS))
                .isTrue();

            start.countDown();

            Result firstResult =
                first.get(20, TimeUnit.SECONDS);

            Result secondResult =
                second.get(20, TimeUnit.SECONDS);

            assertThat(List.of(firstResult, secondResult))
                .containsExactlyInAnyOrder(
                    Result.CREATED,
                    Result.CONFLICT
                );

            assertThat(countConfirmedBookings(room.getId()))
                .isEqualTo(1L);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectOverlapAtDatabaseLevel() {

        Room room = createRoom();
        AppUser firstUser = createUser();
        AppUser secondUser = createUser();

        Booking first = new Booking(
            room,
            firstUser,
            START,
            END
        );

        bookingRepository.saveAndFlush(first);

        Booking second = new Booking(
            room,
            secondUser,
            START.plusSeconds(1800),
            END.plusSeconds(1800)
        );

        assertThatThrownBy(
            () -> bookingRepository.saveAndFlush(second)
        ).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countConfirmedBookings(room.getId()))
            .isEqualTo(1L);
    }

    private Result createBooking(
        Long roomId,
        Long userId,
        CountDownLatch ready,
        CountDownLatch start
    ) throws InterruptedException {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                "Timeout waiting for concurrent start"
            );
        }

        try {
            bookingService.create(
                new CreateBookingRequest(
                    roomId,
                    START,
                    END
                ),
                userId
            );

            return Result.CREATED;

        } catch (BookingConflictException ex) {
            return Result.CONFLICT;
        }
    }

    private Room createRoom() {
        Room room = new Room(
            "Concurrency-" + UUID.randomUUID(),
            4,
            "Test Floor",
            true
        );

        return roomRepository.saveAndFlush(room);
    }

    private AppUser createUser() {
        AppUser user = new AppUser(
            UUID.randomUUID() + "@example.com",
            "test-password-hash"
        );

        return userRepository.saveAndFlush(user);
    }

    private Long countConfirmedBookings(Long roomId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM bookings
            WHERE room_id = ?
              AND status = 'CONFIRMED'
            """,
            Long.class,
            roomId
        );
    }

    private enum Result {
        CREATED,
        CONFLICT
    }
}
