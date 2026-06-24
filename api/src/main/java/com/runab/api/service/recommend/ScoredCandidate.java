package com.runab.api.service.recommend;

import com.runab.api.dto.recommend.MatchResult;
import com.runab.api.entity.PolicyCard;

/**
 * PolicyCard + 그에 대한 매칭 결과 묶음 (선별/조립 단계 내부용).
 */
public record ScoredCandidate(PolicyCard card, MatchResult match) {
}
