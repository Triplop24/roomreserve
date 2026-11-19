package dev.portfolio.roomreserve.auth.dto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {
}
