import { normalizeBusinessIndustry, normalizeBusinessRegion } from "./options.ts";

export type BusinessDraft = {
  status: string;
  industry: string;
  region: string;
  revenue: string;
  employees: string;
};

type BusinessProfile = {
  businessStatus: boolean;
  jobCategory: string | null;
  region: string | null;
  annualRevenue: string | null;
  employeeCount: string | null;
};

type BusinessEditor = { draft: BusinessDraft; saved: BusinessDraft | null; loadFailed: boolean; saving: boolean };
type Action =
  | { type: "loaded"; business: BusinessProfile }
  | { type: "load-failed"; error: unknown }
  | { type: "edit"; field: keyof BusinessDraft; value: string }
  | { type: "save-started" }
  | { type: "save-finished" }
  | { type: "cancel" };

export const initialBusinessEditor: BusinessEditor = {
  draft: { status: "사업 중", industry: "", region: "", revenue: "", employees: "" },
  saved: null,
  loadFailed: false,
  saving: false,
};

export function businessEditorReducer(state: BusinessEditor, action: Action): BusinessEditor {
  if (action.type === "save-started") return { ...state, saving: true };
  if (action.type === "save-finished") return { ...state, saving: false };
  if (state.saving && (action.type === "edit" || action.type === "cancel")) return state;
  if (action.type === "loaded") {
    const b = action.business;
    const draft = {
      status: b.businessStatus ? "사업 중" : "사업 준비 중",
      industry: normalizeBusinessIndustry(b.jobCategory),
      region: normalizeBusinessRegion(b.region),
      revenue: b.annualRevenue ?? "",
      employees: b.employeeCount ?? "",
    };
    return { draft, saved: { ...draft }, loadFailed: false, saving: state.saving };
  }
  if (action.type === "load-failed") {
    const response = (action.error as { response?: { status?: number; data?: { error?: { code?: string } } } } | null)?.response;
    if (response?.status === 404 && response.data?.error?.code === "BUSINESS_INFO_NOT_FOUND") {
      const draft = { ...initialBusinessEditor.draft };
      return { draft, saved: { ...draft }, loadFailed: false, saving: state.saving };
    }
    return { ...state, loadFailed: true };
  }
  if (action.type === "edit") {
    return { ...state, draft: { ...state.draft, [action.field]: action.value } };
  }
  return state.saved ? { ...state, draft: { ...state.saved } } : state;
}

export function toBusinessUpdateRequest(draft: BusinessDraft) {
  return {
    businessStatus: draft.status === "사업 중",
    jobCategory: draft.industry,
    region: draft.region,
    annualRevenue: draft.status === "사업 중" ? draft.revenue || null : null,
    employeeCount: draft.status === "사업 중" ? draft.employees || null : null,
  };
}
