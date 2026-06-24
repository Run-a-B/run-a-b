package com.runab.api.service.recommend;

import com.runab.api.dto.recommend.MatchResult;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.PolicyCard;
import com.runab.api.service.matcher.EligibilityMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 모든 후보 카드에 matcher를 돌려 점수를 매기고, AI에 보낼 30개를 선별한다.
 *   - score 상위 20개 (hardFail 아닌 것)
 *   - 애매/hard negative 5개 (partial·fail 섞인 것)
 *   - 목표 카테고리 후보 5개 (goalScore 기준 — MVP는 점수순 단순화)
 *
 * ⚠️ PolicyCard 데이터가 0개여도 빈 리스트로 정상 동작한다 (현재 데이터 미적재 상태가 정상).
 */
@Component
@RequiredArgsConstructor
public class CandidateSelector {

    private static final int TOP_N = 20;
    private static final int AMBIGUOUS_N = 5;
    private static final int GOAL_N = 5;
    private static final int MAX_TOTAL = 30;

    private final EligibilityMatcher matcher;

    public List<ScoredCandidate> select(BusinessInfo user, List<PolicyCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }

        // 1) 전 카드 점수 계산
        List<ScoredCandidate> scored = new ArrayList<>();
        for (PolicyCard card : cards) {
            scored.add(new ScoredCandidate(card, matcher.match(user, card)));
        }

        // 2) hardFail 분리
        List<ScoredCandidate> ok = new ArrayList<>();
        List<ScoredCandidate> hardFail = new ArrayList<>();
        for (ScoredCandidate sc : scored) {
            (sc.match().isHardFail() ? hardFail : ok).add(sc);
        }

        // 점수순 정렬
        ok.sort(Comparator.comparingInt((ScoredCandidate s) -> s.match().getScore()).reversed());

        // 중복 방지를 위해 LinkedHashMap(키: PolicyCard identity)로 누적
        Map<PolicyCard, ScoredCandidate> selected = new LinkedHashMap<>();

        // 3) score 상위 20 (hardFail 제외)
        ok.stream().limit(TOP_N).forEach(s -> selected.put(s.card(), s));

        // 4) 애매/hard negative 5 — partial+fail 항목이 많은 순. ok 잔여 + hardFail 풀에서.
        List<ScoredCandidate> ambiguousPool = new ArrayList<>();
        ambiguousPool.addAll(ok);
        ambiguousPool.addAll(hardFail);
        ambiguousPool.stream()
                .filter(s -> !selected.containsKey(s.card()))
                .sorted(Comparator
                        .comparingInt(EligibilityMatcherAmbiguity::of).reversed()
                        .thenComparing(s -> s.match().getScore(), Comparator.reverseOrder()))
                .limit(AMBIGUOUS_N)
                .forEach(s -> selected.put(s.card(), s));

        // 5) 목표 카테고리 후보 5 — goalScore 높은 순 (MVP: goalScore 중립값이라 사실상 점수순)
        ok.stream()
                .filter(s -> !selected.containsKey(s.card()))
                .sorted(Comparator
                        .comparingInt((ScoredCandidate s) -> s.match().getGoalScore()).reversed()
                        .thenComparing(s -> s.match().getScore(), Comparator.reverseOrder()))
                .limit(GOAL_N)
                .forEach(s -> selected.put(s.card(), s));

        // 6) 최대 30개로 컷
        return selected.values().stream().limit(MAX_TOTAL).toList();
    }

    // 애매도: partial + fail 항목 수가 많을수록 hard negative/경계 후보
    private static class EligibilityMatcherAmbiguity {
        static int of(ScoredCandidate s) {
            MatchResult m = s.match();
            int partial = m.getPartial() == null ? 0 : m.getPartial().size();
            int fail = m.getFail() == null ? 0 : m.getFail().size();
            return partial + fail;
        }
    }
}
