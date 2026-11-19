package dev.portfolio.roomreserve.auth;

import dev.portfolio.roomreserve.auth.dto.AuthResponse;
import dev.portfolio.roomreserve.auth.dto.LoginRequest;
import dev.portfolio.roomreserve.auth.dto.RegisterRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private static final String ISSUER =
        "https://roomreserve.example";

    private static final Duration TOKEN_LIFETIME =
        Duration.ofHours(1);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(
        AppUserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtEncoder jwtEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Email is already registered"
            );
        }

        String passwordHash =
            passwordEncoder.encode(request.password());

        AppUser user = new AppUser(email, passwordHash);

        try {
            user = userRepository.saveAndFlush(user);

        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Email is already registered",
                ex
            );
        }

        return generateToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        String email = normalizeEmail(request.email());

        AppUser user = userRepository.findByEmail(email)
            .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(
            request.password(),
            user.getPasswordHash()
        )) {
            throw invalidCredentials();
        }

        return generateToken(user);
    }

    private AuthResponse generateToken(AppUser user) {

        Instant now = Instant.now();
        Instant expiresAt = now.plus(TOKEN_LIFETIME);

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim(
                "roles",
                List.of(user.getRole().name())
            )
            .build();

        JwsHeader header = JwsHeader
            .with(MacAlgorithm.HS256)
            .build();

        Jwt jwt = jwtEncoder.encode(
            JwtEncoderParameters.from(header, claims)
        );

        return new AuthResponse(
            jwt.getTokenValue(),
            "Bearer",
            TOKEN_LIFETIME.toSeconds()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Invalid email or password"
        );
    }
}
