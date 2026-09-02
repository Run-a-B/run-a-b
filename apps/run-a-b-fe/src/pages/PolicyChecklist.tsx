import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import api from "@/lib/api";
import {
  buildChecklistProgress,
  checkedForPolicy,
  loadPolicyChecklist,
  retryChecklistLoad,
  type CheckedByPolicy,
  type ChecklistItem,
  type PolicyChecklistDetail,
} from "@/features/policy/checklist";

const CATEGORY_COLORS: Record<string, string> = {
  기술: "bg-violet-100 text-violet-700",
  금융: "bg-indigo-100 text-indigo-700",
  인력: "bg-blue-100 text-blue-700",
  경영: "bg-emerald-100 text-emerald-700",
  창업: "bg-orange-100 text-orange-700",
  수출: "bg-sky-100 text-sky-700",
  내수: "bg-teal-100 text-teal-700",
  기타: "bg-gray-100 text-gray-600",
};

export type ChecklistLoadState =
  | { tag: "loading"; policyId: number }
  | {
      tag: "loaded";
      policyId: number;
      policy: { id: number; title: string; category: string | null };
      detail: PolicyChecklistDetail;
      sourceUrl: string | null;
    }
  | { tag: "failed"; policyId: number };

export function ChecklistLoadNotice({
  failed,
  onBack,
  onRetry,
}: {
  failed: boolean;
  onBack: () => void;
  onRetry: () => void;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-24">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-50"><ChecklistIcon className="text-primary-400" /></div>
      <div role={failed ? "alert" : "status"} aria-live={failed ? undefined : "polite"} aria-busy={failed ? undefined : true} className="text-center">
        <p className="mb-1 text-base font-bold text-gray-700">{failed ? "체크리스트를 불러오지 못했어요" : "체크리스트 정보를 불러오고 있어요"}</p>
        <p className="text-sm leading-6 text-gray-600">{failed ? "네트워크 상태를 확인한 뒤 다시 시도해 주세요." : "정책 원문과 신청 준비 정보를 확인하고 있어요."}</p>
      </div>
      <div className="mt-2 flex gap-3">
        <button onClick={onBack} className="min-h-11 rounded-xl border border-gray-200 px-5 py-2.5 text-sm font-semibold text-gray-600 transition-colors hover:bg-gray-50">정책 상세 보기</button>
        {failed && <button onClick={onRetry} className="min-h-11 rounded-xl bg-primary-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-primary-700">다시 시도</button>}
      </div>
    </div>
  );
}

function currentChecklistLoad(state: ChecklistLoadState, policyId: number): ChecklistLoadState {
  if (!Number.isInteger(policyId) || policyId < 1) return { tag: "failed", policyId };
  return state.policyId === policyId ? state : { tag: "loading", policyId };
}

