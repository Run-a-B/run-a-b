import { useState, useEffect } from "react";
import Dropdown from "@/components/common/Dropdown";
import { useNavigate } from "react-router-dom";
import { MOCK_POLICIES, type Policy } from "@/data/policies";
import { getVisitedPolicies } from "@/data/visited";

const REGIONS = ["전체 지역", "서울특별시", "부산광역시", "대구광역시", "인천광역시", "경기도", "강원도", "충청남도", "전라남도", "경상남도", "제주도"];
const INDUSTRIES = ["전체 업종", "음식점업", "카페/음료", "의류/패션", "뷰티/미용", "교육/학원", "IT/소프트웨어", "제조업", "도소매업"];
const CATEGORIES = ["전체 카테고리", "창업 지원", "자금 지원", "세금 감면", "인건비 지원", "임차료 지원", "교육/컨설팅"];
const SORT_OPTIONS = ["관련도 높은순", "최신순", "마감임박순"];

const CATEGORY_COLORS: Record<string, string> = {
  "최저임금": "bg-gray-100 text-gray-600",
  "노동·복지": "bg-blue-100 text-blue-700",
  "대출·자금": "bg-indigo-100 text-indigo-700",
  "에너지": "bg-orange-100 text-orange-700",
  "디지털": "bg-violet-100 text-violet-700",
  "세금": "bg-green-100 text-green-700",
  "창업지원": "bg-emerald-100 text-emerald-700",
  "임차료": "bg-pink-100 text-pink-700",
  "교육": "bg-sky-100 text-sky-700",
};

const PER_PAGE = 6;

