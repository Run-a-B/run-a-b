# Policy Source Enrichment and Relevance Accuracy Design

## Intent

Run a B must show source-faithful policy details and rank policies in a way that does not mistake a local, expired, or clearly unrelated notice for a strong match. The current page renders everything it receives, but ingestion keeps only a sparse synopsis and discards official source metadata. The current relevance distribution is numerically varied but semantically wrong in several reproducible cases.

## Goals

- Preserve the richest content already returned by the official Bizinfo API without inventing missing facts.
- Expose the original overview, original application method and period, executing agency, inquiry contact, attachment and full-notice file links.
- Render original source material separately from AI-generated summaries or rewrites.
- Stop ambiguous `전남광주` notices from becoming nationwide matches when their title identifies a local district.
- Respect explicit province prefixes, explicit unrelated industries, structured dates and hard-fail availability when calculating or sorting relevance.
- Backfill the running local database through the existing sync endpoint and verify real demo-account results.

## Non-goals

- Do not crawl and download every PDF/HWP attachment during the 1,500-policy list sync.
- Do not claim that an attachment was parsed when only its official link is available.
- Do not populate announcement number, support amount, department or eligibility facts by guessing.
- Do not change the established score weights or the goal/document neutral values.
- Do not replace the current visual language or redesign the policy page.

## Source Contract

The official Bizinfo API documentation lists `description`, `bsnsSumryCn`, `reqstMthPapersCn`, `reqstDt`/`reqstBeginEndDe`, `excInsttNm`, `refrncNm`, `flpthNm`/`fileNm`, and `printFlpthNm`/`printFileNm`. Ingestion will capture these fields explicitly. `description` HTML is the preferred original overview when present; `bsnsSumryCn` remains the compact card synopsis and fallback.

HTML is parsed with jsoup into plain text while retaining paragraph, line-break, list and table-row boundaries. The raw original HTML is stored internally for future reprocessing but is never injected into the browser. Script/style content is discarded. Existing source text must not be deduplicated or rewritten in the original view.

The live Bizinfo feed uses a complete 16-token short-tag set (with `전남광주` representing two provinces) for nationwide scope. Ingestion therefore evaluates the regional tag set as a whole instead of taking the first tag. A single formal province name or an explicit leading title scope such as `[서울]` is stronger evidence; incidental place names later in a nationwide title or agency name must not narrow a nationwide notice. A combined leading scope such as `[전남광주]` is stored only when the remaining title evidence resolves it to one province, otherwise its region remains unknown.

The detail API will expose:

- `originalNoticeText`
- `originalApplicationMethod`
- `originalApplicationPeriod`
- `executingAgency`
- `inquiryContact`
- `sourceAttachments: [{ name, url, role }]`, with role `attachment` or `notice`

Only absolute HTTP(S) links are rendered. A missing richer field falls back to the existing legacy field without breaking old records.

## Relevance Rules

1. A recognized card remains authoritative for a field when it provides that field.
2. An explicit province prefix such as `부산광역시 강서구` resolves to that province before the ambiguous district name is considered.
3. Bizinfo's combined `전남광주` tag is disambiguated from an explicit leading title scope and reliable district evidence. If it cannot be disambiguated it remains unknown; it is never silently stored as `전국`. A complete regional tag set is nationwide unless a stronger formal province or leading scope is present.
4. A confidently resolved, non-nationwide province mismatch is FAIL and a hard failure. Ambiguous or absent region evidence remains UNKNOWN rather than causing exclusion.
5. Text fallback returns PASS for an explicit user-industry phrase, FAIL when a different supported or explicitly exclusive industry is named, and UNKNOWN when no industry evidence exists.
6. Effective dates are assembled field by field: valid card dates first, then stored policy dates for missing bounds. Effective closed/future dates override a stale `open` status; the status text is evaluated only after dates.
7. `관련도 높은순` lists non-hard-fail policies before hard-fail policies, then orders by score and existing stable date/id tie breakers. The published score formula is unchanged.

## UI Behavior

The current brief and AI summary remain. A source section shows the original notice as one ordered, whitespace-preserving body, followed by original application method, raw period, contact and attachments. AI-expanded method text may be shown only under an AI label; it must not replace or hide the stored original method.

Long text and URLs wrap at narrow widths. Legacy records without enrichment keep the current detailed notice presentation and original-source link.

## Verification

- TDD covers official payload deserialization, source formatting/preservation, compact dates, region disambiguation, explicit province precedence, industry tri-state, structured dates and hard-fail sorting.
- Backend and frontend full suites, type build and lint pass.
- Local sync backfills existing rows without duplicates.
- Authenticated demo-account API results show local/expired/unrelated examples no longer occupying the top recommendation slots.
- A real enriched policy detail displays every stored source clause and both attachment roles at desktop and narrow mobile widths.

## Implementation Calls

- Use the existing local branch and running development services.
- Use official API fields plus direct official file links; defer bulk attachment text extraction because it would add unbounded external traffic and format-specific failure modes to the list sync.
- Use jsoup 1.23.2 for deterministic HTML-to-text conversion.