export default function PolicyChecklist() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const policyId = Number(id);
  const [retry, setRetry] = useState(0);
  const [loadState, setLoadState] = useState<ChecklistLoadState>({ tag: "loading", policyId });
  const [checkedByPolicy, setCheckedByPolicy] = useState<CheckedByPolicy>({});

  useEffect(() => {
    let active = true;
    if (!Number.isInteger(policyId) || policyId < 1) {
      return () => {
        active = false;
      };
    }

    loadPolicyChecklist(api, policyId)
      .then((result) => {
        if (active) setLoadState({ tag: "loaded", policyId, ...result });
      })
      .catch(() => {
        if (active) setLoadState({ tag: "failed", policyId });
      });

    return () => {
      active = false;
    };
  }, [policyId, retry]);

  const currentLoad = currentChecklistLoad(loadState, policyId);
  if (currentLoad.tag !== "loaded") {
    const failed = currentLoad.tag === "failed";
    return (
      <div className="min-h-screen bg-gray-50 pt-15">
        <ChecklistBreadcrumb policyId={policyId} title={failed ? "정책" : "정책 로딩 중..."} />
        <ChecklistLoadNotice failed={failed} onBack={() => navigate(`/policies/${policyId}`)} onRetry={retryChecklist} />
      </div>
    );
  }

  const { policy, detail, sourceUrl } = currentLoad;
  const checklist = detail.applicationChecklist ?? [];
  const checked = checkedForPolicy(checkedByPolicy, policyId);
  const required = checklist.filter((item) => item.required);
  const optional = checklist.filter((item) => !item.required);
  const progress = buildChecklistProgress(checklist, checked);
  const totalChecked = checklist.filter((item) => checked[item.id]).length;
  const categoryColor = CATEGORY_COLORS[policy.category ?? ""] ?? "bg-gray-100 text-gray-600";
  const groupItems = progress.kind === "recommendations" ? checklist : required;

  function toggle(itemId: string) {
    setCheckedByPolicy((previous) => {
      const current = checkedForPolicy(previous, policyId);
      return {
        ...previous,
        [policyId]: { ...current, [itemId]: !current[itemId] },
      };
    });
  }

  function retryChecklist() {
    setLoadState(retryChecklistLoad(policyId));
    setRetry((value) => value + 1);
  }

  return (
    <div className="min-h-screen bg-gray-50 pt-15">
      <ChecklistBreadcrumb policyId={policyId} title={policy.title} />
      <div className="flex items-start gap-6 px-40 py-8">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <div className="rounded-2xl border border-gray-200 bg-white px-6 py-5">
            <div className="mb-3 flex items-center gap-2">
              <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${categoryColor}`}>{policy.category ?? "기타"}</span>
              <span className="text-xs text-gray-600">{detail.applicationPeriod}</span>
            </div>
            <h1 className="mb-1 text-lg font-bold leading-snug text-gray-900">{policy.title}</h1>
            <p className="text-sm text-gray-500">{detail.department}</p>
          </div>

          <ChecklistGroup title={progress.groupTitle} completed={progress.completed} total={progress.total} required={progress.kind === "required"} notice={progress.notice} items={groupItems} checked={checked} onToggle={toggle} />

          {progress.kind === "required" && optional.length > 0 && (
            <ChecklistGroup title="추가 확인 서류" completed={optional.filter((item) => checked[item.id]).length} total={optional.length} required={false} notice="제출 여부는 원문 공고에서 확인해 주세요" items={optional} checked={checked} onToggle={toggle} />
          )}

          <div className="flex gap-3 rounded-2xl border border-amber-100 bg-amber-50 px-6 py-4">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="mt-0.5 shrink-0 text-amber-500"><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" /></svg>
            <div><p className="mb-1 text-sm font-semibold text-amber-800">신청 전 확인 사항</p><ul className="space-y-1 text-xs leading-6 text-amber-700"><li>• 제출 서류와 양식은 원문 공고를 기준으로 확인해 주세요.</li><li>• 신청 기간과 예산 현황에 따라 조기 마감될 수 있어요.</li></ul></div>
          </div>
        </div>

        <aside className="sticky top-6 flex w-80 shrink-0 flex-col gap-4">
          <div className="rounded-2xl border border-gray-200 bg-white px-5 py-5">
            <p className="mb-4 text-sm font-bold text-gray-800">준비 현황</p>
            <div className="mb-4 flex items-center gap-4">
              <ProgressRing checked={totalChecked} total={checklist.length} />
              <div>
                <p className="text-sm font-semibold text-gray-800">{progress.kind === "required" ? `필수 서류 ${progress.completed}/${progress.total} 준비됨` : `준비 권장 서류 ${progress.completed}/${progress.total} 확인됨`}</p>
                <p className="mt-0.5 text-xs text-gray-600">{progress.notice ?? `${progress.total - progress.completed}개 필수 서류가 남았어요`}</p>
              </div>
            </div>
            {progress.kind === "required" && <div className="mb-4 flex flex-col gap-1.5">{required.map((item) => <div key={item.id} className="flex items-center gap-2"><div className={`flex h-4 w-4 shrink-0 items-center justify-center rounded-full ${checked[item.id] ? "bg-green-400" : "bg-gray-100"}`}>{checked[item.id] && <span className="text-[8px] font-bold text-white">✓</span>}</div><span className={`truncate text-xs ${checked[item.id] ? "text-gray-600 line-through" : "text-gray-600"}`}>{item.label}</span></div>)}</div>}
            {sourceUrl ? <a href={sourceUrl} target="_blank" rel="noopener noreferrer" className="flex w-full items-center justify-center gap-2 rounded-xl bg-primary-600 py-3 text-sm font-semibold text-white transition-colors hover:bg-primary-700">원문 공고·신청 페이지 보기 <ExternalIcon /></a> : <p className="rounded-xl bg-gray-50 px-4 py-3 text-center text-xs leading-5 text-gray-500">원문 공고·신청 페이지 URL을 확인해 주세요.</p>}
          </div>
          <button onClick={() => navigate(`/policies/${policyId}`)} className="flex items-center justify-center gap-1.5 py-2 text-sm text-gray-500 transition-colors hover:text-gray-700">정책 상세로 돌아가기</button>
        </aside>
      </div>
    </div>
  );
}

function ChecklistBreadcrumb({ policyId, title }: { policyId: number; title: string }) {
  return <div className="px-40 py-4"><nav className="flex items-center gap-1.5 text-sm text-gray-500"><Link to="/" className="transition-colors hover:text-gray-700">홈</Link><span className="text-gray-300">›</span><Link to="/policies" className="transition-colors hover:text-gray-700">정책 모아보기</Link><span className="text-gray-300">›</span><Link to={`/policies/${policyId}`} className="max-w-xs truncate transition-colors hover:text-gray-700">{title}</Link><span className="text-gray-300">›</span><span className="font-medium text-gray-700">신청 준비</span></nav></div>;
}

function ChecklistGroup({ title, completed, total, required, notice, items, checked, onToggle }: { title: string; completed: number; total: number; required: boolean; notice: string | null; items: ChecklistItem[]; checked: Record<string, boolean>; onToggle: (id: string) => void }) {
  return <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white"><div className="flex items-center gap-2 border-b border-gray-100 px-6 py-4"><div className={`h-2 w-2 rounded-full ${required ? "bg-red-400" : "bg-gray-300"}`} /><span className="text-sm font-bold text-gray-800">{title}</span><span className="ml-auto text-xs font-semibold text-primary-600">{completed}/{total} 확인</span></div>{notice && <p className="px-6 pt-3 text-xs text-gray-500">{notice}</p>}<div className="divide-y divide-gray-100">{items.map((item) => <ChecklistRow key={item.id} item={item} checked={Boolean(checked[item.id])} onToggle={() => onToggle(item.id)} />)}</div></div>;
}

export function ChecklistRow({ item, checked, onToggle }: { item: ChecklistItem; checked: boolean; onToggle: () => void }) {
  return <button type="button" aria-pressed={checked} onClick={onToggle} className={`flex w-full items-start gap-4 px-6 py-4 text-left transition-colors hover:bg-gray-50 ${checked ? "bg-gray-50" : ""}`}><span className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-md border-2 ${checked ? "border-primary-600 bg-primary-600" : "border-gray-300"}`}>{checked && <span className="text-[10px] font-bold text-white">✓</span>}</span><span className="min-w-0 flex-1"><span className={`block text-sm font-medium ${checked ? "text-gray-600 line-through" : "text-gray-800"}`}>{item.label}</span>{item.required && <span className="mt-1 block text-xs font-medium text-red-400">필수</span>}{item.description && <span className="mt-0.5 block text-xs leading-5 text-gray-600">{item.description}</span>}</span></button>;
}