function PolicyCard({ policy, visited }: { policy: Policy; visited: boolean }) {
  const navigate = useNavigate();
  const categoryColor = CATEGORY_COLORS[policy.category] ?? "bg-gray-100 text-gray-600";

  return (
    <div
      onClick={() => navigate(`/policies/${policy.id}`)}
      className={`rounded-2xl border p-5 flex flex-col gap-3 hover:shadow-md transition-shadow cursor-pointer ${visited ? "bg-gray-50 border-gray-100" : "bg-white border-gray-200"}`}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${categoryColor}`}>
            {policy.category}
          </span>
          {visited && (
            <span className="flex items-center gap-1 text-xs text-gray-400">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
              읽음
            </span>
          )}
        </div>
        {policy.isAIRecommended && (
          <span className="flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-full bg-primary-50 text-primary-600">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="#FBBF24">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
            AI 추천
          </span>
        )}
      </div>

      <h3 className="text-sm font-bold text-gray-900 leading-snug line-clamp-2">{policy.title}</h3>

      <p className="text-xs text-gray-500 leading-relaxed line-clamp-2 flex-1">{policy.description}</p>

      <div className="flex items-end justify-between pt-1">
        <div className="flex items-center gap-2">
          <div className="w-12 h-1 bg-gray-100 rounded-full overflow-hidden">
            <div className="h-full bg-primary-500 rounded-full" style={{ width: `${policy.relevance}%` }} />
          </div>
          <span className="text-xs font-medium text-primary-600">관련도 {policy.relevance}%</span>
        </div>
        <div className="text-right">
          <p className="text-xs text-gray-500">{policy.agency}</p>
          <p className="text-xs text-gray-400">{policy.date}</p>
        </div>
      </div>
    </div>
  );
}

function PolicyListItem({ policy }: { policy: Policy }) {
  const navigate = useNavigate();
  const categoryColor = CATEGORY_COLORS[policy.category] ?? "bg-gray-100 text-gray-600";

  return (
    <div
      onClick={() => navigate(`/policies/${policy.id}`)}
      className="bg-white rounded-2xl border border-gray-200 px-6 py-4 flex items-center gap-5 hover:shadow-md transition-shadow cursor-pointer"
    >
      <div className="flex flex-col gap-2 flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className={`text-xs font-medium px-2.5 py-0.5 rounded-full shrink-0 ${categoryColor}`}>
            {policy.category}
          </span>
          {policy.isAIRecommended && (
            <span className="flex items-center gap-1 text-xs font-medium px-2.5 py-0.5 rounded-full bg-primary-50 text-primary-600 shrink-0">
              <svg width="10" height="10" viewBox="0 0 24 24" fill="#FBBF24">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
              </svg>
              AI 추천
            </span>
          )}
          <span className="text-xs text-gray-400 ml-auto shrink-0">{policy.date}</span>
        </div>
        <h3 className="text-sm font-bold text-gray-900 leading-snug line-clamp-1">{policy.title}</h3>
        <p className="text-xs text-gray-500 leading-relaxed line-clamp-1">{policy.description}</p>
      </div>

      <div className="flex items-center gap-4 shrink-0">
        <div className="flex items-center gap-2">
          <div className="w-16 h-1.5 bg-gray-100 rounded-full overflow-hidden">
            <div className="h-full bg-primary-500 rounded-full" style={{ width: `${policy.relevance}%` }} />
          </div>
          <span className="text-xs font-medium text-primary-600 w-16">관련도 {policy.relevance}%</span>
        </div>
        <span className="text-xs text-gray-500 w-20 text-right">{policy.agency}</span>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-300">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
      </div>
    </div>
  );
}

export default function Policies() {
  const [region, setRegion] = useState("전체 지역");
  const [industry, setIndustry] = useState("전체 업종");
  const [category, setCategory] = useState("전체 카테고리");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState("관련도 높은순");
  const [page, setPage] = useState(1);
  const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
  const [visitedIds, setVisitedIds] = useState<Set<number>>(() => getVisitedPolicies());

  useEffect(() => {
    const onFocus = () => setVisitedIds(getVisitedPolicies());
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, []);

  function handleFilter(setter: (v: string) => void) {
    return (v: string) => { setter(v); setPage(1); };
  }

  function handleReset() {
    setRegion("전체 지역");
    setIndustry("전체 업종");
    setCategory("전체 카테고리");
    setQuery("");
    setPage(1);
  }

  const filtered = MOCK_POLICIES.filter(p => {
    if (region !== "전체 지역" && p.region !== "전국" && p.region !== region) return false;
    if (industry !== "전체 업종" && p.industry !== "전체" && p.industry !== industry) return false;
    if (category !== "전체 카테고리" && p.filterCategory !== category) return false;
    if (query && !p.title.includes(query) && !p.description.includes(query)) return false;
    return true;
  });

  const sorted = [...filtered].sort((a, b) => {
    if (sort === "관련도 높은순") return b.relevance - a.relevance;
    if (sort === "최신순") return b.date.localeCompare(a.date);
    if (sort === "마감임박순") return a.date.localeCompare(b.date);
    return 0;
  });

  const totalPages = Math.ceil(sorted.length / PER_PAGE);
  const paged = sorted.slice((page - 1) * PER_PAGE, page * PER_PAGE);

  return (
    <div>
      <div className="bg-primary-100 px-40 pt-25">
        <p className="text-sm font-semibold text-primary-600">AI 맞춤 추천</p>
        <h4 className="text-3xl font-bold mt-3">정책 모아보기</h4>
        <p className="text-base mt-3 text-gray-500 pb-10">내 사업 조건에 맞는 정책을 한 곳에서 찾아보세요. 업종·지역·키워드로 필터링할 수 있어요.</p>
      </div>

      <div className="px-40 border border-x-0 border-gray-200 py-4 flex items-center gap-3">
        <Dropdown value={region}   onChange={handleFilter(setRegion)}   options={REGIONS} />
        <Dropdown value={industry} onChange={handleFilter(setIndustry)} options={INDUSTRIES} />
        <Dropdown value={category} onChange={handleFilter(setCategory)} options={CATEGORIES} />

        <div className="flex-1 flex items-center gap-2.5 border border-gray-200 rounded-2xl px-4 py-2.5 focus-within:border-primary-400 transition-colors bg-white">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-gray-400 shrink-0">
            <circle cx="11" cy="11" r="8"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            type="text"
            value={query}
            onChange={e => { setQuery(e.target.value); setPage(1); }}
            placeholder="정책명, 지원금명으로 검색..."
            className="flex-1 text-sm text-gray-700 placeholder-gray-400 focus:outline-none bg-transparent"
          />
        </div>

        <button
          onClick={handleReset}
          className="flex items-center gap-1.5 border border-gray-200 rounded-2xl px-4 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-50 transition-colors shrink-0"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
            <path d="M3 3v5h5"/>
          </svg>
          초기화
        </button>
      </div>

      <div className="px-40 py-8">
        <div className="flex items-center justify-between mb-6">
          <p className="text-sm text-gray-500">총 <span className="font-semibold text-gray-900">{sorted.length}개</span>의 정책이 있어요</p>
          <div className="flex items-center gap-3">
            <Dropdown value={sort} onChange={handleFilter(setSort)} options={SORT_OPTIONS} />
            <div className="flex items-center border border-gray-200 rounded-xl overflow-hidden">
              <button
                onClick={() => setViewMode("grid")}
                className={`p-2.5 transition-colors ${viewMode === "grid" ? "bg-primary-600 text-white" : "text-gray-400 hover:bg-gray-50"}`}
                aria-label="그리드 보기"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
                  <rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/>
                </svg>
              </button>
              <button
                onClick={() => setViewMode("list")}
                className={`p-2.5 transition-colors ${viewMode === "list" ? "bg-primary-600 text-white" : "text-gray-400 hover:bg-gray-50"}`}
                aria-label="리스트 보기"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/>
                  <line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/>
                </svg>
              </button>
            </div>
          </div>
        </div>

        {sorted.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-gray-400">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="mb-3">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            <p className="text-sm">조건에 맞는 정책이 없어요</p>
          </div>
        ) : (
          <>
            {viewMode === "grid" ? (
              <div className="grid grid-cols-3 gap-4">
                {paged.map(policy => (
                  <PolicyCard key={policy.id} policy={policy} visited={visitedIds.has(policy.id)} />
                ))}
              </div>
            ) : (
              <div className="flex flex-col gap-3">
                {paged.map(policy => (
                  <PolicyListItem key={policy.id} policy={policy} />
                ))}
              </div>
            )}

            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-1 mt-10">
                {Array.from({ length: totalPages }, (_, i) => i + 1).map(p => (
                  <button
                    key={p}
                    onClick={() => setPage(p)}
                    className={`w-9 h-9 rounded-full text-sm font-medium transition-colors ${
                      p === page ? "bg-primary-600 text-white" : "text-gray-600 hover:bg-gray-100"
                    }`}
                  >
                    {p}
                  </button>
                ))}
                {page < totalPages && (
                  <button
                    onClick={() => setPage(p => Math.min(p + 1, totalPages))}
                    className="w-9 h-9 flex items-center justify-center text-gray-500 hover:bg-gray-100 rounded-full text-lg leading-none"
                  >
                    ›
                  </button>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
