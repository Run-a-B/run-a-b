package com.runab.api.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleIdentityVerifier {
    private static final int MAX_TOKEN_LENGTH = 8192;
    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    @Autowired
    public GoogleIdentityVerifier(@Value("${google.client-id:}") String clientId) {
        // The installed Google HTTP client bounds certificate fetches to 20s connect/read timeouts.
        // GoogleIdTokenVerifier checks signature, Google issuer, expiry and this application's audience.
        this(clientId, new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId == null ? "" : clientId.trim())).build());
    }

    GoogleIdentityVerifier(String clientId, GoogleIdTokenVerifier verifier) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.verifier = verifier;
    }

    public VerifiedIdentity verify(String credential) {
        if (clientId.isBlank()) throw new BusinessException(ErrorCode.GOOGLE_NOT_CONFIGURED);
        if (credential == null || credential.isBlank() || credential.length() > MAX_TOKEN_LENGTH) {
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
        }

        GoogleIdToken token;
        try {
            token = verifier.verify(credential);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        if (token == null) throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
        GoogleIdToken.Payload payload = token.getPayload();
        String subject = payload.getSubject();
        String email = payload.getEmail();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())
                || subject == null || subject.isBlank() || subject.length() > 255
                || email == null || email.isBlank() || email.length() > 100) {
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        String name = payload.get("name") instanceof String value ? value : null;
        return new VerifiedIdentity(subject, email, name);
    }

    public record VerifiedIdentity(String subject, String email, String name) {}
}
