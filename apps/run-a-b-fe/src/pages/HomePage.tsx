import { Link } from "react-router-dom";

const policies = [
  {
    id: 1,
    tag: "최저임금",
    tagStyle: "bg-primary-100 text-primary-600",
    date: "2026.01.15",
    title: "2026년 최저임금 인상에 따른 소상공인 인건비 부담 완화 지원",
    description: "최저임금 1만 30원 적용에 따라 소규모 사업장 인건비 부담이 증가합니다. 일자리 안정자금 및 사회보험료 지원을 통해 실질적인 지원을 받을 수 있어요.",
    relevance: 92,
    source: "고용노동부",
  },
  {
    id: 2,
    tag: "지원금",
    tagStyle: "bg-green-100 text-green-600",
    date: "2026.02.03",
    title: "소상공인 경영안정자금 대출 한도 확대 및 금리 인하",
    description: "소상공인시장진흥공단에서 운영하는 경영안정자금의 대출 한도가 기존 2천만원에서 3천만원으로 확대되며, 연 2.5% 고정금리가 적용돼요.",
    relevance: 85,
    source: "중소벤처기업부",
  },
  {
    id: 3,
    tag: "에너지",
    tagStyle: "bg-yellow-100 text-yellow-600",
    date: "2026.01.28",
    title: "소상공인 전기료·가스비 절감 에너지 바우처 2026년 확대 시행",
    description: "음식업·소매업 소상공인을 대상으로 에너지 효율화 설비 구축 비용의 최대 80%를 지원합니다. 1인당 지원 한도 500만원.",
    relevance: 78,
    source: "산업통상자원부",
  },
]

