package com.aircargo.authservice.controller;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.service.MfaPolicyService;
import com.aircargo.common.entity.Airline;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el enrolamiento forzado de MFA cuando app.mfa.mandatory=true
 * (default de producción): el usuario sin MFA NO recibe sesión en login ni
 * en set-password; debe completar setup→enable (TOTP) y luego re-autenticarse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties = {
        "app.mfa.mandatory=true"
})
@Transactional
class MfaMandatoryIntegrationTest {

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

    @Autowired
    private MfaPolicyService mfaPolicyService;

    @Autowired
    private com.aircargo.common.auth.JwtUtil jwtUtil;

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
    void login_sinMFA_requiereEnrolamiento_yLuegoMFA_verificaFlujoCompleto() throws Exception {
        userRepository.save(user("enrollme@aircargo.com", UserRole.OPERATIONS));

        // 1. Login sin MFA → 428 MFA_ENROLLMENT_REQUIRED + enrollToken (sin token de sesión)
        JsonNode first = readBody(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"enrollme@aircargo.com\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.mfaEnrollmentRequired").value(true))
                .andExpect(jsonPath("$.enrollToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString());
        String enrollToken = first.get("enrollToken").asText();

        // 2. Setup → secreto + otpauth
        JsonNode setup = readBody(mockMvc.perform(post("/api/auth/mfa/enroll/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("enrollToken", enrollToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.email").value("enrollme@aircargo.com"))
                .andReturn().getResponse().getContentAsString());
        String secret = setup.get("secret").asText();

        // 3. Enable con código inválido → 400
        mockMvc.perform(post("/api/auth/mfa/enroll/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "enrollToken", enrollToken, "secret", secret, "totpCode", "000000"))))
                .andExpect(status().isBadRequest());

        // 4. Enable con código válido → 200 y MFA habilitado en BD
        String code = totpCode(secret);
        mockMvc.perform(post("/api/auth/mfa/enroll/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "enrollToken", enrollToken, "secret", secret, "totpCode", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollSuccess").value(true));

        AppUser enabled = userRepository.findByEmail("enrollme@aircargo.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(enabled.getMfaEnabled()));
        org.junit.jupiter.api.Assertions.assertTrue(enabled.getMfaSecret() != null && !enabled.getMfaSecret().isBlank());

        // 5. El enroll token ya fue consumido → reuso rechazado
        mockMvc.perform(post("/api/auth/mfa/enroll/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("enrollToken", enrollToken))))
                .andExpect(status().isUnauthorized());

        // 6. Login ahora pide código MFA (ya no enrolamiento)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"enrollme@aircargo.com\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.mfaRequired").value(true));

        // 7. Login con contraseña limpia + código válido → sesión
        String secondCode = totpCode(secret);
        java.util.Map<String, String> loginPayload = new java.util.HashMap<>();
        loginPayload.put("email", "enrollme@aircargo.com");
        loginPayload.put("totpCode", secondCode);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginSinMFA_noEmiteTokenDeSesion() throws Exception {
        userRepository.save(user("noma@aircargo.com", UserRole.OPERATIONS));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"noma@aircargo.com\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.mfaEnrollmentRequired").value(true));
    }

    @Test
    void setPassword_sinMFA_devuelveEnrolamientoEnVezDeSesion() throws Exception {
        userRepository.save(user("pwflow@aircargo.com", UserRole.OPERATIONS));

        mockMvc.perform(post("/api/auth/set-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", "pwflow@aircargo.com", "newPassword", "CorrectHorse1!"))))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.mfaEnrollmentRequired").value(true))
                .andExpect(jsonPath("$.enrollToken").isNotEmpty())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void login_conMFAVigente_trasReinicio_pideReEnrolamientoConReasonReset() throws Exception {
        String email = "resetme@aircargo.com";
        userRepository.save(user(email, UserRole.OPERATIONS));
        String secret = enrollUser(email);

        // Usuario tiene MFA vigente → login actual pide código (no re-enrolamiento)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaEnrollmentRequired").doesNotExist());

        // Reinicio de la aplicación → epoch adelantado → mismo login ahora exige re-enrolar
        mfaPolicyService.resetNow();

        JsonNode resetBody = readBody(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.mfaEnrollmentRequired").value(true))
                .andExpect(jsonPath("$.mfaReason").value("reset"))
                .andExpect(jsonPath("$.enrollToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString());
        String reEnrollToken = resetBody.get("enrollToken").asText();

        // Re-enrolamiento permitido aun con mfaEnabled=true (está caducado por reinicio)
        JsonNode reSetup = readBody(mockMvc.perform(post("/api/auth/mfa/enroll/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("enrollToken", reEnrollToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andReturn().getResponse().getContentAsString());
        String newSecret = reSetup.get("secret").asText();

        String newCode = totpCode(newSecret);
        mockMvc.perform(post("/api/auth/mfa/enroll/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "enrollToken", reEnrollToken, "secret", newSecret, "totpCode", newCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollSuccess").value(true));
    }

    @Test
    void login_conMFA_antiguo_pideReEnrolamientoConReasonExpired() throws Exception {
        String email = "expired@aircargo.com";
        userRepository.save(user(email, UserRole.OPERATIONS));
        enrollUser(email);

        // Retrasar mfa_enrolled_at más allá de max-age-days (7) → caduca
        AppUser stale = userRepository.findByEmail(email).orElseThrow();
        stale.setMfaEnrolledAt(java.time.OffsetDateTime.now().minusDays(8));
        userRepository.save(stale);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.mfaEnrollmentRequired").value(true))
                .andExpect(jsonPath("$.mfaReason").value("expired"))
                .andExpect(jsonPath("$.enrollToken").isNotEmpty());
    }

    @Test
    void login_sinMFA_siempreReportaReasonRequired() throws Exception {
        userRepository.save(user("first@aircargo.com", UserRole.OPERATIONS));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"first@aircargo.com\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.mfaEnrollmentRequired").value(true))
                .andExpect(jsonPath("$.mfaReason").value("required"));
    }

    /** Completa el flujo completo de enrolamiento para un usuario y devuelve el secreto. */
    private String enrollUser(String email) throws Exception {
        JsonNode first = readBody(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.enrollToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString());
        String enrollToken = first.get("enrollToken").asText();

        JsonNode setup = readBody(mockMvc.perform(post("/api/auth/mfa/enroll/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("enrollToken", enrollToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String secret = setup.get("secret").asText();

        mockMvc.perform(post("/api/auth/mfa/enroll/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "enrollToken", enrollToken, "secret", secret, "totpCode", totpCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollSuccess").value(true));
        return secret;
    }

    private JsonNode readBody(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    /** Mismo cálculo que MfaService: generate recibe el periodo (time/30), no el epoch crudo. */
    private String totpCode(String secret) {
        try {
            return new DefaultCodeGenerator().generate(secret, new SystemTimeProvider().getTime() / 30);
        } catch (dev.samstevens.totp.exceptions.CodeGenerationException e) {
            throw new IllegalStateException("No se pudo generar código TOTP", e);
        }
    }
}