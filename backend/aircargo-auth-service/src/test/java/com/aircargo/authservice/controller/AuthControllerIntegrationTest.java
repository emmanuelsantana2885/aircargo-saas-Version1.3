package com.aircargo.authservice.controller;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.PasswordResetToken;
import com.aircargo.authservice.service.PasswordResetService;
import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.common.entity.Airline;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Airline airline;

    @BeforeEach
    void setUp() {
        airline = Airline.builder()
                .code("TST")
                .name("Test Airline")
                .iataCode("TT")
                .country("DO")
                .isActive(true)
                .build();
        entityManager.persist(airline);
        entityManager.flush();
    }

    private AppUser user(String email, UserRole role) {
        return AppUser.builder()
                .email(email)
                .fullName("Test User")
                .role(role)
                .airline(airline)
                .build();
    }

    @Test
    void login_validUser_returnsToken() throws Exception {
        userRepository.save(user("test@aircargo.com", UserRole.OPERATIONS));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@aircargo.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("test@aircargo.com"))
                .andExpect(jsonPath("$.role").value("OPERATIONS"));
    }

    @Test
    void login_unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@aircargo.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_inactiveUser_returns403() throws Exception {
        userRepository.save(user("inactive@aircargo.com", UserRole.OPERATIONS));
        AppUser inactive = userRepository.findByEmail("inactive@aircargo.com").orElseThrow();
        inactive.setIsActive(false);
        userRepository.save(inactive);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inactive@aircargo.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_missingPasswordWhenHashSet_returns428() throws Exception {
        userRepository.save(user("pw@aircargo.com", UserRole.ADMIN));
        AppUser pwUser = userRepository.findByEmail("pw@aircargo.com").orElseThrow();
        pwUser.setPasswordHash(passwordEncoder.encode("CorrectHorse1!"));
        userRepository.save(pwUser);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pw@aircargo.com\"}"))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void login_wrongPassword_returnsGeneric401_sameAsUnknownUser() throws Exception {
        userRepository.save(user("wrongpw@aircargo.com", UserRole.OPERATIONS));
        AppUser u = userRepository.findByEmail("wrongpw@aircargo.com").orElseThrow();
        u.setPasswordHash(passwordEncoder.encode("CorrectHorse1!"));
        userRepository.save(u);

        String wrongPasswordResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrongpw@aircargo.com\",\"password\":\"BadPassword9!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownUserResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@aircargo.com\",\"password\":\"BadPassword9!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(
                objectMapper.readTree(unknownUserResponse).get("error").asText(),
                objectMapper.readTree(wrongPasswordResponse).get("error").asText());
    }

    @Test
    void login_locksAccountAfterFiveFailedAttempts() throws Exception {
        userRepository.save(user("lockme@aircargo.com", UserRole.OPERATIONS));
        AppUser u = userRepository.findByEmail("lockme@aircargo.com").orElseThrow();
        u.setPasswordHash(passwordEncoder.encode("CorrectHorse1!"));
        userRepository.save(u);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"lockme@aircargo.com\",\"password\":\"Nope1234!\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Email y/o contraseña incorrectos"));
        }

        // 6th attempt: even the correct password is rejected while locked
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lockme@aircargo.com\",\"password\":\"CorrectHorse1!\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("bloqueada")));

        AppUser locked = userRepository.findByEmail("lockme@aircargo.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(5, locked.getFailedLoginAttempts());
        org.junit.jupiter.api.Assertions.assertNotNull(locked.getLockedUntil());
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setPassword_weakPassword_returns400() throws Exception {
        userRepository.save(user("weakpw@aircargo.com", UserRole.OPERATIONS));

        mockMvc.perform(post("/api/auth/set-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"weakpw@aircargo.com\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditEvents_recordedForLogins_andServedByQuerySide() throws Exception {
        userRepository.save(user("audited@aircargo.com", UserRole.OPERATIONS));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"audited@aircargo.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@aircargo.com\",\"password\":\"Whatever1!\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/audit-logs/security"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'LOGIN_SUCCEEDED')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'LOGIN_FAILED')]").isNotEmpty());
    }

    @Test
    void createUser_invalidEmail_returns400() throws Exception {
        String body = """
                {"email":"broken-email","fullName":"X","role":"READ_ONLY",
                 "airlineId":"%s","siteIds":["00000000-0000-0000-0000-00000000000a"]}
                """.formatted(airline.getId());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetToken_invalid_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"no-existe\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetToken_fullFlow_setsPasswordAndSingleUse() throws Exception {
        AppUser u = user("reset@aircargo.com", UserRole.OPERATIONS);
        userRepository.save(u);

        String raw = "tok-" + java.util.UUID.randomUUID();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUserId(u.getId());
        prt.setTokenHash(PasswordResetService.sha256Hex(raw));
        prt.setExpiresAt(java.time.OffsetDateTime.now().plusMinutes(15));
        entityManager.persist(prt);
        entityManager.flush();

        // contraseña débil rechazada
        mockMvc.perform(post("/api/auth/set-password-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("token", raw, "newPassword", "weak"))))
                .andExpect(status().isBadRequest());

        // contraseña fuerte aceptada y devuelve JWT
        mockMvc.perform(post("/api/auth/set-password-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("token", raw, "newPassword", "CorrectHorse1!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        // el token ya fue consumido → reuso rechazado
        mockMvc.perform(post("/api/auth/set-password-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("token", raw, "newPassword", "OtherHorse1!"))))
                .andExpect(status().isBadRequest());

        // la nueva contraseña autentica
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", "reset@aircargo.com", "password", "CorrectHorse1!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }
}