export function HomePage() {
  return (
    <div>
      <section className="pt-30 px-40 flex bg-white pb-25 gap-5 border-b border-gray-200 truncate">
        <div className="flex-1 my-auto">
          <div className="inline-flex items-center gap-1.5 border-[1.5px] border-primary-400 text-xs font-semibold mb-5 px-3 py-1 rounded-sm text-primary-600 before:content-[''] before:size-1.5 before:rounded-full before:bg-primary-500 before:shrink-0">
            AI 기반 정책 분석 플랫폼
          </div>
          <h1 className="text-5xl font-black mb-8">
            내 가게에 맞는 정책,<br />
            <span className="text-primary-600 py-1">AI가 직접 분석해 드릴게요</span>
          </h1>
          <p className="leading-7 text-text-secondary mb-7">
          복잡한 공고문 직접 읽을 필요 없어요.<br/>
          내 업종과 지역에 맞는 정책을 찾아<br/>
          지원사업 신청까지 한 번에 이어드려요.
          </p>
          <div className="flex gap-5 ">
            <Link to={""} className="font-medium text-white bg-primary-600 px-7 py-4 rounded-2xl hover:bg-primary-700 all duration-300 ease-out hover:transform-[translateY(-1px)]">나에게 맞는 정책 찾기</Link>
            <Link to={""} className="font-medium border text-gray-700 border-gray-300 px-7 py-4 rounded-2xl hover:bg-gray-50 hover:border-gray-400">무료로 시작하기 ➔</Link>
          </div>
        </div>
        <div className="flex-1 pl-40 h-110">
          <div className="border flex-col border-gray-300 h-full rounded-3xl text-sm truncate">
            <p className="text-text-secondary p-5">서비스 주요 기능</p>
            <div className="border-[0.5px] border-gray-200"></div>

            <div className="flex items-center p-3 text-sm border-b border-[0.5px] border-gray-200 hover:bg-gray-50">
              <div className="w-7 h-7 bg-primary-50 flex justify-center items-center text-primary-600 font-bold rounded-lg border border-primary-100">01</div>
              <div className="">
                <p className="pl-3 pt-3 pb-1 text-text-secondary"><span className="text-gray-800 font-semibold">내 사업 정보로 맞춤 필터링</span><br />
                  업종·지역·매출·직원 수를 입력하면 관련 정책만 골라서 보여줘요
                </p>
              </div>
            </div>

            <div className="flex items-center p-3 text-sm border-b border-[0.5px] border-gray-200 hover:bg-gray-50">
              <div className="w-7 h-7 bg-primary-50 flex justify-center items-center text-primary-600 font-bold rounded-lg border border-primary-100">02</div>
              <div className="">
                <p className="pl-3 pt-3 pb-1 text-text-secondary"><span className="text-gray-800 font-semibold">공고문 AI 3줄 요약</span><br />
                  수십 페이지 공고문을 핵심만 뽑아 읽기 쉽게 정리해 드려요
                </p>
              </div>
            </div>

            <div className="flex items-center p-3 text-sm border-b border-[0.5px] border-gray-200 hover:bg-gray-50">
              <div className="w-7 h-7 bg-primary-50 flex justify-center items-center text-primary-600 font-bold rounded-lg border border-primary-100">03</div>
              <div className="">
                <p className="pl-3 pt-3 pb-1 text-text-secondary"><span className="text-gray-800 font-semibold">내 사업 영향도 AI 리포트</span><br />
                  이 정책이 내 인건비·세금·비용에 구체적으로 어떤 영향을 주는지 분석해요
                </p>
              </div>
            </div>

            <div className="flex items-center p-3 text-sm border-b border-[0.5px] border-gray-200 hover:bg-gray-50">
              <div className="w-7 h-7 bg-primary-50 flex justify-center items-center text-primary-600 font-bold rounded-lg border border-primary-100">04</div>
              <div className="">
                <p className="pl-3 pt-3 pb-1 text-text-secondary"><span className="text-gray-800 font-semibold">리포트 누적 저장</span><br />
                  생성한 분석 리포트를 모아보고 언제든 다시 확인할 수 있어요
                </p>
              </div>
            </div>
            <div className="flex gap-1.5 font-medium pt-6 px-5 text-sm text-primary-600 bg-primary-50 h-full before:content-[''] before:size-2 before:rounded-full before:bg-primary-500 before:shrink-0">
                <p className="-mt-1.5">회원가입 후 바로 모든 기능을 무료로 사용할 수 있어요</p>
            </div>
          

          </div>
        </div>
      </section>
      <section className="px-40 py-10">
        <div>
          <p className="text-xs pb-3 text-primary-500 font-medium">내 업종 맞춤</p>
          <h1 className="font-bold text-2xl pb-3">최신 정책</h1>
          <div className="flex items-center justify-between text-sm text-text-secondary font-medium mb-5">
            <p>최신 사업 정책들이에요</p>
            <Link to={""} className="text-primary-500">전체 보기 ➔</Link>
          </div>

          <div className="flex gap-5 mb-10">
            {policies.map((policy) => (
              <div key={policy.id} className="flex-1 border-2 border-gray-200 bg-white rounded-2xl p-6 flex flex-col gap-3 transition-all duration-300 ease-out hover:border-primary-300 hover:-translate-y-1 hover:shadow-[0_8px_32px_rgba(139,92,246,0.12)]">
                <div className="flex items-center justify-between">
                  <div className={`${policy.tagStyle} px-3 py-1 text-xs font-medium rounded-full`}>{policy.tag}</div>
                  <p className="text-xs text-text-secondary">{policy.date}</p>
                </div>
                <h2 className="text-base font-bold leading-snug line-clamp-2">{policy.title}</h2>
                <p className="text-sm text-text-secondary leading-relaxed line-clamp-2">{policy.description}</p>
                <div className="flex items-center justify-between mt-auto pt-2">
                  <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-primary-100">
                    <div className="w-10 h-1 rounded-full bg-gray-300 overflow-hidden">
                      <div className="h-full rounded-full bg-primary-600" style={{ width: `${policy.relevance}%` }} />
                    </div>
                    <span className="text-xs font-bold text-primary-600">관련도 {policy.relevance}%</span>
                  </div>
                  <span className="text-xs text-text-secondary">{policy.source}</span>
                </div>
              </div>
            ))}
          </div>
          
          <div className="w-full bg-primary-600 rounded-2xl px-6 py-10">
            <div className="flex items-center justify-between">
              <div className="flex-col">
                <p className="text-gray-300 text-sm font-medium mb-3">지금 바로 시작하세요</p>
                <pre className="text-2xl font-bold text-white mb-2">
                {`나에게 맞는 정책,
직접 찾아보세요
`}</pre>
                <p className="text-gray-300 text-sm font-medium ">업종, 지역, 키워드로 필터링해서</p>
                <p className="text-gray-300 text-sm font-medium">내 사업과 연관된 정책만 골라 볼 수 있어요.</p>
                </div>
                <div className="flex items-center justify-center">
                  <Link to={""} className="text-base bg-white px-8 py-4 text-primary-600 font-bold rounded-4xl transition-all duration-300 ease-out hover:transform-[scale(1.04)] hover:shadow-[0 8px 24px rgba(0,0,0,0.2)]">정책 모아보기 ➔</Link>
                </div>
            </div>
              
          </div>
        </div>
      </section>

      <section className="px-40 py-16 bg-gray-50">
        <p className="text-xs font-medium text-primary-500 mb-3">서비스 소개</p>
        <h2 className="text-2xl font-bold mb-10">Run a B만의 3가지 차별점</h2>
        <div className="flex gap-5">
          <div className="flex-1 bg-white rounded-2xl p-8 flex flex-col gap-5 border border-gray-200 transition-all duration-300 ease-out hover:-translate-y-1 hover:shadow-[0_8px_32px_rgba(139,92,246,0.12)] hover:border-primary-300">
            <div className="w-12 h-12 bg-primary-50 rounded-xl flex items-center justify-center"><img src="/box.svg" alt="box" className="w-6 h-6" /></div>
            <h3 className="text-base font-bold">통합 정책 허브</h3>
            <p className="text-sm text-text-secondary leading-7">흩어진 정책·지원사업을 한 곳에서 탐색할 수 있어요. 내 업종·지역 필터로 관련 정책만 쏙 골라보세요.</p>
          </div>
          <div className="flex-1 bg-white rounded-2xl p-8 flex flex-col gap-5 border border-primary-200 transition-all duration-300 ease-out hover:-translate-y-1 hover:shadow-[0_8px_32px_rgba(139,92,246,0.12)] hover:border-primary-300">
            <div className="w-12 h-12 bg-primary-50 rounded-xl flex items-center justify-center"><img src="/ai.svg" alt="ai" className="w-6 h-6" /></div>
            <h3 className="text-base font-bold">AI 맞춤 리포트</h3>
            <p className="text-sm text-text-secondary leading-7">복잡한 공고문을 AI가 쉬운 말로 요약하고, 내 사업 조건(업종·지역·매출·직원수)에 대입해 영향을 분석한 리포트를 생성해 드려요.</p>
          </div>
          <div className="flex-1 bg-white rounded-2xl p-8 flex flex-col gap-5 border border-gray-200 transition-all duration-300 ease-out hover:-translate-y-1 hover:shadow-[0_8px_32px_rgba(139,92,246,0.12)] hover:border-primary-300">
            <div className="w-12 h-12 bg-primary-50 rounded-xl flex items-center justify-center"><img src="/lamp.svg" alt="lamp" className="w-6 h-6" /></div>
            <h3 className="text-base font-bold">지원사업 맥락 추천</h3>
            <p className="text-sm text-text-secondary leading-7">단순 자격 매칭이 아니라, 정책 영향 분석 결과에 기반한 지원사업을 추천해 드려요. 피해를 줄이거나 혜택을 극대화할 수 있는 지원을 찾아드려요.</p>
          </div>
        </div>
      </section>
      <footer className="bg-gray-900 text-gray-400 px-100 py-15 text-center">
        <p className="font-bold text-xl text-white mb-3">Run a B</p>
        <p className="text-sm mb-3">소상공인과 예비 창업자가 정책 변화에 빠르게 대응하고, 받을 수 있는 지원은 놓치지 않도록 돕는 AI 플랫폼입니다.</p>
        <p className="text-sm text-gray-600">© 2026 Run a B. Capstone Project Team Frontier.</p>
      </footer> 
    </div>
  )
}
