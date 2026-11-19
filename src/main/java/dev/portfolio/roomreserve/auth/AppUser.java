package dev.portfolio.roomreserve.auth;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        name = "email",
        nullable = false,
        unique = true,
        length = 255
    )
    private String email;

    @Column(
        name = "password_hash",
        nullable = false
    )
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "role",
        nullable = false,
        length = 16
    )
    private UserRole role;

    @Column(
        name = "created_at",
        nullable = false
    )
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = UserRole.USER;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
