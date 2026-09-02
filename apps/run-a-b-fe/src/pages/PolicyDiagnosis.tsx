import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import api from "@/lib/api";
import {
  buildDiagnosisPrefill,
  EMPLOYEE_OPTIONS,
  INDUSTRY_OPTIONS,
  REGION_OPTIONS,
  REVENUE_OPTIONS,
  toDiagnosisRequest,
  validateDiagnosisForm,
  type DiagnosisForm,
  type DiagnosisResponse,
  type EvidenceStatus,
  type SavedBusiness,
} from "@/features/diagnosis/model";
import { statusPresentation, type PolicyDetailData } from "@/features/policy/presentation";
import { isLoadedPolicyForRoute, type LoadedPolicy } from "@/features/diagnosis/policyRoute";

type FlowState =
  | { tag: "idle" }
  | { tag: "analyzing"; policyId: number; step: number }
  | { tag: "result"; policyId: number; result: DiagnosisResponse }
  | { tag: "error"; policyId: number; message: string };

const EMPTY_FORM: DiagnosisForm = {
  businessStatus: true,
  industry: "",
  region: "",
  annualRevenueBand: "",
  employeeCountBand: "",
};

const AUDIT_STEPS = ["내 사업 정보 확인", "공고문 조건 대조", "판정 근거 정리"];

const STATUS_ICONS: Record<EvidenceStatus, string> = {
  PASS: "✓",
  PARTIAL: "~",
  UNKNOWN: "?",
  FAIL: "!",
  NOT_REQUIRED: "–",
};

function errorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null && "response" in error) {
    const response = error.response as { data?: { message?: string } } | undefined;
    if (response?.data?.message) return response.data.message;
  }
  return "진단을 완료하지 못했어요. 잠시 후 다시 시도해 주세요.";
}

function safeExternalUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === "https:" || url.protocol === "http:" ? url.href : null;
  } catch {
    return null;
  }
}

function nonblank(value: string | null, fallback: string): string {
  return value?.trim() || fallback;
}

export function OriginalNoticeLink({ detailUrl }: { detailUrl: string | null }) {
  const originalUrl = safeExternalUrl(detailUrl);
  if (!originalUrl) return null;
  return (
    <a href={originalUrl} target="_blank" rel="noopener noreferrer" className="rounded-xl bg-primary-600 px-5 py-3 text-sm font-semibold text-white hover:bg-primary-700">
      원문 공고 보기
    </a>
  );
}

function SelectField({
  id,
  label,
  value,
  options,
  error,
  onChange,
}: {
  id: keyof DiagnosisForm;
  label: string;
  value: string;
  options: readonly string[];
  error?: string;
  onChange: (value: string) => void;
}) {
  const errorId = `${id}-error`;
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-semibold text-gray-700 mb-2">{label}</label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
        className={`w-full rounded-xl border bg-white px-4 py-3 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-200 ${error ? "border-red-400" : "border-gray-200 focus:border-primary-400"}`}
      >
        <option value="">선택해 주세요</option>
        {options.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
      {error && <p id={errorId} role="alert" className="mt-1.5 text-xs text-red-600">{error}</p>}
    </div>
  );
}

