export const DIAGNOSIS_AUDIT_STEPS = [
  "내 사업 정보 확인",
  "공고문 조건 대조",
  "판정 근거 정리",
] as const;

export type DiagnosisAnnouncementState =
  | { tag: "loading" }
  | { tag: "unavailable" }
  | { tag: "form" }
  | { tag: "analyzing"; step: number }
  | { tag: "result"; verdictLabel: string }
  | { tag: "error"; message: string };

export type DiagnosisFlowTag = "idle" | "analyzing" | "result" | "error";
export type DiagnosisFocusTarget = "form" | "analyzing" | "result" | "error";

export function diagnosisSourceLabel(sourceSystem: string | null): string {
  const source = sourceSystem?.trim();
  if (!source) return "출처 정보 없음";
  return source.toLowerCase() === "bizinfo" ? "기업마당" : source;
}

export function diagnosisExtractionLabel(extractionStatus: string | null): string {
  const labels: Record<string, string> = {
    extracted: "추출 완료",
    partially_extracted: "일부 추출",
    failed: "추출 실패",
    not_available: "추출 정보 없음",
  };
  const status = extractionStatus?.trim().toLowerCase();
  if (!status) return "상태 정보 없음";
  return labels[status] ?? "상태 확인 필요";
}

export function diagnosisAnnouncement(state: DiagnosisAnnouncementState): string {
  switch (state.tag) {
    case "loading":
      return "정책 정보를 불러오고 있어요.";
    case "unavailable":
      return "정책 정보를 불러오지 못했어요.";
    case "form":
      return "진단 입력 양식입니다. 필수 항목을 입력해 주세요.";
    case "analyzing": {
      const step = Math.min(Math.max(state.step, 0), DIAGNOSIS_AUDIT_STEPS.length - 1);
      return `${DIAGNOSIS_AUDIT_STEPS[step]} 진행 중입니다.`;
    }
    case "result":
      return `진단 결과가 나왔습니다. ${state.verdictLabel}`;
    case "error":
      return `진단에 실패했습니다. ${state.message}`;
  }
}

export function diagnosisFocusTarget(tag: DiagnosisFlowTag): DiagnosisFocusTarget {
  return tag === "idle" ? "form" : tag;
}
