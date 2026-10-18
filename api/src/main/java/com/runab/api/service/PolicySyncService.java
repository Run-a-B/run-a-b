package com.runab.api.service;

import com.runab.api.client.BizinfoApiClient;
import com.runab.api.dto.policy.BizinfoPolicyItem;
import com.runab.api.dto.policy.PolicySyncResult;
import com.runab.api.entity.Policy;
import com.runab.api.repository.PolicyRepository;
import com.runab.api.service.matcher.RegionHierarchy;
import com.runab.api.service.source.BizinfoSourceTextParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
            String synopsis = BizinfoSourceTextParser.toText(item.getBsnsSumryCn());
            String description = synopsis == null ? null : truncate(synopsis.replaceAll("\\s+", " "), 1000);
            String originalNoticeText = BizinfoSourceTextParser.toText(item.getDescription());
            String originalNoticeHtml = BizinfoSourceTextParser.hasSubstantiveContent(item.getDescription())
                    ? item.getDescription() : null;
            String region = extractRegion(item.getHashtags(), item.getPblancNm());
            String agency = truncate(item.getJrsdInsttNm(), 100);
            String originalPeriod = firstNonBlank(item.getReqstDt(), item.getReqstBeginEndDe());
            LocalDate startDate = parsePeriod(originalPeriod, true);
            LocalDate endDate = parsePeriod(originalPeriod, false);
            LocalDate publishedDate = parseDate(item.getCreatPnttm());
            String targetGroup = truncate(item.getTrgetNm(), 200);
            String applicationMethod = BizinfoSourceTextParser.toText(item.getReqstMthPapersCn());
            String applicationUrl = fitUrl(item.getRceptEngnHmpgUrl(), 500);
            String detailUrl = fitUrl(toAbsoluteUrl(item.getPblancUrl()), 500);
            String executingAgency = BizinfoSourceTextParser.toText(item.getExcInsttNm());
            String inquiryContact = BizinfoSourceTextParser.toText(item.getRefrncNm());
            String attachmentName = firstNonBlank(item.getFileNm(), null);
            String attachmentUrl = toAbsoluteUrl(item.getFlpthNm());
            String noticeFileName = firstNonBlank(item.getPrintFileNm(), null);
            String noticeFileUrl = toAbsoluteUrl(item.getPrintFlpthNm());

            Optional<Policy> existing = policyRepository.findByExternalId(item.getPblancId());
            if (existing.isPresent()) {
                Policy policy = existing.get();
                if (originalPeriod == null || originalPeriod.isBlank()) {
                    startDate = policy.getApplicationStartDate();
                    endDate = policy.getApplicationEndDate();
                }
                policy.updateFromBizinfo(title, category, category, region, "전체", description,
                        agency, startDate, endDate, publishedDate, targetGroup,
                        applicationMethod, applicationUrl, detailUrl, SOURCE_SYSTEM);
                String sourceText = originalNoticeText;
                if (sourceText == null && (originalNoticeHtml != null || policy.getOriginalNoticeHtml() == null)) {
                    sourceText = synopsis;
                }
                policy.updateSourceFromBizinfo(originalNoticeHtml, sourceText, applicationMethod, originalPeriod,
                        executingAgency, inquiryContact, attachmentName, attachmentUrl, noticeFileName, noticeFileUrl);
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
                        .originalNoticeHtml(originalNoticeHtml)
                        .originalNoticeText(firstNonBlank(originalNoticeText, synopsis))
                        .originalApplicationMethod(applicationMethod)
                        .originalApplicationPeriod(originalPeriod)
                        .executingAgency(executingAgency)
                        .inquiryContact(inquiryContact)
                        .attachmentName(attachmentName)
                        .attachmentUrl(attachmentUrl)
                        .noticeFileName(noticeFileName)
                        .noticeFileUrl(noticeFileUrl)
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

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    // length 초과 시 잘라낸다
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    // 정확한 지역 태그를 우선 사용하고, 전남광주 결합 태그는 명확한 제목 근거가 있어야 한다.
    private String extractRegion(String hashtags, String title) {
        if (hashtags == null || hashtags.isBlank()) {
            return "전국";
        }
        boolean combinedRegion = false;
        for (String tag : hashtags.split(",")) {
            String trimmed = tag.trim();
            if (REGION_MAP.containsKey(trimmed)) {
                return REGION_MAP.get(trimmed);
            }
            if (REGION_MAP.containsValue(trimmed)) return trimmed;
            if (trimmed.equals("전남광주")) combinedRegion = true;
        }
        if (combinedRegion) return resolveCombinedRegion(title);
        return "전국";
    }

    private String resolveCombinedRegion(String title) {
        if (title == null || title.isBlank()) return null;
        Set<String> candidates = new HashSet<>();
        for (String token : title.split("[^가-힣]+")) {
            Set<String> provinces = RegionHierarchy.provincesOf(token);
            if (provinces.size() == 1) candidates.addAll(provinces);
        }
        if (candidates.size() != 1) return null;
        String region = candidates.iterator().next();
        return Set.of("전라남도", "광주광역시").contains(region) ? region : null;
    }

    // "2026-06-17 ~ 2026-07-10"를 " ~ "로 split. begin=true면 시작일, false면 종료일. 실패 시 null
    private LocalDate parsePeriod(String period, boolean begin) {
        if (period == null || !period.contains("~")) {
            return null;
        }
        String[] parts = period.split("~", -1);
        String target = begin ? parts[0] : (parts.length > 1 ? parts[1] : null);
        return parseDate(target);
    }

    // yyyy-MM-dd / yyyyMMdd 및 날짜 뒤 시간 지원. 실패 시 null (예외 던지지 않음)
    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String datePart = raw.trim().split("\\s+")[0]; // 날짜+시간이면 날짜 부분만
            DateTimeFormatter formatter = datePart.matches("\\d{8}")
                    ? DateTimeFormatter.BASIC_ISO_DATE : DateTimeFormatter.ISO_LOCAL_DATE;
            return LocalDate.parse(datePart, formatter);
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
        try {
            URI absolute = URI.create(BIZINFO_BASE_URL + "/").resolve(url.trim());
            return "https".equalsIgnoreCase(absolute.getScheme()) || "http".equalsIgnoreCase(absolute.getScheme())
                    ? absolute.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
