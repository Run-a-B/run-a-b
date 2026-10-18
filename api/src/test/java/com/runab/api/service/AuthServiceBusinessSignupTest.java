package com.runab.api.service;

import com.runab.api.dto.auth.SignupBusinessRequest;
import com.runab.api.entity.AuthProvider;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.User;
import com.runab.api.exception.ErrorCode;
import com.runab.api.jwt.JwtTokenProvider;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceBusinessSignupTest {

    @Mock private UserRepository userRepository;
    @Mock private BusinessInfoRepository businessInfoRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private EmailService emailService;

    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                userRepository,
                businessInfoRepository,
                passwordEncoder,
                jwtTokenProvider,
                new BusinessFieldConverter(),
                emailService
        );
        user = User.builder()
                .email("owner@example.com")
                .username("사장님")
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @ParameterizedTest
    @MethodSource("acceptedRevenueLabels")
    void signupBusinessPersistsEveryAcceptedRevenueLabel(String label, long storedValue) {
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(jwtTokenProvider.createAccessToken(7L)).willReturn("access-token");

        service.signupBusiness(7L, request(true, label, "1~4명"));

        ArgumentCaptor<BusinessInfo> saved = ArgumentCaptor.forClass(BusinessInfo.class);
        verify(businessInfoRepository).save(saved.capture());
        assertThat(saved.getValue().getAnnualRevenue()).isEqualTo(storedValue);
    }

    @Test
    void signupBusinessRejectsUnknownRevenueWithoutSaving() {
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.signupBusiness(
                7L,
                request(true, "지원하지 않는 구간", "1~4명")
        )).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(businessInfoRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void signupBusinessRejectsUnknownEmployeeCountWithoutSaving() {
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.signupBusiness(
                7L,
                request(true, "1억원~3억원", "직원 아주 많음")
        )).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(businessInfoRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @MethodSource("ambiguousFormerSignupRevenueLabels")
    void signupBusinessRejectsAmbiguousFormerRevenueLabels(String label) {
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.signupBusiness(7L, request(true, label, "1~4명")))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(businessInfoRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @MethodSource("missingOperatingFields")
    void operatingSignupRejectsEachMissingOperatingFieldAsRequired(
            String annualRevenue,
            String employeeCount
    ) {
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.signupBusiness(
                7L,
                request(true, annualRevenue, employeeCount)
        ))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUSINESS_FIELD_REQUIRED);

        verify(businessInfoRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @MethodSource("preparingRequestsWithOperatingFields")
    void preparingSignupRejectsEachOperatingOnlyValue(
            String annualRevenue,
            String employeeCount
    ) {
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.signupBusiness(
                7L,
                request(false, annualRevenue, employeeCount)
        )).extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_FIELD_NOT_ALLOWED);

        verify(businessInfoRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preparingSignupPersistsNullOperatingOnlyValues() {
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(jwtTokenProvider.createAccessToken(7L)).willReturn("access-token");

        service.signupBusiness(7L, request(false, null, null));

        ArgumentCaptor<BusinessInfo> saved = ArgumentCaptor.forClass(BusinessInfo.class);
        verify(businessInfoRepository).save(saved.capture());
        assertThat(saved.getValue().getBusinessStatus()).isFalse();
        assertThat(saved.getValue().getAnnualRevenue()).isNull();
        assertThat(saved.getValue().getEmployeeCount()).isNull();
    }

    private static SignupBusinessRequest request(
            boolean businessStatus,
            String annualRevenue,
            String employeeCount
    ) {
        SignupBusinessRequest request = new SignupBusinessRequest();
        request.setBusinessStatus(businessStatus);
        request.setJobCategory("카페/베이커리");
        request.setRegion("서울");
        request.setAnnualRevenue(annualRevenue);
        request.setEmployeeCount(employeeCount);
        return request;
    }

    static Stream<Arguments> acceptedRevenueLabels() {
        return Stream.of(
                Arguments.of("1,000만원 미만", 5_000_000L),
                Arguments.of("1,000만원~5,000만원", 30_000_000L),
                Arguments.of("5,000만원~1억원", 75_000_000L),
                Arguments.of("1억원~3억원", 200_000_000L),
                Arguments.of("3억원~5억원", 400_000_000L),
                Arguments.of("5억원 이상", 500_000_000L),
                Arguments.of("1억~3억", 200_000_000L),
                Arguments.of("3억~5억", 400_000_000L)
        );
    }

    static Stream<String> ambiguousFormerSignupRevenueLabels() {
        return Stream.of("1억 미만", "5억~10억", "10억 이상");
    }

    static Stream<Arguments> missingOperatingFields() {
        return Stream.of(
                Arguments.of(null, "1~4명"),
                Arguments.of("1억원~3억원", null),
                Arguments.of("   ", "1~4명"),
                Arguments.of("1억원~3억원", "   ")
        );
    }

    static Stream<Arguments> preparingRequestsWithOperatingFields() {
        return Stream.of(
                Arguments.of("1억원~3억원", null),
                Arguments.of(null, "1~4명"),
                Arguments.of("1억원~3억원", "1~4명")
        );
    }
}
