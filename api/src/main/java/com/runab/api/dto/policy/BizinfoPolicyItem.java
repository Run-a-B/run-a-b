package com.runab.api.dto.policy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기업마당(bizinfo) OpenAPI 응답 1건 매핑용 DTO.
 * 응답 필드가 많지만 우리 Policy로 매핑에 필요한 필드만 받는다.
 * (응답 필드명이 camelCase라 별도 @JsonProperty 없이 필드명으로 자동 매핑됨)
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 안 쓰는 필드(inqireCo, totCnt 등)는 무시
public class BizinfoPolicyItem {

    private String pblancId;                     // 공고 ID (PBLN_...) → externalId
    private String pblancNm;                     // 공고명 → title
    private String bsnsSumryCn;                  // 사업요약(HTML 포함) → description
    private String pldirSportRealmLclasCodeNm;   // 지원분야 대분류 → category / filterCategory
    private String hashtags;                     // 해시태그(콤마구분) → region 추출
    private String jrsdInsttNm;                  // 소관기관 → agency
    private String reqstBeginEndDe;              // 신청기간 "2026-06-17 ~ 2026-07-10" → start/end
    private String creatPnttm;                   // 등록일시 → publishedDate
    private String pblancUrl;                    // 상세페이지 URL → detailUrl
    private String reqstMthPapersCn;             // 신청방법 → applicationMethod
    private String rceptEngnHmpgUrl;             // 접수기관 홈페이지 → applicationUrl
    private String trgetNm;                      // 지원대상 → targetGroup
}
