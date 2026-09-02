package com.runab.api.service.diagnosis;

import com.runab.api.service.matcher.MatchStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisInputNormalizerTest {

    @Test
    void revenueBandCanPassPartiallyOverlapOrFailAMaximum() {
        var normalizer = new DiagnosisInputNormalizer();
        assertThat(normalizer.revenueBand("1,000만원~5,000만원").compareToMaximum(50_000_000L))
                .isEqualTo(MatchStatus.PASS);
        assertThat(normalizer.revenueBand("5,000만원~1억원").compareToMaximum(75_000_000L))
                .isEqualTo(MatchStatus.PARTIAL);
        assertThat(normalizer.revenueBand("1억원~3억원").compareToMaximum(90_000_000L))
                .isEqualTo(MatchStatus.FAIL);
    }

    @Test
    void openEndedEmployeeBandPartiallyOverlapsOrFails() {
        var normalizer = new DiagnosisInputNormalizer();
        assertThat(normalizer.employeeBand("30명 이상").compareToMaximum(50L))
                .isEqualTo(MatchStatus.PARTIAL);
        assertThat(normalizer.employeeBand("30명 이상").compareToMaximum(20L))
                .isEqualTo(MatchStatus.FAIL);
    }
}