function ProgressRing({ checked, total }: { checked: number; total: number }) {
  const ratio = total > 0 ? checked / total : 0;
  const circumference = 2 * Math.PI * 26;
  return <div className="relative h-16 w-16 shrink-0"><svg width="64" height="64" viewBox="0 0 64 64" className="-rotate-90"><circle cx="32" cy="32" r="26" fill="none" stroke="#f3f4f6" strokeWidth="6" /><circle cx="32" cy="32" r="26" fill="none" stroke="#6C63FF" strokeWidth="6" strokeDasharray={`${circumference}`} strokeDashoffset={`${circumference * (1 - ratio)}`} strokeLinecap="round" style={{ transition: "stroke-dashoffset 0.4s ease" }} /></svg><div className="absolute inset-0 flex items-center justify-center"><span className="text-sm font-bold text-gray-800">{checked}/{total}</span></div></div>;
}

function ChecklistIcon({ className }: { className: string }) {
  return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" /><rect x="8" y="2" width="8" height="4" rx="1" ry="1" /><line x1="9" y1="12" x2="15" y2="12" /><line x1="9" y1="16" x2="13" y2="16" /></svg>;
}

function ExternalIcon() {
  return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" /><polyline points="15 3 21 3 21 9" /><line x1="10" y1="14" x2="21" y2="3" /></svg>;
}
