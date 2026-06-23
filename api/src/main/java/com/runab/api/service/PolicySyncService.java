package com.runab.api.service;

import com.runab.api.client.BizinfoApiClient;
import com.runab.api.dto.policy.BizinfoPolicyItem;
import com.runab.api.dto.policy.PolicySyncResult;
import com.runab.api.entity.Policy;
import com.runab.api.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * 기업마당(bizinfo) 정책을 받아와 Policy 테이블에 동기화한다.
 * pblancId(externalId)로 중복을 판단하여 있으면 update, 없으면 insert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicySyncService {

    private static final String SOURCE_SYSTEM = "bizinfo";
    private static final String BIZINFO_BASE_URL = "https://www.bizinfo.go.kr";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 지역 단축명 → 정식 명칭 정규화 (PolicySpecification의 region 값과 일치시킴)
    private static final Map<String, String> REGION_MAP = Map.ofEntries(
            Map.entry("서울", "서울특별시"), Map.entry("부산", "부산광역시"),
            Map.entry("대구", "대구광역시"), Map.entry("인천", "인천광역시"),
            Map.entry("광주", "광주광역시"), Map.entry("대전", "대전광역시"),
            Map.entry("울산", "울산광역시"), Map.entry("세종", "세종특별자치시"),
            Map.entry("경기", "경기도"), Map.entry("강원", "강원특별자치도"),
            Map.entry("충북", "충청북도"), Map.entry("충남", "충청남도"),
            Map.entry("전북", "전북특별자치도"), Map.entry("전남", "전라남도"),
            Map.entry("경북", "경상북도"), Map.entry("경남", "경상남도"),
            Map.entry("제주", "제주특별자치도")
    );

    private final BizinfoApiClient bizinfoApiClient;
    private final PolicyRepository policyRepository;

    @Transactional
    public PolicySyncResult syncPolicies() {
        PolicySyncResult result = new PolicySyncResult();

        for (BizinfoPolicyItem item : bizinfoApiClient.fetchAllPolicies()) {
            if (item.getPblancId() == null || item.getPblancId().isBlank()) {
                continue; // 식별자 없는 항목은 중복 판단 불가 → skip
            }

            String title = truncate(item.getPblancNm(), 500);
            String category = item.getPldirSportRealmLclasCodeNm();
            String description = stripHtml(item.getBsnsSumryCn());
            String region = extractRegion(item.getHashtags());
            String agency = truncate(item.getJrsdInsttNm(), 100);
            LocalDate startDate = parsePeriod(item.getReqstBeginEndDe(), true);
            LocalDate endDate = parsePeriod(item.getReqstBeginEndDe(), false);
            LocalDate publishedDate = parseDate(item.getCreatPnttm());
            String targetGroup = truncate(item.getTrgetNm(), 200);
            String applicationMethod = stripHtml(item.getReqstMthPapersCn());
            String applicationUrl = fitUrl(item.getRceptEngnHmpgUrl(), 500);
            String detailUrl = fitUrl(toAbsoluteUrl(item.getPblancUrl()), 500);

            Optional<Policy> existing = policyRepository.findByExternalId(item.getPblancId());
            if (existing.isPresent()) {
                existing.get().updateFromBizinfo(title, category, category, region, "전체", description,
                        agency, startDate, endDate, publishedDate, targetGroup,
                        applicationMethod, applicationUrl, detailUrl, SOURCE_SYSTEM);
                result.countUpdated();
            } else {
                Policy policy = Policy.builder()
                        .externalId(item.getPblancId())
                        .title(title)
                        .category(category)
                        .filterCategory(category)
                        .region(region)
                        .industry("전체") // 자격요건 매핑은 policy_card 단계에서 처리
                        .description(description)
                        .agency(agency)
                        .applicationStartDate(startDate)
                        .applicationEndDate(endDate)
                        .publishedDate(publishedDate)
                        .targetGroup(targetGroup)
                        .applicationMethod(applicationMethod)
                        .applicationUrl(applicationUrl)
                        .detailUrl(detailUrl)
                        .sourceSystem(SOURCE_SYSTEM)
                        .build();
                policyRepository.save(policy);
                result.countNew();
            }
        }

        log.info("[bizinfo] 동기화 완료 - 신규 {}건, 갱신 {}건, 전체 {}건",
                result.getNewCount(), result.getUpdatedCount(), result.getTotalCount());
        return result;
    }

    // ===== 변환 헬퍼 =====

    // HTML 태그 제거 + 공백 정리 (Jsoup 등 추가 의존성 없이 정규식으로 처리)
    private String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return html.replaceAll("<[^>]*>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // length 초과 시 잘라낸다
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    // hashtags("기술,전남,로봇,2026")에서 아는 지역명을 찾아 정규화. 없으면 "전국"
    private String extractRegion(String hashtags) {
        if (hashtags == null || hashtags.isBlank()) {
            return "전국";
        }
        for (String tag : hashtags.split(",")) {
            String trimmed = tag.trim();
            if (REGION_MAP.containsKey(trimmed)) {
                return REGION_MAP.get(trimmed);
            }
        }
        return "전국";
    }

    // "2026-06-17 ~ 2026-07-10"를 " ~ "로 split. begin=true면 시작일, false면 종료일. 실패 시 null
    private LocalDate parsePeriod(String period, boolean begin) {
        if (period == null || !period.contains("~")) {
            return null;
        }
        String[] parts = period.split("~");
        String target = begin ? parts[0] : (parts.length > 1 ? parts[1] : null);
        return parseDate(target);
    }

    // "yyyy-MM-dd ..." 형태에서 날짜 부분만 LocalDate로. 실패 시 null (예외 던지지 않음)
    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String datePart = raw.trim().split("\\s+")[0]; // 날짜+시간이면 날짜 부분만
            return LocalDate.parse(datePart, DATE_FORMATTER);
        } catch (Exception e) {
            return null; // 1건 파싱 실패가 전체 동기화를 막지 않도록 안전 처리
        }
    }

    // URL은 잘라내면 깨진 링크가 되므로, 컬럼 길이를 초과하면 저장하지 않고 null 처리
    private String fitUrl(String url, int maxLength) {
        if (url == null || url.length() > maxLength) {
            return null;
        }
        return url;
    }

    // 상대경로면 bizinfo 도메인을 붙여 절대경로화
    private String toAbsoluteUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.startsWith("http") ? url : BIZINFO_BASE_URL + url;
    }
}
