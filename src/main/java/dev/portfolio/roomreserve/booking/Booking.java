package dev.portfolio.roomreserve.booking;

import dev.portfolio.roomreserve.room.Room;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Booking() {
    }

    public Booking(Room room, String customerEmail,
                   Instant startAt, Instant endAt) {
        this.room = room;
        this.customerEmail = customerEmail;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = BookingStatus.CONFIRMED;
        this.createdAt = Instant.now();
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    public Long getId() { return id; }

    public Room getRoom() { return room; }

    public String getCustomerEmail() { return customerEmail; }

    public Instant getStartAt() { return startAt; }

    public Instant getEndAt() { return endAt; }

    public BookingStatus getStatus() { return status; }

    public Instant getCreatedAt() { return createdAt; }

}
