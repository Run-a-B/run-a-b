package com.runab.api.service;

import com.runab.api.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessFieldConverterTest {

    private final BusinessFieldConverter converter = new BusinessFieldConverter();

    @ParameterizedTest
    @MethodSource("canonicalRevenueBands")
    void everyCanonicalRevenueLabelRoundTripsExactly(String label, long storedValue) {
        assertThat(converter.parseRevenue(label)).isEqualTo(storedValue);
        assertThat(converter.formatRevenue(storedValue)).isEqualTo(label);
    }

    @ParameterizedTest
    @MethodSource("exactLegacyRevenueAliases")
    void exactLegacyRevenueAliasesAreStoredAndReturnedCanonically(
            String formerLabel,
            long storedValue,
            String canonicalLabel
    ) {
        assertThat(converter.parseRevenue(formerLabel)).isEqualTo(storedValue);
        assertThat(converter.formatRevenue(storedValue)).isEqualTo(canonicalLabel);
    }

    @ParameterizedTest
    @MethodSource("ambiguousFormerSignupRevenueLabels")
    void ambiguousFormerSignupRevenueLabelsAreRejected(String label) {
        assertThatThrownBy(() -> converter.parseRevenue(label))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void unknownNonblankRevenueLabelIsRejectedInsteadOfBecomingNull() {
        assertThatThrownBy(() -> converter.parseRevenue("지원하지 않는 구간"))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void absentRevenueRemainsAbsent() {
        assertThat(converter.parseRevenue(null)).isNull();
        assertThat(converter.parseRevenue("   ")).isNull();
    }

    @ParameterizedTest
    @MethodSource("canonicalEmployeeBands")
    void everyCanonicalEmployeeLabelRoundTripsExactly(String label, int storedValue) {
        assertThat(converter.parseEmployeeCount(label)).isEqualTo(storedValue);
        assertThat(converter.formatEmployeeCount(storedValue)).isEqualTo(label);
    }

    @Test
    void unknownNonblankEmployeeLabelIsRejectedInsteadOfBecomingNull() {
        assertThatThrownBy(() -> converter.parseEmployeeCount("직원 아주 많음"))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    static Stream<Arguments> canonicalRevenueBands() {
        return Stream.of(
                Arguments.of("1,000만원 미만", 5_000_000L),
                Arguments.of("1,000만원~5,000만원", 30_000_000L),
                Arguments.of("5,000만원~1억원", 75_000_000L),
                Arguments.of("1억원~3억원", 200_000_000L),
                Arguments.of("3억원~5억원", 400_000_000L),
                Arguments.of("5억원 이상", 500_000_000L)
        );
    }

    static Stream<Arguments> exactLegacyRevenueAliases() {
        return Stream.of(
                Arguments.of("1억~3억", 200_000_000L, "1억원~3억원"),
                Arguments.of("3억~5억", 400_000_000L, "3억원~5억원")
        );
    }

    static Stream<String> ambiguousFormerSignupRevenueLabels() {
        return Stream.of("1억 미만", "5억~10억", "10억 이상");
    }

    static Stream<Arguments> canonicalEmployeeBands() {
        return Stream.of(
                Arguments.of("없음 (나 혼자)", 0),
                Arguments.of("1~4명", 2),
                Arguments.of("5~9명", 7),
                Arguments.of("10~29명", 20),
                Arguments.of("30명 이상", 50)
        );
    }
}
