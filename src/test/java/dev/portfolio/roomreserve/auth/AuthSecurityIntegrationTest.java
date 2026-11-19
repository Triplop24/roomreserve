package dev.portfolio.roomreserve.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.portfolio.roomreserve.room.Room;
import dev.portfolio.roomreserve.room.RoomRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthSecurityIntegrationTest {

    private static final String PASSWORD = "TestPassword123!";

    private static final String ISSUER =
        "https://roomreserve.example";

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("roomreserve_security_test")
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRegisterUserWithEncryptedPasswordAndValidJwt()
        throws Exception {

        String email = randomEmail();

        MvcResult result = mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(email, PASSWORD))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andReturn();

        AppUser user = userRepository.findByEmail(email)
            .orElseThrow();

        assertThat(user.getPasswordHash())
            .isNotEqualTo(PASSWORD);

        assertThat(passwordEncoder.matches(
            PASSWORD,
            user.getPasswordHash()
        )).isTrue();

        assertThat(user.getRole())
            .isEqualTo(UserRole.USER);

        String token = readToken(result);
        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject())
            .isEqualTo(user.getId().toString());

        assertThat(jwt.getIssuer().toString())
            .isEqualTo(ISSUER);

        assertThat(jwt.getClaimAsStringList("roles"))
            .containsExactly("USER");
    }

    @Test
    void shouldLoginAndRejectInvalidCredentials()
        throws Exception {

        String email = randomEmail();

        registerUser(email);

        mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(email, PASSWORD))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString());

        mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(
                        email,
                        "WrongPassword123!"
                    ))
            )
            .andExpect(status().isUnauthorized());

        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(email, PASSWORD))
            )
            .andExpect(status().isConflict());
    }

    @Test
    void shouldEnforceAuthenticationOwnershipAndAdminRole()
        throws Exception {

        String firstEmail = randomEmail();
        String secondEmail = randomEmail();

        registerUser(firstEmail);
        registerUser(secondEmail);

        String firstToken = login(firstEmail);
        String secondToken = login(secondEmail);

        // Requests without a valid JWT are rejected
        mockMvc.perform(get("/api/bookings/my"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(
                get("/api/bookings/my")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer invalid.token.value"
                    )
            )
            .andExpect(status().isUnauthorized());

        // A regular user cannot access admin endpoints
        mockMvc.perform(
                get("/api/bookings")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(firstToken)
                    )
            )
            .andExpect(status().isForbidden());

        Room room = roomRepository.saveAndFlush(
            new Room(
                "Security Test Room",
                6,
                "Test Floor",
                true
            )
        );

        String bookingJson = objectMapper.writeValueAsString(
            Map.of(
                "roomId", room.getId(),
                "startAt", "2030-04-01T10:00:00Z",
                "endAt", "2030-04-01T11:00:00Z"
            )
        );

        // First user creates a booking
        MvcResult bookingResult = mockMvc.perform(
                post("/api/bookings")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(firstToken)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bookingJson)
            )
            .andExpect(status().isCreated())
            .andReturn();

        long bookingId = objectMapper.readTree(
            bookingResult.getResponse().getContentAsString()
        ).get("id").asLong();

        // Owner can read their booking
        mockMvc.perform(
                get("/api/bookings/{id}", bookingId)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(firstToken)
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(bookingId));

        // Owner can list their bookings
        mockMvc.perform(
                get("/api/bookings/my")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(firstToken)
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(bookingId));

        // Another user cannot read this booking
        mockMvc.perform(
                get("/api/bookings/{id}", bookingId)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(secondToken)
                    )
            )
            .andExpect(status().isForbidden());

        // Another user cannot cancel it
        mockMvc.perform(
                delete("/api/bookings/{id}", bookingId)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(secondToken)
                    )
            )
            .andExpect(status().isForbidden());

        // Promote the second user in the test database
        int updated = jdbcTemplate.update(
            """
            UPDATE app_users
            SET role = 'ADMIN'
            WHERE email = ?
            """,
            secondEmail
        );

        assertThat(updated).isEqualTo(1);

        String adminToken = login(secondEmail);

        Jwt adminJwt = jwtDecoder.decode(adminToken);

        assertThat(adminJwt.getClaimAsStringList("roles"))
            .containsExactly("ADMIN");

        // Admin can list all bookings
        mockMvc.perform(
                get("/api/bookings")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(adminToken)
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(bookingId));

        // Admin can cancel another user's booking
        mockMvc.perform(
                delete("/api/bookings/{id}", bookingId)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(adminToken)
                    )
            )
            .andExpect(status().isNoContent());

        // Owner sees that the booking is cancelled
        mockMvc.perform(
                get("/api/bookings/{id}", bookingId)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(firstToken)
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private void registerUser(String email) throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(email, PASSWORD))
            )
            .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(email, PASSWORD))
            )
            .andExpect(status().isOk())
            .andReturn();

        return readToken(result);
    }

    private String credentials(
        String email,
        String password
    ) throws Exception {
        return objectMapper.writeValueAsString(
            Map.of(
                "email", email,
                "password", password
            )
        );
    }

    private String readToken(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(
            result.getResponse().getContentAsString()
        );

        return json.get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String randomEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
