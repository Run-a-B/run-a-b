package com.runab.api.dto.policy;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 정책 목록 페이징 응답.
 * 프론트가 페이징 UI(총 개수, 총 페이지)를 그리므로 메타데이터를 포함한다.
 */
@Getter
@Builder
public class PolicyPageResponse {

    private List<PolicyListResponse> policies;
    private int currentPage;     // 1-based (프론트 기준)
    private int totalPages;
    private long totalElements;
    private int pageSize;

    public static PolicyPageResponse from(Page<PolicyListResponse> page) {
        return PolicyPageResponse.builder()
                .policies(page.getContent())
                // Spring Pageable은 0-based이므로 +1 해서 1-based로 돌려준다
                .currentPage(page.getNumber() + 1)
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .pageSize(page.getSize())
                .build();
    }
}
