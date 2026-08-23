package com.aircargo.authservice.service;

import com.aircargo.authservice.entity.PasswordResetToken;
import com.aircargo.authservice.repository.PasswordResetTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Tokens de un solo uso para establecer/restablecer contraseña.
 * En BD solo se persiste el SHA-256 del token; el valor crudo viaja
 * únicamente en el enlace entregado al usuario una sola vez.
 */
@Service
public class PasswordResetService {

    public static final int EXPIRY_MINUTES = 15;

    private final PasswordResetTokenRepository repository;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetTokenRepository repository) {
        this.repository = repository;
    }

    /** Genera token crudo (devolver UNA vez), persiste su hash e invalida los pendientes anteriores del usuario. */
    @Transactional
    public String create(UUID userId) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        for (PasswordResetToken pending : repository.findByUserIdAndUsedAtIsNull(userId)) {
            pending.setUsedAt(OffsetDateTime.now());
        }
        PasswordResetToken entity = new PasswordResetToken();
        entity.setUserId(userId);
        entity.setTokenHash(sha256Hex(token));
        entity.setExpiresAt(OffsetDateTime.now().plusMinutes(EXPIRY_MINUTES));
        repository.save(entity);
        return token;
    }

    public Optional<PasswordResetToken> validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHashAndUsedAtIsNull(sha256Hex(rawToken))
                .filter(t -> t.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Transactional
    public void markUsed(PasswordResetToken token) {
        token.setUsedAt(OffsetDateTime.now());
        repository.save(token);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
