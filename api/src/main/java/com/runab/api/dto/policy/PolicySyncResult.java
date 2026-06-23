package com.runab.api.dto.policy;

import lombok.Getter;

/**
 * bizinfo 동기화 결과 집계.
 */
@Getter
public class PolicySyncResult {

    private int newCount;       // 신규 저장 건수
    private int updatedCount;   // 갱신 건수
    private int totalCount;     // 전체 처리 건수

    public void countNew() {
        this.newCount++;
        this.totalCount++;
    }

    public void countUpdated() {
        this.updatedCount++;
        this.totalCount++;
    }
}
