package com.runab.api.service.matcher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionHierarchyTest {

    @Test
    void explicitProvinceDisambiguatesDistrict() {
        assertThat(RegionHierarchy.provincesOf("부산광역시 강서구"))
                .containsExactly("부산광역시");
        assertThat(RegionHierarchy.provincesOf("부산 강서구"))
                .containsExactly("부산광역시");
        assertThat(RegionHierarchy.sameProvince("서울특별시", "부산광역시 강서구")).isFalse();
    }

    @Test
    void districtWithoutProvincePreservesAmbiguity() {
        assertThat(RegionHierarchy.provincesOf("강서구"))
                .containsExactlyInAnyOrder("서울특별시", "부산광역시");
        assertThat(RegionHierarchy.provincesOf("미확인 지역")).isEmpty();
    }
}
