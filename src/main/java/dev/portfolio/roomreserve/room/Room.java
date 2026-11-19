package dev.portfolio.roomreserve.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Room() {
        // Required by JPA.
    }

    public Room(String name, int capacity, String location, boolean active) {
        this.name = name;
        this.capacity = capacity;
        this.location = location;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getLocation() {
        return location;
    }

    public boolean isActive() {
        return active;
    }
}
