package com.runab.api.dto.policy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기업마당(bizinfo) OpenAPI 응답 1건 매핑용 DTO.
 * 응답 필드가 많지만 우리 Policy로 매핑에 필요한 필드만 받는다.
 * 공식 원문 필드와 기존 응답의 별칭을 함께 받는다.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 안 쓰는 필드(inqireCo, totCnt 등)는 무시
public class BizinfoPolicyItem {

    private String pblancId;                     // 공고 ID (PBLN_...) → externalId
    private String pblancNm;                     // 공고명 → title
    private String bsnsSumryCn;                  // 사업요약(HTML 포함) → description
    private String description;                  // 공식 공고 본문(HTML)
    private String pldirSportRealmLclasCodeNm;   // 지원분야 대분류 → category / filterCategory
    @JsonAlias("hashTags")
    private String hashtags;                    // 공식 hashTags와 기존 hashtags 모두 수신
    private String jrsdInsttNm;                  // 소관기관 → agency
    private String reqstBeginEndDe;              // 신청기간 "2026-06-17 ~ 2026-07-10" → start/end
    private String reqstDt;                      // 공식 신청기간(원문 보존)
    private String creatPnttm;                   // 등록일시 → publishedDate
    private String pblancUrl;                    // 상세페이지 URL → detailUrl
    private String reqstMthPapersCn;             // 신청방법 → applicationMethod
    private String rceptEngnHmpgUrl;             // 접수기관 홈페이지 → applicationUrl
    private String trgetNm;                      // 지원대상 → targetGroup
    private String excInsttNm;                   // 수행기관
    private String refrncNm;                     // 문의처
    private String flpthNm;                      // 첨부파일 경로
    private String fileNm;                       // 첨부파일 이름
    private String printFlpthNm;                 // 공고문 파일 경로
    private String printFileNm;                  // 공고문 파일 이름
}