function EvidenceRow({ evidence }: { evidence: DiagnosisResponse["evidence"][number] }) {
  const presentation = statusPresentation[evidence.status];
  return (
    <li className="rounded-xl border border-gray-200 p-4">
      <div className="flex items-start justify-between gap-3">
        <h3 className="text-sm font-bold text-gray-800">{evidence.label}</h3>
        <span className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold ${presentation.className}`}>
          <span aria-hidden="true">{STATUS_ICONS[evidence.status]}</span>
          {presentation.label}
        </span>
      </div>
      <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
        <div><dt className="text-xs text-gray-400">내 조건</dt><dd className="mt-0.5 text-gray-700">{evidence.userValue ?? "입력 정보 없음"}</dd></div>
        <div><dt className="text-xs text-gray-400">공고 조건</dt><dd className="mt-0.5 text-gray-700">{evidence.policyValue ?? "확인된 근거 없음"}</dd></div>
      </dl>
      <p className="mt-3 text-sm leading-6 text-gray-600">{evidence.reason ?? "판정 이유를 확인할 수 없어요."}</p>
      <p className="mt-2 text-xs text-gray-400">출처: {evidence.source ?? "출처 정보 없음"}</p>
    </li>
  );
}

export function DiagnosisResult({ policyId, result, onRetry }: { policyId: number; result: DiagnosisResponse; onRetry?: () => void }) {
  const verdict = statusPresentation[result.verdict];
  const category = nonblank(result.policy.category, "정보 없음");
  const agency = nonblank(result.policy.agency, "정보 없음");
  const applicationPeriod = nonblank(result.policy.applicationPeriod, "기간 정보 없음 — 원문 공고 확인");
  return (
    <div className="bg-gray-50 min-h-screen pt-15 px-40">
      <main className="max-w-4xl mx-auto py-10">
        <nav className="flex items-center gap-1.5 text-sm text-gray-500"><Link to="/policies">정책 모아보기</Link><span className="text-gray-300">›</span><Link to={`/policies/${policyId}`}>{result.policy.title}</Link><span className="text-gray-300">›</span><span className="text-gray-700">60초 정책 진단</span></nav>
        <section className="mt-5 bg-white rounded-2xl border border-gray-200 p-6" aria-live="polite">
          <p className="text-sm text-gray-500">{result.policy.title}</p>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-bold text-gray-900">{result.verdictLabel || verdict.label}</h1>
            <span className={`rounded-full border px-3 py-1 text-sm font-semibold ${verdict.className}`}>{verdict.label}</span>
            <span className="text-sm text-gray-500">판정 신뢰도: {result.confidence === "HIGH" ? "높음" : result.confidence === "MEDIUM" ? "보통" : "낮음"}</span>
          </div>
          <dl className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-sm text-gray-600">
            <div className="flex gap-1.5"><dt className="text-gray-400">분야</dt><dd>{category}</dd></div>
            <div className="flex gap-1.5"><dt className="text-gray-400">주관 기관</dt><dd>{agency}</dd></div>
            <div className="flex gap-1.5"><dt className="text-gray-400">신청 기간</dt><dd>{applicationPeriod}</dd></div>
          </dl>
          <p className="mt-4 text-base leading-7 text-gray-700">{result.summary}</p>
          <div className="mt-5 rounded-xl bg-primary-50 border border-primary-100 px-4 py-3 text-sm text-primary-700">
            {result.score === null ? "조건 일치도를 계산할 근거가 충분하지 않아요." : `조건 일치도 ${result.score}점`}
          </div>
        </section>

        <section className="mt-5 bg-white rounded-2xl border border-gray-200 p-6">
          <h2 className="text-lg font-bold text-gray-900">항목별 판정 근거</h2>
          <ul className="mt-4 space-y-3">{result.evidence.map((evidence) => <EvidenceRow key={evidence.key} evidence={evidence} />)}</ul>
          {result.unknownConditions.length > 0 && <div className="mt-5"><h3 className="text-sm font-semibold text-gray-700">원문에서 더 확인할 조건</h3><div className="mt-2 flex flex-wrap gap-2">{result.unknownConditions.map((condition) => <span key={condition} className="rounded-full border border-gray-200 bg-gray-100 px-2.5 py-1 text-xs text-gray-600">확인 필요 · {condition}</span>)}</div></div>}
          {result.hardFailReasons.length > 0 && <div className="mt-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"><p className="font-semibold">현재 대상이 아닐 수 있는 이유</p><ul className="mt-1 list-disc pl-5">{result.hardFailReasons.map((reason) => <li key={reason}>{reason}</li>)}</ul></div>}
        </section>

        <section className="mt-5 bg-white rounded-2xl border border-gray-200 p-6 text-sm text-gray-600">
          <h2 className="font-bold text-gray-900">정보 품질</h2>
          <dl className="mt-3 grid gap-3 sm:grid-cols-2"><div><dt className="text-gray-400">출처</dt><dd>{result.dataQuality.sourceSystem ?? "출처 정보 없음"}</dd></div><div><dt className="text-gray-400">확인 가능한 항목 수</dt><dd>{result.dataQuality.verifiedFactCount}/{result.dataQuality.totalFactCount}</dd></div><div><dt className="text-gray-400">구조화 조건</dt><dd>{result.dataQuality.policyCardAvailable ? "확인됨" : "미확인"}</dd></div><div><dt className="text-gray-400">추출 상태</dt><dd>{result.dataQuality.extractionStatus ?? "상태 정보 없음"}</dd></div></dl>
          <p className="mt-5 rounded-xl bg-gray-50 px-4 py-3 leading-6">{result.disclaimer}</p>
        </section>

        <div className="mt-5 flex flex-wrap gap-3">
          <OriginalNoticeLink detailUrl={result.policy.detailUrl} />
          <Link to={`/policies/${policyId}/checklist`} className="rounded-xl border border-gray-200 bg-white px-5 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-50">신청 체크리스트</Link>
          {onRetry && <button onClick={onRetry} className="rounded-xl border border-gray-200 bg-white px-5 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-50">입력 다시 하기</button>}
          <Link to={`/policies/${policyId}`} className="rounded-xl border border-gray-200 bg-white px-5 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-50">정책 상세 보기</Link>
        </div>
      </main>
    </div>
  );
}

export default function PolicyDiagnosis() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const policyId = Number(id);
  const [loadedPolicy, setLoadedPolicy] = useState<LoadedPolicy | null>(null);
  const [policyState, setPolicyState] = useState<"loading" | "missing" | "error" | "ready">("loading");
  const [form, setForm] = useState<DiagnosisForm>(EMPTY_FORM);
  const [errors, setErrors] = useState<Partial<Record<keyof DiagnosisForm, string>>>({});
  const [flow, setFlow] = useState<FlowState>({ tag: "idle" });
  const editedRef = useRef(false);
  const mountedRef = useRef(true);
  const diagnosisGenerationRef = useRef(0);
  const minimumTimerRef = useRef<{ timer: number; resolve: () => void } | null>(null);
  const policyBelongsToRoute = isLoadedPolicyForRoute(loadedPolicy, policyId);
  const policy = policyBelongsToRoute ? loadedPolicy?.detail ?? null : null;

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      if (minimumTimerRef.current) {
        window.clearTimeout(minimumTimerRef.current.timer);
        minimumTimerRef.current.resolve();
        minimumTimerRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    let active = true;
    diagnosisGenerationRef.current += 1;
    if (minimumTimerRef.current) {
      window.clearTimeout(minimumTimerRef.current.timer);
      minimumTimerRef.current.resolve();
      minimumTimerRef.current = null;
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 라우트 변경 시 이전 정책의 폼을 보이지 않게 한다.
    setPolicyState("loading");
    setFlow({ tag: "idle" });
    api.get(`/api/v1/policies/${policyId}`)
      .then((response) => {
        if (!active) return;
        setLoadedPolicy({ policyId, detail: response.data.data as PolicyDetailData });
        setPolicyState("ready");
      })
      .catch((error: unknown) => {
        if (!active) return;
        const status = typeof error === "object" && error !== null && "response" in error
          ? (error.response as { status?: number } | undefined)?.status
          : undefined;
        setPolicyState(status === 404 ? "missing" : "error");
      });
    return () => { active = false; };
  }, [policyId]);

  useEffect(() => {
    if (typeof window === "undefined" || !window.localStorage.getItem("access_token")) return;
    let active = true;
    api.get("/api/v1/users/me/business", { validateStatus: () => true })
      .then((response) => {
        if (!active || editedRef.current || response.status < 200 || response.status >= 300) return;
        setForm(buildDiagnosisPrefill(response.data.data as SavedBusiness));
      })
      .catch(() => { /* optional prefill must leave a guest diagnosis usable */ });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (flow.tag !== "analyzing") return;
    const second = window.setTimeout(() => setFlow((current) => current.tag === "analyzing" ? { ...current, step: 1 } : current), 500);
    const third = window.setTimeout(() => setFlow((current) => current.tag === "analyzing" ? { ...current, step: 2 } : current), 1000);
    return () => {
      window.clearTimeout(second);
      window.clearTimeout(third);
    };
  }, [flow.tag]);

  function updateForm<K extends keyof DiagnosisForm>(key: K, value: DiagnosisForm[K]) {
    editedRef.current = true;
    setForm((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (flow.tag === "analyzing") return;
    const nextErrors = validateDiagnosisForm(form);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    const requestPolicyId = policyId;
    const requestGeneration = diagnosisGenerationRef.current;
    setFlow({ tag: "analyzing", policyId: requestPolicyId, step: 0 });
    const startedAt = Date.now();
    const outcome = api.post(`/api/v1/policies/${policyId}/diagnosis`, toDiagnosisRequest(form))
      .then((response) => ({ type: "result" as const, result: response.data.data as DiagnosisResponse }))
      .catch((error: unknown) => ({ type: "error" as const, message: errorMessage(error) }));
    const minimumDisplay = new Promise<void>((resolve) => {
      const timer = window.setTimeout(() => {
        minimumTimerRef.current = null;
        resolve();
      }, Math.max(0, 1500 - (Date.now() - startedAt)));
      minimumTimerRef.current = { timer, resolve };
    });
    const [settled] = await Promise.all([outcome, minimumDisplay]);
    if (!mountedRef.current || diagnosisGenerationRef.current !== requestGeneration) return;
    setFlow(settled.type === "result"
      ? { tag: "result", policyId: requestPolicyId, result: settled.result }
      : { tag: "error", policyId: requestPolicyId, message: settled.message });
  }

  if (policyState === "loading" || (loadedPolicy !== null && !policyBelongsToRoute)) {
    return (
      <div className="bg-gray-50 min-h-screen pt-15 px-40">
        <div className="py-20 max-w-3xl mx-auto">
          <h1 className="text-2xl font-bold text-gray-900">60초 정책 진단</h1>
          <div className="mt-6 bg-white rounded-2xl border border-gray-200 px-6 py-12 text-center">
            <div className="mx-auto w-9 h-9 rounded-full border-[3px] border-gray-200 border-t-primary-600 animate-spin" />
            <p className="mt-4 text-sm text-gray-500">정책 정보를 불러오고 있어요</p>
          </div>
        </div>
      </div>
    );
  }

  if (policyState === "missing" || policyState === "error" || !policy) {
    return (
      <div className="bg-gray-50 min-h-screen pt-15 px-40">
        <div className="py-20 max-w-3xl mx-auto text-center bg-white rounded-2xl border border-gray-200">
          <h1 className="text-xl font-bold text-gray-900">{policyState === "missing" ? "정책을 찾을 수 없어요" : "정책 정보를 불러오지 못했어요"}</h1>
          <button onClick={() => navigate("/policies")} className="mt-5 text-sm font-semibold text-primary-600 hover:underline">정책 목록으로 돌아가기</button>
        </div>
      </div>
    );
  }

  if (flow.tag === "analyzing" && flow.policyId === policyId) {
    return (
      <div className="bg-gray-50 min-h-screen pt-15 px-40">
        <main className="max-w-3xl mx-auto py-10">
          <h1 className="text-2xl font-bold text-gray-900">60초 정책 진단</h1>
          <p className="mt-2 text-sm text-gray-500">{policy.title}</p>
          <section className="mt-6 bg-white rounded-2xl border border-gray-200 px-6 py-10" aria-live="polite">
            <div className="mx-auto w-10 h-10 rounded-full border-[3px] border-primary-200 border-t-primary-600 animate-spin" />
            <h2 className="mt-5 text-center text-base font-bold text-gray-800">공고문 조건을 비교하고 있어요</h2>
            <ol className="mt-7 max-w-md mx-auto space-y-3">
              {AUDIT_STEPS.map((step, index) => (
                <li key={step} className={`flex items-center gap-3 rounded-xl px-4 py-3 text-sm ${index <= flow.step ? "bg-primary-50 text-primary-700" : "bg-gray-50 text-gray-400"}`}>
                  <span className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold ${index < flow.step ? "bg-primary-600 text-white" : index === flow.step ? "bg-primary-200 text-primary-700" : "bg-gray-200 text-gray-500"}`}>{index < flow.step ? "✓" : index + 1}</span>
                  {step}
                </li>
              ))}
            </ol>
          </section>
        </main>
      </div>
    );
  }

  if (flow.tag === "result" && flow.policyId === policyId) return <DiagnosisResult policyId={policyId} result={flow.result} onRetry={() => setFlow({ tag: "idle" })} />;

  return (
    <div className="bg-gray-50 min-h-screen pt-15 px-40">
      <main className="max-w-3xl mx-auto py-10">
        <nav className="flex items-center gap-1.5 text-sm text-gray-500"><Link to="/policies">정책 모아보기</Link><span className="text-gray-300">›</span><Link to={`/policies/${policyId}`}>{policy.title}</Link><span className="text-gray-300">›</span><span className="text-gray-700">60초 정책 진단</span></nav>
        <section className="mt-5 bg-white rounded-2xl border border-gray-200 p-6">
          <h1 className="text-2xl font-bold text-gray-900">60초 정책 진단</h1>
          <p className="mt-2 text-sm text-gray-500">내 사업 정보와 공고 조건을 1차 비교해 드려요. 최종 자격은 원문 공고와 담당 기관에서 확인해 주세요.</p>
          <p className="mt-4 rounded-xl border border-primary-100 bg-primary-50 px-4 py-3 text-sm text-primary-700">{policy.title}</p>
          {flow.tag === "error" && flow.policyId === policyId && <p role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{flow.message}</p>}
          {flow.tag === "error" && flow.policyId === policyId && <div className="mt-3"><OriginalNoticeLink detailUrl={policy.detailUrl} /></div>}
          <form className="mt-6 space-y-5" onSubmit={submit} noValidate>
            <fieldset><legend className="text-sm font-semibold text-gray-700 mb-2">사업 상태</legend><div className="flex gap-3"><label className={`flex-1 cursor-pointer rounded-xl border px-4 py-3 text-sm ${form.businessStatus ? "border-primary-400 bg-primary-50 text-primary-700" : "border-gray-200 text-gray-600"}`}><input type="radio" name="businessStatus" checked={form.businessStatus} onChange={() => updateForm("businessStatus", true)} className="mr-2" />사업 중</label><label className={`flex-1 cursor-pointer rounded-xl border px-4 py-3 text-sm ${!form.businessStatus ? "border-primary-400 bg-primary-50 text-primary-700" : "border-gray-200 text-gray-600"}`}><input type="radio" name="businessStatus" checked={!form.businessStatus} onChange={() => updateForm("businessStatus", false)} className="mr-2" />사업 준비 중</label></div></fieldset>
            <div className="grid gap-5 sm:grid-cols-2"><SelectField id="region" label="지역" value={form.region} options={REGION_OPTIONS} error={errors.region} onChange={(value) => updateForm("region", value)} /><SelectField id="industry" label="업종" value={form.industry} options={INDUSTRY_OPTIONS} error={errors.industry} onChange={(value) => updateForm("industry", value)} /></div>
            {form.businessStatus && <div className="grid gap-5 sm:grid-cols-2"><SelectField id="annualRevenueBand" label="연 매출 구간" value={form.annualRevenueBand} options={REVENUE_OPTIONS} error={errors.annualRevenueBand} onChange={(value) => updateForm("annualRevenueBand", value)} /><SelectField id="employeeCountBand" label="직원 수 구간" value={form.employeeCountBand} options={EMPLOYEE_OPTIONS} error={errors.employeeCountBand} onChange={(value) => updateForm("employeeCountBand", value)} /></div>}
            <button type="submit" className="w-full rounded-xl bg-primary-600 py-3 text-sm font-semibold text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-primary-300">{flow.tag === "error" && flow.policyId === policyId ? "다시 진단하기" : "내 조건으로 진단하기"}</button>
          </form>
        </section>
      </main>
    </div>
  );
}
