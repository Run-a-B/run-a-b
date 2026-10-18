package com.runab.api.service.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.runab.api.entity.Policy;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The model selects whole source passages; it cannot author dates, amounts or eligibility facts. */
public final class SummarySource {
    public record Passage(String id, String text) {}

    private final List<Passage> passages = new ArrayList<>();
    private final Set<String> explicitConditionIds = new LinkedHashSet<>();
    private int characters;

    public SummarySource(Policy policy) {
        String notice = firstPresent(policy.getOriginalNoticeText(), policy.getDescription());
        if (notice != null) {
            // Keep sub-bullets together: splitting on every newline loses OR branches and exceptions.
            String[] sections = notice.contains("☞") ? notice.split("☞", -1) : notice.split("\\n\\s*\\n");
            for (int i = 0; i < sections.length; i++) {
                String id = "notice-" + i;
                boolean added = add(id, sections[i]);
                // Explicit eligibility wording is retained in both bullet blocks and plain paragraphs.
                // Paragraph order and words such as "기업당 100만원" do not establish eligibility.
                if (added && isExplicitCondition(sections[i].trim())) {
                    explicitConditionIds.add(id);
                }
            }
        }
        add("purpose", policy.getPurposeText());
        add("support", policy.getSupportScale());
        add("target", policy.getTargetGroup());
    }

    private boolean isExplicitCondition(String text) {
        return text.matches("(?s)^(지원\\s*대상|신청\\s*대상|참여\\s*대상|제외\\s*대상|지원\\s*제외|신청\\s*제외|지원\\s*자격|신청\\s*자격)\\s*[:：].*")
                || text.matches("(?s)^다음.*중\\s*하나.*해당.*")
                || text.matches("(?s).*(요건|조건)\\s*중\\s*하나.*해당.*");
    }

    private boolean add(String id, String value) {
        if (value == null || value.isBlank()) return false;
        String text = value.trim();
        // Never cut a condition mid-sentence to fit the prompt. Omit an oversized block instead.
        if (text.length() > 12_000 || characters + text.length() > 36_000) return false;
        passages.add(new Passage(id, text));
        characters += text.length();
        return true;
    }

    public List<Passage> passages() { return List.copyOf(passages); }

    public List<Passage> eligibilityPassages() {
        return passages.stream().filter(p -> explicitConditionIds.contains(p.id())).toList();
    }

    public List<Passage> select(JsonNode ai, String field, int max) {
        JsonNode ids = ai.path(field);
        if (!ids.isArray() || ids.size() > max) throw invalid();
        List<Passage> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode id : ids) {
            if (!id.isTextual() || !seen.add(id.asText())) throw invalid();
            Passage passage = passages.stream().filter(p -> p.id().equals(id.asText())).findFirst().orElseThrow(SummarySource::invalid);
            if (field.equals("support_ids") && explicitConditionIds.contains(passage.id())) throw invalid();
            result.add(passage);
        }
        if (field.equals("eligibility_ids") && !explicitConditionIds.isEmpty()) {
            // Preserve both the model's selected conditions and every explicitly labelled target/exclusion.
            // A target block never cancels another paragraph containing an exception.
            return passages.stream().filter(p -> seen.contains(p.id()) || explicitConditionIds.contains(p.id())).toList();
        }
        return result;
    }

    String expanded(List<Passage> overview, List<Passage> support, List<Passage> eligibility) {
        Set<Passage> selected = new LinkedHashSet<>(overview);
        selected.addAll(support);
        selected.addAll(eligibility);
        String text = passages.stream().filter(selected::contains).map(Passage::text).collect(Collectors.joining("\n\n"));
        return text.isBlank() ? null : text;
    }

    static String join(List<Passage> passages, String fallback) {
        return passages.isEmpty() ? fallback : passages.stream().map(Passage::text).collect(Collectors.joining("\n"));
    }

    public static String applicationPeriod(Policy policy) {
        String original = firstPresent(policy.getOriginalApplicationPeriod());
        if (original != null) return original;
        if (policy.getApplicationStartDate() != null || policy.getApplicationEndDate() != null) {
            return (policy.getApplicationStartDate() == null ? "시작일 미확인" : policy.getApplicationStartDate())
                    + " ~ " + (policy.getApplicationEndDate() == null ? "종료일 미확인" : policy.getApplicationEndDate());
        }
        return "수집된 정보에 신청 기간이 없습니다. 원문 공고를 확인해 주세요.";
    }

    public static String firstPresent(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    private static BusinessException invalid() { return new BusinessException(ErrorCode.AI_INVALID_RESPONSE); }
}
