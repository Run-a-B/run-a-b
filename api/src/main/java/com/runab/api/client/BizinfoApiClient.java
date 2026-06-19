package com.runab.api.client;

import com.runab.api.dto.policy.BizinfoApiResponse;
import com.runab.api.dto.policy.BizinfoPolicyItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

/**
 * 기업마당(bizinfo) OpenAPI 호출 클라이언트.
 * Spring 6의 RestClient 사용 (RestTemplate 아님).
 */
@Slf4j
@Component
public class BizinfoApiClient {

    private final RestClient restClient;
    private final String apiUrl;
    private final String apiKey;

    public BizinfoApiClient(
            @Value("${bizinfo.api.url}") String apiUrl,
            @Value("${bizinfo.api.key}") String apiKey) {
        this.restClient = RestClient.create();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    /**
     * 전체 정책 목록을 받아온다.
     * searchCnt는 생략(전체 받기) — 약 1,545건이라 페이징 없이 한 번에 받아도 부담 없음.
     * 호출 실패/타임아웃 시 예외를 던지지 않고 빈 리스트를 반환한다(동기화가 통째로 죽지 않게).
     */
    public List<BizinfoPolicyItem> fetchAllPolicies() {
        try {
            BizinfoApiResponse response = restClient.get()
                    .uri(apiUrl + "?crtfcKey={key}&dataType=json", apiKey)
                    .retrieve()
                    .body(BizinfoApiResponse.class);

            if (response == null || response.getJsonArray() == null) {
                log.warn("[bizinfo] 응답이 비어있습니다.");
                return Collections.emptyList();
            }
            log.info("[bizinfo] 정책 {}건 수신", response.getJsonArray().size());
            return response.getJsonArray();
        } catch (Exception e) {
            log.error("[bizinfo] API 호출 실패: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
