package com.runab.api.service;

import com.runab.api.dto.user.BusinessInfoUpdateRequest;
import com.runab.api.entity.AuthProvider;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.User;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceBusinessInfoTest {
    @Mock UserRepository users;
    @Mock BusinessInfoRepository businesses;
    @Mock PasswordEncoder passwords;
    UserService service;

    @BeforeEach void setup() {
        service = new UserService(users, businesses, passwords, new BusinessFieldConverter());
    }

    @ParameterizedTest
    @CsvSource({"region,INVALID_REGION", "industry,INVALID_JOB_CATEGORY"})
    void blankRequiredFieldDoesNotMutateExistingProfile(String field, String expectedError) {
        BusinessInfo existing = operatingProfile();
        given(businesses.findByUserId(7L)).willReturn(Optional.of(existing));
        BusinessInfoUpdateRequest request = new BusinessInfoUpdateRequest();
        if (field.equals("region")) request.setRegion("   ");
        else request.setJobCategory("   ");

        assertThatThrownBy(() -> service.updateMyBusinessInfo(7L, request))
                .isInstanceOf(BusinessException.class).extracting("errorCode.code").isEqualTo(expectedError);
        assertThat(existing.getRegion()).isEqualTo("서울특별시");
        assertThat(existing.getJobCategory()).isEqualTo("카페/음료");
    }

    @ParameterizedTest
    @CsvSource({"region,INVALID_REGION", "industry,INVALID_JOB_CATEGORY"})
    void valuesLongerThanDatabaseColumnsAreRejected(String field, String expectedError) {
        BusinessInfo existing = operatingProfile();
        given(businesses.findByUserId(7L)).willReturn(Optional.of(existing));
        BusinessInfoUpdateRequest request = new BusinessInfoUpdateRequest();
        if (field.equals("region")) request.setRegion("가".repeat(51));
        else request.setJobCategory("가".repeat(51));

        assertThatThrownBy(() -> service.updateMyBusinessInfo(7L, request))
                .isInstanceOf(BusinessException.class).extracting("errorCode.code").isEqualTo(expectedError);
        assertThat(existing.getRegion()).isEqualTo("서울특별시");
        assertThat(existing.getJobCategory()).isEqualTo("카페/음료");
    }

    @ParameterizedTest
    @ValueSource(strings = {"revenue", "employees"})
    void switchingToOperatingRequiresBothBandsBeforeChangingProfile(String omittedField) {
        BusinessInfo existing = BusinessInfo.builder().businessStatus(false)
                .jobCategory("카페/음료").region("서울특별시").build();
        given(businesses.findByUserId(7L)).willReturn(Optional.of(existing));
        BusinessInfoUpdateRequest request = new BusinessInfoUpdateRequest();
        request.setBusinessStatus(true);
        if (!omittedField.equals("revenue")) request.setAnnualRevenue("1억원~3억원");
        if (!omittedField.equals("employees")) request.setEmployeeCount("1~4명");

        assertThatThrownBy(() -> service.updateMyBusinessInfo(7L, request))
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_FIELD_REQUIRED);
        assertThat(existing.getBusinessStatus()).isFalse();
        assertThat(existing.getAnnualRevenue()).isNull();
        assertThat(existing.getEmployeeCount()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"revenue", "employees"})
    void explicitBlankOperatingBandIsRejectedInsteadOfRetainingOldValue(String field) {
        BusinessInfo existing = operatingProfile();
        given(businesses.findByUserId(7L)).willReturn(Optional.of(existing));
        BusinessInfoUpdateRequest request = new BusinessInfoUpdateRequest();
        if (field.equals("revenue")) request.setAnnualRevenue("   ");
        else request.setEmployeeCount("   ");

        assertThatThrownBy(() -> service.updateMyBusinessInfo(7L, request))
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_FIELD_REQUIRED);
        assertThat(existing.getAnnualRevenue()).isEqualTo(200_000_000L);
        assertThat(existing.getEmployeeCount()).isEqualTo(2);
    }

    @Test void firstOperatingProfileWithoutBandsIsRejectedBeforeInsert() {
        BusinessInfoUpdateRequest request = new BusinessInfoUpdateRequest();
        request.setBusinessStatus(true);
        request.setRegion("서울특별시");
        request.setJobCategory("카페/음료");
        lenient().when(users.findById(7L)).thenReturn(Optional.of(user()));
        lenient().when(businesses.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> service.updateMyBusinessInfo(7L, request))
                .extracting("errorCode").isEqualTo(ErrorCode.BUSINESS_FIELD_REQUIRED);
        verify(businesses, never()).save(any());
    }

    @Test void omittedFieldsRetainExistingBandsDuringPartialUpdate() {
        BusinessInfo existing = operatingProfile();
        given(businesses.findByUserId(7L)).willReturn(Optional.of(existing));
        BusinessInfoUpdateRequest request = new BusinessInfoUpdateRequest();
        request.setRegion("부산광역시");

        var response = service.updateMyBusinessInfo(7L, request);

        assertThat(response.getRegion()).isEqualTo("부산광역시");
        assertThat(response.getBusinessStatus()).isTrue();
        assertThat(response.getJobCategory()).isEqualTo("카페/음료");
        assertThat(response.getAnnualRevenue()).isEqualTo("1억원~3억원");
        assertThat(response.getEmployeeCount()).isEqualTo("1~4명");
    }

    @Test void switchingToPreparingClearsStoredBandsEvenWhenClientSendsPreviousSelections() {
        BusinessInfo existing = operatingProfile();
        given(businesses.findByUserId(7L)).willReturn(Optional.of(existing));
        BusinessInfoUpdateRequest request = new BusinessInfoUpdateRequest();
        request.setBusinessStatus(false);
        request.setAnnualRevenue("1억원~3억원");
        request.setEmployeeCount("1~4명");

        var response = service.updateMyBusinessInfo(7L, request);

        assertThat(response.getBusinessStatus()).isFalse();
        assertThat(response.getAnnualRevenue()).isNull();
        assertThat(response.getEmployeeCount()).isNull();
        assertThat(existing.getAnnualRevenue()).isNull();
        assertThat(existing.getEmployeeCount()).isNull();
    }

    @Test void nonblankLegacyLabelsRemainAccepted() {
        BusinessInfo existing = operatingProfile();
        given(businesses.findByUserId(7L)).willReturn(Optional.of(existing));
        BusinessInfoUpdateRequest request = new BusinessInfoUpdateRequest();
        request.setRegion("서울");
        request.setJobCategory("카페/베이커리");

        var response = service.updateMyBusinessInfo(7L, request);

        assertThat(response.getRegion()).isEqualTo("서울");
        assertThat(response.getJobCategory()).isEqualTo("카페/베이커리");
    }

    @Test void validNewOperatingProfileStoresCanonicalBands() {
        given(users.findById(7L)).willReturn(Optional.of(user()));
        given(businesses.save(any())).willAnswer(call -> call.getArgument(0));
        BusinessInfoUpdateRequest request = new BusinessInfoUpdateRequest();
        request.setBusinessStatus(true);
        request.setRegion("서울특별시");
        request.setJobCategory("카페/음료");
        request.setAnnualRevenue("1억~3억");
        request.setEmployeeCount("1~4명");

        var response = service.updateMyBusinessInfo(7L, request);

        assertThat(response.getAnnualRevenue()).isEqualTo("1억원~3억원");
        assertThat(response.getEmployeeCount()).isEqualTo("1~4명");
        ArgumentCaptor<BusinessInfo> saved = ArgumentCaptor.forClass(BusinessInfo.class);
        verify(businesses).save(saved.capture());
        assertThat(saved.getValue().getAnnualRevenue()).isEqualTo(200_000_000L);
        assertThat(saved.getValue().getEmployeeCount()).isEqualTo(2);
    }

    private BusinessInfo operatingProfile() {
        return BusinessInfo.builder().businessStatus(true).region("서울특별시").jobCategory("카페/음료")
                .annualRevenue(200_000_000L).employeeCount(2).build();
    }

    private User user() {
        return User.builder().email("report-profile-test@example.com").username("사업자")
                .provider(AuthProvider.LOCAL).build();
    }
}
