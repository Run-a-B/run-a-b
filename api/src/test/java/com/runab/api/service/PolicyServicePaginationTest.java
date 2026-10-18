package com.runab.api.service;

import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.PolicyCardRepository;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.service.matcher.EligibilityMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PolicyServicePaginationTest {
    @Mock private PolicyRepository policies;
    @Mock private BusinessInfoRepository businessInfo;
    @Mock private PolicyCardRepository cards;
    @Mock private EligibilityMatcher matcher;
    private PolicyService service;

    @BeforeEach
    void setUp() {
        service = new PolicyService(policies, businessInfo, cards, matcher);
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 6", "-2147483648, 6", "0, 0", "0, -1", "0, 101",
            "0, 2147483647", "2147483647, 1", "2147483646, 6"
    })
    void rejectsInvalidOrUnboundedPaginationBeforeLoadingPolicies(int page, int size) {
        assertThatThrownBy(() -> service.getPolicies("전체 지역", "전체 업종", "전체 카테고리",
                null, "최신순", page, size, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(policies, businessInfo, cards, matcher);
    }
}
