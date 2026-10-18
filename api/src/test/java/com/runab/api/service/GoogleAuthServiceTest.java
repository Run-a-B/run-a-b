package com.runab.api.service;

import com.runab.api.dto.auth.GoogleLoginRequest;
import com.runab.api.entity.AuthProvider;
import com.runab.api.entity.User;
import com.runab.api.exception.ErrorCode;
import com.runab.api.jwt.JwtTokenProvider;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {
    @Mock UserRepository users;
    @Mock BusinessInfoRepository businesses;
    @Mock JwtTokenProvider tokens;
    @Mock GoogleIdentityVerifier identities;
    GoogleAuthService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAuthService(users, businesses, tokens, identities);
    }

    @Test
    void newVerifiedIdentityCreatesGoogleAccountWithNormalizedEmailAndBoundedName() {
        given(identities.verify("id-credential")).willReturn(new GoogleIdentityVerifier.VerifiedIdentity(
                "google-sub", "OWNER@EXAMPLE.TEST", "가".repeat(25)));
        given(users.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 12L);
            return user;
        });
        given(tokens.createAccessToken(12L)).willReturn("app-session");

        var result = service.googleLogin(request());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("owner@example.test");
        assertThat(saved.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(saved.getValue().getProviderId()).isEqualTo("google-sub");
        assertThat(saved.getValue().getUsername()).isEqualTo("가".repeat(20));
        assertThat(saved.getValue().getPassword()).isNull();
        assertThat(result.getAccessToken()).isEqualTo("app-session");
    }

    @Test
    void emptyNameFallsBackToBoundedEmailPrefix() {
        given(identities.verify("id-credential")).willReturn(new GoogleIdentityVerifier.VerifiedIdentity(
                "google-sub", "abcdefghijklmnopqrstuv@example.test", "   "));
        given(users.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        var result = service.googleLogin(request());

        assertThat(result.getUser().getName()).isEqualTo("abcdefghijklmnopqrst");
    }

    @Test
    void longUnicodeNameIsTruncatedAtACharacterBoundary() {
        given(identities.verify("id-credential")).willReturn(new GoogleIdentityVerifier.VerifiedIdentity(
                "google-sub", "owner@example.test", "🙂".repeat(25)));
        given(users.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.googleLogin(request()).getUser().getName()).isEqualTo("🙂".repeat(20));
    }

    @Test
    void matchingEmailAndGoogleSubjectReturnExistingAccountWithoutCreatingAnother() {
        User existing = user(7L, AuthProvider.GOOGLE, "google-sub", "owner@example.test");
        given(identities.verify("id-credential")).willReturn(identity());
        given(users.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub")).willReturn(Optional.of(existing));
        given(users.findByEmail("owner@example.test")).willReturn(Optional.of(existing));
        given(tokens.createAccessToken(7L)).willReturn("app-session");

        assertThat(service.googleLogin(request()).getUser().getId()).isEqualTo(7L);
        verify(users, never()).save(any());
    }

    @Test
    void existingGoogleSubjectKeepsItsAccountWhenItsVerifiedEmailChanges() {
        User existing = user(7L, AuthProvider.GOOGLE, "google-sub", "old@example.test");
        given(identities.verify("id-credential")).willReturn(identity());
        given(users.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub")).willReturn(Optional.of(existing));
        given(tokens.createAccessToken(7L)).willReturn("app-session");

        assertThat(service.googleLogin(request()).getUser().getId()).isEqualTo(7L);
        verify(users, never()).save(any());
    }

    @Test
    void verifiedGoogleEmailNeverSilentlyLinksLocalAccount() {
        given(identities.verify("id-credential")).willReturn(identity());
        given(users.findByEmail("owner@example.test"))
                .willReturn(Optional.of(user(7L, AuthProvider.LOCAL, null, "owner@example.test")));

        assertRejected(ErrorCode.GOOGLE_ACCOUNT_CONFLICT);
    }

    @Test
    void sameGoogleEmailWithDifferentSubjectIsRejected() {
        given(identities.verify("id-credential")).willReturn(identity());
        given(users.findByEmail("owner@example.test"))
                .willReturn(Optional.of(user(7L, AuthProvider.GOOGLE, "different-sub", "owner@example.test")));

        assertRejected(ErrorCode.GOOGLE_ACCOUNT_CONFLICT);
    }

    @Test
    void matchingSubjectCannotTakeEmailAlreadyOwnedByAnotherAccount() {
        given(identities.verify("id-credential")).willReturn(identity());
        given(users.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub"))
                .willReturn(Optional.of(user(7L, AuthProvider.GOOGLE, "google-sub", "old@example.test")));
        given(users.findByEmail("owner@example.test"))
                .willReturn(Optional.of(user(9L, AuthProvider.LOCAL, null, "owner@example.test")));

        assertRejected(ErrorCode.GOOGLE_ACCOUNT_CONFLICT);
    }

    @Test
    void deletedGoogleSubjectCannotGetFreshSession() {
        User deleted = user(7L, AuthProvider.GOOGLE, "google-sub", "owner@example.test");
        deleted.softDelete();
        given(identities.verify("id-credential")).willReturn(identity());
        given(users.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub")).willReturn(Optional.of(deleted));

        assertRejected(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void deletedEmailCannotBeRecreatedThroughGoogle() {
        User deleted = user(7L, AuthProvider.LOCAL, null, "owner@example.test");
        deleted.softDelete();
        given(identities.verify("id-credential")).willReturn(identity());
        given(users.findByEmail("owner@example.test")).willReturn(Optional.of(deleted));

        assertRejected(ErrorCode.UNAUTHORIZED);
    }

    private void assertRejected(ErrorCode code) {
        assertThatThrownBy(() -> service.googleLogin(request())).extracting("errorCode").isEqualTo(code);
        verify(users, never()).save(any());
        verify(tokens, never()).createAccessToken(any());
    }

    private static GoogleIdentityVerifier.VerifiedIdentity identity() {
        return new GoogleIdentityVerifier.VerifiedIdentity("google-sub", "owner@example.test", "테스트");
    }

    private static User user(Long id, AuthProvider provider, String subject, String email) {
        User user = User.builder().provider(provider).providerId(subject).email(email).username("테스트").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static GoogleLoginRequest request() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        ReflectionTestUtils.setField(request, "idToken", "id-credential");
        return request;
    }
}
