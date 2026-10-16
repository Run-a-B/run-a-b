# Policy Detail Enrichment and Honest Relevance Design

**Date:** 2026-10-16

## Problem

The live database contains policies but no `policy_card` rows. The list matcher therefore classifies the same high-weight fields as unknown for every policy and emits the same 37% score. Anonymous users also receive a numeric 0%, which looks like a real negative judgment even though no comparison was performed. Policy detail pages expose the stored source description as one dense paragraph, so useful target and support clauses are hard to scan.

## Goals

- Make relevance policy-specific even while structured card extraction is unavailable.
- Use only stored policy facts and the signed-in business profile; never infer unsupported eligibility facts.
- Distinguish “not analyzed” from a real 0% score.
- Make policy details substantially easier to scan without inventing or rewriting source facts.
- Preserve the current Run-a-B card, color, typography, and layout language.

## Relevance Rules

`PolicyCard` remains authoritative when it contains a usable value. Only an `UNKNOWN` region, industry, or date result may fall back to `Policy` metadata.

- **Region:** `전국` or the same province is `PASS`. A different metadata region stays `UNKNOWN`, because metadata alone may be incomplete; it is not a hard failure.
- **Industry:** an explicit non-`전체` stored industry is compared with `IndustryHierarchy`. A mismatch is `FAIL`. When the stored industry is broad, the existing conservative text matcher may return `PASS`; otherwise it stays `UNKNOWN`.
- **Application date:** an end date before today is `FAIL` and a hard failure; a future start date is `PARTIAL`; a currently active range or one-sided current date is `PASS`; no dates is `UNKNOWN`.
- All other card-only criteria retain their current behavior. Missing evidence remains `UNKNOWN` or `NOT_REQUIRED` according to the existing matcher contract.
- Anonymous users or users without business information receive `relevance: null`, rendered as `로그인 후 분석`, not `0%`.

The relevance value is a deterministic condition-match score, not an AI probability or final eligibility decision.

## Detail Presentation

The stored description is split only on explicit source markers (`☞`) and line/bullet separators. Text before the first marker becomes **사업 개요**; marked clauses become ordered **공고문 핵심 항목**. If there are no markers, the original description remains the overview. Empty and duplicate clauses are removed without altering the remaining wording.

The detail page also shows the stored target group, application period, application method, and official source link in a structured section. Missing fields are omitted or described as unavailable; no amounts, deadlines, eligibility conditions, or labels are fabricated. The official notice remains the final authority.

## Success Criteria

- Unit tests prove two policies without cards can produce different region/date-aware matcher results.
- Existing card-backed matcher behavior stays compatible.
- The authenticated live list no longer collapses every visible policy to one score when metadata differs.
- Anonymous list cards do not show a fake 0% score.
- A real description containing `☞` renders its overview and every stored clause as separate, readable content.
- Backend tests, frontend tests, build, and a live API/browser smoke check pass.

