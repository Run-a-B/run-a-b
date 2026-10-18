package com.runab.api.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.runab.api.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GoogleIdentityVerifierTest {
    @Test
    void disabledGoogleLoginFailsBeforeAnyProviderCall() {
        GoogleIdTokenVerifier provider = mock(GoogleIdTokenVerifier.class);
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier("  ", provider);

        assertThatThrownBy(() -> verifier.verify("id-credential"))
                .extracting("errorCode").isEqualTo(ErrorCode.GOOGLE_NOT_CONFIGURED);
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankCredentialsAreRejectedBeforeAnyProviderCall(String credential) {
        GoogleIdTokenVerifier provider = mock(GoogleIdTokenVerifier.class);
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier("runab-client", provider);

        assertThatThrownBy(() -> verifier.verify(credential))
                .extracting("errorCode").isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED);
        verifyNoInteractions(provider);
    }

    @Test
    void oversizedCredentialIsRejectedBeforeParsingOrFetchingCertificates() {
        GoogleIdTokenVerifier provider = mock(GoogleIdTokenVerifier.class);
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier("runab-client", provider);

        assertThatThrownBy(() -> verifier.verify("a".repeat(8193)))
                .extracting("errorCode").isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED);
        verifyNoInteractions(provider);
    }

    @Test
    void verifiedProviderPayloadBecomesAnIdentity() throws Exception {
        GoogleIdTokenVerifier provider = mock(GoogleIdTokenVerifier.class);
        GoogleIdToken.Payload payload = payload().set("name", "테스트");
        given(provider.verify("id-credential")).willReturn(token(payload));

        assertThat(new GoogleIdentityVerifier("runab-client", provider).verify("id-credential"))
                .isEqualTo(new GoogleIdentityVerifier.VerifiedIdentity("google-sub", "owner@example.test", "테스트"));
    }

    @Test
    void emailMustBeVerifiedAndIdentityClaimsMustFitDatabaseBounds() throws Exception {
        for (GoogleIdToken.Payload payload : List.of(
                payload().setEmailVerified(false), payload().setEmailVerified(null),
                payload().setSubject(" "), payload().setSubject(null),
                payload().setEmail(null), payload().setEmail(" "),
                payload().setSubject("a".repeat(256)), payload().setEmail("a".repeat(101)))) {
            GoogleIdTokenVerifier provider = mock(GoogleIdTokenVerifier.class);
            given(provider.verify("id-credential")).willReturn(token(payload));

            assertThatThrownBy(() -> new GoogleIdentityVerifier("runab-client", provider).verify("id-credential"))
                    .extracting("errorCode").isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    @Test
    void invalidSignatureOrProviderFailureReturnsOnlyGenericAuthenticationError() throws Exception {
        GoogleIdTokenVerifier provider = mock(GoogleIdTokenVerifier.class);
        given(provider.verify("invalid-signature")).willReturn(null);
        given(provider.verify("provider-failure")).willThrow(new IOException("private provider details"));
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier("runab-client", provider);

        for (String credential : List.of("invalid-signature", "provider-failure")) {
            assertThatThrownBy(() -> verifier.verify(credential)).extracting("errorCode").isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    @Test
    void realGoogleVerifierRejectsWrongAudienceIssuerAndExpiryWithoutNetwork() throws Exception {
        HttpTransport noNetwork = new HttpTransport() {
            @Override protected LowLevelHttpRequest buildRequest(String method, String url) {
                throw new AssertionError("Invalid claims must fail before certificate retrieval");
            }
        };
        GoogleIdTokenVerifier provider = new GoogleIdTokenVerifier.Builder(noNetwork, GsonFactory.getDefaultInstance())
                .setAudience(List.of("runab-client")).build();
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier("runab-client", provider);

        for (GoogleIdToken.Payload invalid : List.of(
                payload().setAudience("another-client"),
                payload().setIssuer("https://untrusted.example.test"),
                payload().setExpirationTimeSeconds(Instant.now().getEpochSecond() - 3600))) {
            String serialized = compact(invalid);
            assertThatThrownBy(() -> verifier.verify(serialized))
                    .extracting("errorCode").isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    private static GoogleIdToken.Payload payload() {
        long now = Instant.now().getEpochSecond();
        return new GoogleIdToken.Payload().setIssuer("https://accounts.google.com").setAudience("runab-client")
                .setIssuedAtTimeSeconds(now).setExpirationTimeSeconds(now + 3600)
                .setSubject("google-sub").setEmail("owner@example.test").setEmailVerified(true);
    }

    private static GoogleIdToken token(GoogleIdToken.Payload payload) {
        return new GoogleIdToken(new JsonWebSignature.Header().setAlgorithm("RS256"),
                payload, new byte[] {1}, new byte[] {1});
    }

    private static String compact(GoogleIdToken.Payload payload) throws IOException {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = encoder.encodeToString(GsonFactory.getDefaultInstance().toByteArray(payload));
        return header + "." + claims + ".AQ";
    }
}
