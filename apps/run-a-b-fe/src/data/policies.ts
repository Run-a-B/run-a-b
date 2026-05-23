export interface Policy {
  id: number;
  category: string;
  filterCategory: string;
  region: string;
  industry: string;
  isAIRecommended: boolean;
  title: string;
  description: string;
  relevance: number;
  agency: string;
  date: string;
}

export interface PolicyDetailData {
  announcementNo: string;
  department: string;
  applicationPeriod: string;
  supportScale: string;
  targetGroup: string;
  tags: string[];
  purposeText: string;
  targetConditions: string[];
  supportItems: { category: string; amount: string; method: string }[];
  applicationMethod: string;
  exclusions: string[];
  aiSummaryText: string;
  aiHighlights: { icon: string; label: string; content: string }[];
  businessImpact: { label: string; level: number; direction: "up" | "down"; tag: string; barColor: string; tagColor: string }[];
  reportData: {
    impactLabel: string;
    impactStyle: string;
    summary: string;
    details: string[];
    relatedIds: number[];
  };
}

export const MOCK_POLICIES: Policy[] = [
  {
    id: 1,
    category: "최저임금",
    filterCategory: "인건비 지원",
    region: "전국",
    industry: "전체",
    isAIRecommended: true,
    title: "2026년 최저임금 인상에 따른 소상공인 인건비 부담 완화 지원",
    description: "최저임금 1만 30원 적용에 따라 소규모 사업장 인건비 부담이 증가합니다. 일자리 안정자금 및 사회보험료 지원을 통해 실질적인 비용 절감이 가능합니다.",
    relevance: 92,
    agency: "고용노동부",
    date: "2026.01.15",
  },
  {
    id: 2,
    category: "노동·복지",
    filterCategory: "인건비 지원",
    region: "전국",
    industry: "전체",
    isAIRecommended: true,
    title: "사회보험료 지원 확대 — 고용보험·국민연금 사업주 부담 경감",
    description: "10인 미만 소규모 사업체 사업주의 고용보험 및 국민연금 부담분을 최대 80% 지원합니다. 월 지원액 최대 8만원.",
    relevance: 88,
    agency: "고용노동부",
    date: "2026.01.20",
  },
  {
    id: 3,
    category: "대출·자금",
    filterCategory: "자금 지원",
    region: "전국",
    industry: "전체",
    isAIRecommended: true,
    title: "소상공인 경영안정자금 대출 한도 확대 및 금리 인하",
    description: "소상공인시장진흥공단에서 운영하는 경영안정자금의 대출 한도가 기존 2천만원에서 3천만원으로 확대되며, 연 2.5% 고정금리 적용.",
    relevance: 85,
    agency: "중소벤처기업부",
    date: "2026.02.03",
  },
  {
    id: 4,
    category: "에너지",
    filterCategory: "자금 지원",
    region: "서울특별시",
    industry: "음식점업",
    isAIRecommended: false,
    title: "소상공인 전기료·가스비 절감 에너지 바우처 2026년 확대 시행",
    description: "음식업·소매업 소상공인을 대상으로 에너지 효율화 설비 구축 비용의 최대 80%를 지원합니다. 1인당 지원 한도 500만원.",
    relevance: 78,
    agency: "산업통상자원부",
    date: "2026.01.28",
  },
  {
    id: 5,
    category: "디지털",
    filterCategory: "창업 지원",
    region: "전국",
    industry: "도소매업",
    isAIRecommended: false,
    title: "소상공인 디지털 전환 바우처 — 키오스크·POS·배달앱 비용 지원",
    description: "매장 디지털화를 위한 키오스크, POS 시스템 도입 비용을 최대 400만원까지 지원합니다. 2026년 상반기 선착순 접수.",
    relevance: 74,
    agency: "중소벤처기업부",
    date: "2026.02.10",
  },
  {
    id: 6,
    category: "세금",
    filterCategory: "세금 감면",
    region: "전국",
    industry: "전체",
    isAIRecommended: false,
    title: "2026 간이과세자 기준 상향 — 연매출 1억 400만원으로 확대",
    description: "간이과세자 기준이 연매출 8,000만원에서 1억 400만원으로 상향되어 더 많은 소상공인이 간편한 세금 신고를 이용할 수 있게 됩니다.",
    relevance: 70,
    agency: "기획재정부",
    date: "2026.01.05",
  },
  {
    id: 7,
    category: "창업지원",
    filterCategory: "창업 지원",
    region: "경기도",
    industry: "전체",
    isAIRecommended: false,
    title: "소상공인 창업 패키지 지원 — 초기 창업비용 최대 1천만원 지원",
    description: "예비 창업자 및 창업 1년 이내 소상공인을 대상으로 초기 사업화 자금 및 멘토링을 지원합니다.",
    relevance: 65,
    agency: "중소벤처기업부",
    date: "2026.03.01",
  },
  {
    id: 8,
    category: "임차료",
    filterCategory: "임차료 지원",
    region: "서울특별시",
    industry: "전체",
    isAIRecommended: false,
    title: "소상공인 임차료 부담 경감 — 착한 임대인 세액공제 확대",
    description: "임대료를 자발적으로 인하한 임대인에게 인하액의 70%를 세액공제하는 제도가 확대 시행됩니다.",
    relevance: 62,
    agency: "국세청",
    date: "2026.02.20",
  },
  {
    id: 9,
    category: "교육",
    filterCategory: "교육/컨설팅",
    region: "전국",
    industry: "전체",
    isAIRecommended: false,
    title: "소상공인 경영역량 강화 교육 — 온라인 무료 과정 100종 제공",
    description: "마케팅, 세무, 인사관리 등 경영 전반에 걸친 온라인 교육 과정을 무료로 제공합니다. 수료 시 수강증 발급.",
    relevance: 58,
    agency: "소상공인시장진흥공단",
    date: "2026.01.10",
  },
  {
    id: 10,
    category: "디지털",
    filterCategory: "창업 지원",
    region: "부산광역시",
    industry: "도소매업",
    isAIRecommended: false,
    title: "전통시장 스마트화 지원 — Wi-Fi·CCTV·무인결제 인프라 구축",
    description: "전통시장 내 스마트 인프라 구축을 위한 설비 비용을 최대 90% 지원합니다. 시장 단위로 신청 가능.",
    relevance: 55,
    agency: "중소벤처기업부",
    date: "2026.03.15",
  },
  {
    id: 11,
    category: "노동·복지",
    filterCategory: "인건비 지원",
    region: "전국",
    industry: "전체",
    isAIRecommended: false,
    title: "소상공인 고용보험 특례 가입 — 자영업자 실업급여 수급 확대",
    description: "자영업자 고용보험 특례 가입 대상이 확대되어 폐업 시 실업급여를 받을 수 있는 소상공인이 늘어납니다.",
    relevance: 52,
    agency: "고용노동부",
    date: "2026.02.15",
  },
  {
    id: 12,
    category: "대출·자금",
    filterCategory: "자금 지원",
    region: "전국",
    industry: "전체",
    isAIRecommended: false,
    title: "소상공인 긴급경영안정자금 — 재난·재해 피해 업체 우선 지원",
    description: "재난 및 재해로 피해를 입은 소상공인에게 긴급 경영안정자금을 우선 지원합니다. 최대 5천만원, 연 1.5%.",
    relevance: 48,
    agency: "소상공인시장진흥공단",
    date: "2026.01.25",
  },
];

export const POLICY_DETAILS: Record<number, PolicyDetailData> = {
  1: {
    announcementNo: "고용부-2026-0115",
    department: "고용노동부 고용정책실",
    applicationPeriod: "2026.02.01 ~ 2026.12.31",
    supportScale: "월 최대 30만원",
    targetGroup: "30인 미만 사업장",
    tags: ["최저임금", "노동·고용", "소상공인 지원"],
    purposeText:
      "2026년 최저임금이 시간당 10,030원(전년 대비 1.7% 인상)으로 결정됨에 따라, 영세 소상공인 및 소규모 사업장의 인건비 부담을 완화하고 지속 가능한 고용 환경을 조성하기 위하여 일자리 안정자금 지원 사업을 시행합니다.",
    targetConditions: [
      "고용보험에 가입된 상시 근로자 30인 미만 사업장",
      "최저임금 이상으로 근로자에게 임금을 지급하는 사업주",
      "사업자등록증 보유 및 정상 사업 운영 중인 자",
    ],
    supportItems: [
      { category: "5인 미만 사업장", amount: "1인당 월 30만원", method: "직접 지급" },
      { category: "5인~30인 미만 사업장", amount: "1인당 월 20만원", method: "직접 지급" },
    ],
    applicationMethod: "온라인(고용보험 홈페이지) 또는 관할 고용센터 방문 접수",
    exclusions: ["공공기관, 공사 등 준공공 사업장 및 금융·보험업"],
    aiSummaryText:
      "2026년 최저임금이 시간당 10,030원으로 오르면서 소규모 사업장의 인건비 부담이 늘어요. 정부는 30인 미만 사업장에 월 최대 30만원의 일자리 안정자금을 직접 지원합니다. 음식업·소매업처럼 직원을 고용하는 소상공인이라면 꼭 챙겨야 할 정책이에요.",
    aiHighlights: [
      { icon: "money", label: "지원 금액", content: "직원 1인당 월 최대 30만원 직접 지급 (5인 미만 기준)" },
      { icon: "check", label: "신청 자격", content: "고용보험 가입 + 최저임금 이상 지급 + 30인 미만 사업장" },
      { icon: "calendar", label: "신청 기간", content: "2026년 2월 1일 ~ 12월 31일 (온라인 및 방문 접수 모두 가능)" },
    ],
    businessImpact: [
      { label: "인건비", level: 85, direction: "up", tag: "증가", barColor: "bg-red-400", tagColor: "text-red-500" },
      { label: "지원금", level: 90, direction: "up", tag: "혜택", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "순부담", level: 30, direction: "down", tag: "경감", barColor: "bg-yellow-400", tagColor: "text-yellow-600" },
    ],
    reportData: {
      impactLabel: "일부 부정적 영향",
      impactStyle: "bg-red-50 text-red-600 border border-red-100",
      summary: "최저임금 인상으로 월 인건비가 약 5만원 증가하지만, 일자리 안정자금을 신청하면 실질 부담은 오히려 줄어들어요.",
      details: [
        "음식업 기준 직원 3명 고용 시, 최저임금 인상에 따른 월 추가 인건비는 약 15,600원 × 3명 = 약 47,000원이에요. 일자리 안정자금(월 20만원 × 3명 = 60만원)을 수령하면 오히려 월 548,000원의 순이익이 발생해요.",
        "단, 고용보험 미가입 직원이 있다면 해당 인원은 지원 대상에서 제외되므로 먼저 고용보험 가입 여부를 확인하세요.",
      ],
      relatedIds: [2, 11],
    },
  },
  2: {
    announcementNo: "고용부-2026-0120",
    department: "고용노동부 사회보험정책과",
    applicationPeriod: "2026.03.01 ~ 2026.12.31",
    supportScale: "월 최대 8만원",
    targetGroup: "10인 미만 사업장",
    tags: ["노동·복지", "사회보험", "인건비 지원"],
    purposeText:
      "소규모 사업체의 사회보험료 부담을 경감하여 고용 안정성을 높이고, 근로자의 사회안전망 가입률을 제고하기 위해 고용보험 및 국민연금 사업주 부담분 일부를 지원합니다.",
    targetConditions: [
      "상시 근로자 10인 미만 소규모 사업체",
      "고용보험 및 국민연금에 가입된 근로자를 고용한 사업주",
      "월평균 보수 260만원 미만 근로자 보유 사업장",
    ],
    supportItems: [
      { category: "고용보험료", amount: "사업주 부담분의 80%", method: "보험료 차감" },
      { category: "국민연금료", amount: "사업주 부담분의 80%", method: "보험료 차감" },
    ],
    applicationMethod: "근로복지공단 지사 방문 또는 온라인(4대 사회보험 포털)",
    exclusions: ["월평균 보수 260만원 이상 근로자", "법인 대표자 본인 가입분"],
    aiSummaryText:
      "10인 미만 소규모 사업체라면 고용보험과 국민연금 사업주 부담분의 최대 80%를 지원받을 수 있어요. 월 최대 8만원까지 절감 가능하고, 신청도 온라인으로 간단히 처리할 수 있어요.",
    aiHighlights: [
      { icon: "money", label: "지원 금액", content: "사업주 부담 보험료의 최대 80%, 월 최대 8만원" },
      { icon: "check", label: "신청 자격", content: "상시 근로자 10인 미만 + 월평균 보수 260만원 미만 근로자 보유" },
      { icon: "calendar", label: "신청 기간", content: "2026년 3월 1일 ~ 12월 31일" },
    ],
    businessImpact: [
      { label: "보험료 부담", level: 75, direction: "up", tag: "증가", barColor: "bg-red-400", tagColor: "text-red-500" },
      { label: "지원금", level: 80, direction: "up", tag: "혜택", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "실질 비용", level: 25, direction: "down", tag: "절감", barColor: "bg-yellow-400", tagColor: "text-yellow-600" },
    ],
    reportData: {
      impactLabel: "긍정적 영향",
      impactStyle: "bg-green-50 text-green-600 border border-green-100",
      summary: "사업주 부담 보험료의 80%를 지원받아 직원 1인당 월 최대 8만원을 절감할 수 있어요.",
      details: [
        "고용보험과 국민연금 사업주 부담분 합산 월 약 5~10만원 중 80%를 돌려받을 수 있어요. 직원 3명 기준 월 최대 24만원 절감이 가능해요.",
        "신청 후 다음 달부터 보험료 청구액에서 자동 차감되니 별도 추가 절차 없이 편리하게 혜택을 받을 수 있어요.",
      ],
      relatedIds: [1, 11],
    },
  },
  3: {
    announcementNo: "중기부-2026-0203",
    department: "중소벤처기업부 소상공인정책과",
    applicationPeriod: "2026.03.01 ~ 2026.11.30",
    supportScale: "최대 3천만원",
    targetGroup: "소상공인 전체",
    tags: ["대출·자금", "경영안정", "저금리"],
    purposeText:
      "소상공인의 경영 안정을 위해 운전자금 및 시설자금 대출 한도를 확대하고 금리를 인하하여 자금 조달 부담을 낮추고 지속 가능한 경영 환경을 조성합니다.",
    targetConditions: [
      "사업자등록증을 보유한 소상공인 (상시 근로자 5인 미만)",
      "부동산 임대업, 금융업 등 일부 업종 제외",
      "신용점수 600점 이상",
    ],
    supportItems: [
      { category: "운전자금", amount: "최대 2천만원", method: "직접 대출 (연 2.5%)" },
      { category: "시설자금", amount: "최대 3천만원", method: "직접 대출 (연 2.0%)" },
    ],
    applicationMethod: "소상공인시장진흥공단 지역 센터 방문 또는 온라인 신청",
    exclusions: ["부동산 임대업, 금융·보험업, 도박·사행업"],
    aiSummaryText:
      "소상공인이 경영 위기를 넘길 수 있도록 저금리 대출 한도가 2천만원에서 3천만원으로 늘어났어요. 연 2.5% 고정금리라 이자 부담도 낮아요. 운영 자금이 필요한 소상공인이라면 올해 꼭 확인해야 할 정책이에요.",
    aiHighlights: [
      { icon: "money", label: "대출 한도", content: "시설자금 최대 3천만원, 운전자금 최대 2천만원" },
      { icon: "check", label: "대출 금리", content: "연 2.0~2.5% 고정금리 (시중금리 대비 절반 수준)" },
      { icon: "calendar", label: "신청 기간", content: "2026년 3월 1일 ~ 11월 30일 (예산 소진 시 마감)" },
    ],
    businessImpact: [
      { label: "자금 조달", level: 90, direction: "up", tag: "확대", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "이자 비용", level: 35, direction: "down", tag: "절감", barColor: "bg-yellow-400", tagColor: "text-yellow-600" },
      { label: "경영 안정", level: 80, direction: "up", tag: "향상", barColor: "bg-green-400", tagColor: "text-green-600" },
    ],
    reportData: {
      impactLabel: "긍정적 영향",
      impactStyle: "bg-green-50 text-green-600 border border-green-100",
      summary: "연 2.5% 고정금리로 최대 3천만원을 빌려 시중금리 대비 이자를 절반 이하로 줄일 수 있어요.",
      details: [
        "3천만원을 연 2.5%로 대출받으면 월 이자는 약 62,500원이에요. 시중은행 평균 금리(연 5.5%) 대비 연간 90만원가량 이자 절감 효과가 있어요.",
        "운전자금 2천만원을 별도로 신청하면 시설·운전자금 합산 최대 5천만원까지 활용할 수 있어요.",
      ],
      relatedIds: [12],
    },
  },
  4: {
    announcementNo: "산업부-2026-0128",
    department: "산업통상자원부 에너지효율과",
    applicationPeriod: "2026.02.15 ~ 2026.10.31",
    supportScale: "최대 500만원",
    targetGroup: "음식업·소매업 소상공인",
    tags: ["에너지", "전기료 절감", "바우처"],
    purposeText:
      "에너지 비용 상승으로 어려움을 겪는 소상공인의 에너지 부담을 완화하고 효율적인 에너지 사용 환경을 조성하기 위해 에너지 효율화 설비 구축 비용을 지원합니다.",
    targetConditions: [
      "음식점업, 소매업 등 에너지 다소비 업종 소상공인",
      "서울특별시 소재 사업장 (지역 한정)",
      "사업자등록 후 6개월 이상 영업 중인 사업장",
    ],
    supportItems: [
      { category: "LED 조명 교체", amount: "설치비의 80% (최대 100만원)", method: "바우처 지급" },
      { category: "고효율 냉난방기", amount: "설치비의 80% (최대 300만원)", method: "바우처 지급" },
      { category: "인버터 냉장고", amount: "설치비의 80% (최대 100만원)", method: "바우처 지급" },
    ],
    applicationMethod: "서울시 소상공인지원센터 방문 또는 에너지바우처 시스템 온라인 신청",
    exclusions: ["서울 외 지역 사업장", "사업자등록 6개월 미만 사업장"],
    aiSummaryText:
      "서울 음식업·소매업 소상공인이라면 에너지 효율화 설비 비용의 최대 80%, 최대 500만원을 바우처로 받을 수 있어요. LED 조명부터 고효율 냉난방기까지 지원 항목이 다양해요.",
    aiHighlights: [
      { icon: "money", label: "지원 금액", content: "설비 구축 비용의 최대 80%, 1인당 최대 500만원" },
      { icon: "check", label: "신청 자격", content: "서울시 소재 음식업·소매업 + 6개월 이상 영업" },
      { icon: "calendar", label: "신청 기간", content: "2026년 2월 15일 ~ 10월 31일 (선착순)" },
    ],
    businessImpact: [
      { label: "에너지 비용", level: 70, direction: "down", tag: "절감", barColor: "bg-green-400", tagColor: "text-blue-500" },
      { label: "초기 투자", level: 80, direction: "up", tag: "지원", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "운영비", level: 40, direction: "down", tag: "경감", barColor: "bg-yellow-400", tagColor: "text-yellow-600" },
    ],
    reportData: {
      impactLabel: "긍정적 영향",
      impactStyle: "bg-green-50 text-green-600 border border-green-100",
      summary: "에너지 효율화 설비 교체 후 전기료가 월 평균 20~30% 줄어들어요.",
      details: [
        "LED 조명과 고효율 냉난방기로 교체 시 연간 전기료 절감액은 약 120~180만원으로 추정돼요. 지원받은 500만원의 투자금은 약 3~4년 안에 회수 가능해요.",
        "에너지 절감 실적 보고를 해두면 추가 인센티브도 받을 수 있으니, 설치 후 에너지관리공단에 실적 등록을 꼭 챙기세요.",
      ],
      relatedIds: [5],
    },
  },
  5: {
    announcementNo: "중기부-2026-0210",
    department: "중소벤처기업부 디지털혁신과",
    applicationPeriod: "2026.03.01 ~ 2026.06.30",
    supportScale: "최대 400만원",
    targetGroup: "도소매업 소상공인",
    tags: ["디지털", "키오스크", "POS 지원"],
    purposeText:
      "소상공인의 디지털 전환을 가속화하고 경영 효율성을 높이기 위해 키오스크, POS 시스템, 배달 플랫폼 등 디지털 인프라 도입 비용을 바우처 형태로 지원합니다.",
    targetConditions: [
      "도소매업, 음식점업 소상공인 (상시 근로자 5인 미만)",
      "디지털 기기 미보유 또는 도입 3년 이상 경과 사업장",
      "사업자등록 1년 이상",
    ],
    supportItems: [
      { category: "키오스크", amount: "구입비의 70% (최대 200만원)", method: "바우처 지급" },
      { category: "POS 시스템", amount: "구입비의 70% (최대 100만원)", method: "바우처 지급" },
      { category: "배달앱 가입비", amount: "최대 100만원", method: "바우처 지급" },
    ],
    applicationMethod: "온라인(중소벤처기업부 스마트상점 플랫폼) 선착순 접수",
    exclusions: ["부동산업, 금융업", "이미 동일 사업을 통해 지원받은 사업장"],
    aiSummaryText:
      "키오스크나 POS 도입을 고민하고 있다면 올해가 최적의 타이밍이에요. 구입비의 70%까지 최대 400만원을 바우처로 받을 수 있어요. 상반기 선착순 마감이니 서두르세요.",
    aiHighlights: [
      { icon: "money", label: "지원 금액", content: "디지털 기기 구입비의 최대 70%, 총 400만원 한도" },
      { icon: "check", label: "신청 자격", content: "도소매·음식점업 소상공인 + 사업자등록 1년 이상" },
      { icon: "calendar", label: "신청 기간", content: "2026년 3월 1일 ~ 6월 30일 (선착순 마감)" },
    ],
    businessImpact: [
      { label: "운영 효율", level: 85, direction: "up", tag: "향상", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "초기 비용", level: 60, direction: "up", tag: "지원", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "인건비", level: 30, direction: "down", tag: "절감", barColor: "bg-yellow-400", tagColor: "text-yellow-600" },
    ],
    reportData: {
      impactLabel: "긍정적 영향",
      impactStyle: "bg-green-50 text-green-600 border border-green-100",
      summary: "키오스크 도입 후 주문 처리 시간이 30% 단축되고 월 인건비를 30~50만원 절감할 수 있어요.",
      details: [
        "키오스크 1대 구입비 약 280만원 중 196만원을 지원받으면 자부담은 84만원이에요. 이 비용은 월 인건비 절감액으로 약 2개월 만에 회수 가능해요.",
        "POS 시스템 연동 시 매출 데이터 자동 집계가 가능해져 재고 관리와 세무 신고 준비 시간도 줄어들어요.",
      ],
      relatedIds: [4, 9],
    },
  },
  6: {
    announcementNo: "기재부-2026-0105",
    department: "기획재정부 세제실",
    applicationPeriod: "2026.01.01 ~ 상시",
    supportScale: "세금 신고 간소화",
    targetGroup: "연매출 1억 400만원 미만 사업자",
    tags: ["세금", "간이과세", "부가가치세"],
    purposeText:
      "간이과세자 적용 기준을 연매출 8,000만원에서 1억 400만원으로 상향하여 더 많은 소상공인이 간편한 세금 신고 제도의 혜택을 누릴 수 있도록 합니다.",
    targetConditions: [
      "직전 연도 연매출 1억 400만원 미만 개인사업자",
      "부동산 임대업, 과세유흥업소 등 일부 업종 제외",
      "신규 사업자도 즉시 적용 가능",
    ],
    supportItems: [
      { category: "부가가치세", amount: "일반과세 대비 세부담 40~50% 감소", method: "신고 간소화" },
      { category: "세금계산서 발행", amount: "의무 면제 (영수증 대체 가능)", method: "제도 적용" },
    ],
    applicationMethod: "별도 신청 없이 매출 기준 자동 적용 (국세청 안내 통보)",
    exclusions: ["부동산 임대업, 과세유흥업소, 전문직 일부"],
    aiSummaryText:
      "연매출 1억 400만원 미만이면 이제 간이과세자로 자동 분류돼요. 부가세 부담이 크게 줄고, 세금계산서 발행 의무도 없어져요. 별도 신청 없이 자동 적용되니 국세청 안내를 확인하세요.",
    aiHighlights: [
      { icon: "money", label: "세금 절감", content: "일반과세 대비 부가가치세 부담 40~50% 감소" },
      { icon: "check", label: "적용 기준", content: "직전 연도 연매출 1억 400만원 미만 개인사업자" },
      { icon: "calendar", label: "적용 시점", content: "2026년 1월 1일부터 자동 적용" },
    ],
    businessImpact: [
      { label: "세금 부담", level: 55, direction: "down", tag: "경감", barColor: "bg-green-400", tagColor: "text-blue-500" },
      { label: "행정 부담", level: 40, direction: "down", tag: "감소", barColor: "bg-green-400", tagColor: "text-blue-500" },
      { label: "현금 흐름", level: 60, direction: "up", tag: "개선", barColor: "bg-green-400", tagColor: "text-green-600" },
    ],
    reportData: {
      impactLabel: "긍정적 영향",
      impactStyle: "bg-green-50 text-green-600 border border-green-100",
      summary: "간이과세자 전환 시 부가가치세 부담이 연간 약 200~400만원 줄어들어요.",
      details: [
        "일반과세자 기준 연매출 1억원이면 부가세 납부액이 약 1,000만원이지만, 간이과세자는 업종별 부가가치율 적용으로 약 200~400만원 수준으로 줄어요.",
        "세금계산서 발행 의무도 없어져 거래처 서류 처리 부담이 크게 줄어들고, 별도 신청 없이 자동 적용돼요.",
      ],
      relatedIds: [8],
    },
  },
  7: {
    announcementNo: "중기부-2026-0301",
    department: "중소벤처기업부 창업진흥과",
    applicationPeriod: "2026.04.01 ~ 2026.09.30",
    supportScale: "최대 1천만원",
    targetGroup: "경기도 예비창업자·창업 1년 이내",
    tags: ["창업지원", "초기 자금", "멘토링"],
    purposeText:
      "소상공인의 창업 초기 진입 장벽을 낮추고 성공 가능성을 높이기 위해 초기 사업화 자금과 전문가 멘토링을 패키지로 지원합니다.",
    targetConditions: [
      "경기도 소재 예비창업자 또는 창업 1년 이내 소상공인",
      "연령 제한 없음 (단, 공고일 기준 사업자등록 미보유 또는 1년 이내)",
      "음식업, 소매업, 서비스업 등 일반 업종",
    ],
    supportItems: [
      { category: "초기 사업화 자금", amount: "최대 1천만원", method: "직접 지급 (사업비 정산)" },
      { category: "전문가 멘토링", amount: "총 20시간 무료", method: "매칭 지원" },
    ],
    applicationMethod: "경기도 소상공인지원센터 방문 접수 또는 온라인 신청",
    exclusions: ["창업 1년 초과 사업자", "부동산·금융업종"],
    aiSummaryText:
      "경기도에서 창업을 준비 중이라면 초기 자금 1천만원과 전문가 멘토링 20시간을 무료로 받을 수 있어요. 자금 지원과 멘토링이 패키지로 묶여 있어 창업 초기 실패 위험을 크게 줄여줘요.",
    aiHighlights: [
      { icon: "money", label: "지원 금액", content: "초기 사업화 자금 최대 1천만원 직접 지급" },
      { icon: "check", label: "신청 자격", content: "경기도 소재 예비창업자 또는 창업 1년 이내" },
      { icon: "calendar", label: "신청 기간", content: "2026년 4월 1일 ~ 9월 30일" },
    ],
    businessImpact: [
      { label: "초기 자금", level: 90, direction: "up", tag: "지원", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "사업 위험", level: 50, direction: "down", tag: "감소", barColor: "bg-yellow-400", tagColor: "text-yellow-600" },
      { label: "성장 가능성", level: 75, direction: "up", tag: "향상", barColor: "bg-green-400", tagColor: "text-green-600" },
    ],
    reportData: {
      impactLabel: "긍정적 영향",
      impactStyle: "bg-green-50 text-green-600 border border-green-100",
      summary: "초기 자금 1천만원과 전문가 멘토링 20시간으로 창업 첫해 생존율을 크게 높일 수 있어요.",
      details: [
        "창업 초기 6개월이 가장 어려운 시기인데, 1천만원의 사업화 자금이 임대료·인테리어·초도 물품 구입을 커버해요.",
        "멘토링 20시간은 세무·마케팅·인허가 전문가와 1:1로 진행되어 실제 창업 현장에서 바로 적용할 수 있는 조언을 받을 수 있어요.",
      ],
      relatedIds: [3, 9],
    },
  },
  8: {
    announcementNo: "국세청-2026-0220",
    department: "국세청 소득세과",
    applicationPeriod: "2026.01.01 ~ 상시",
    supportScale: "임대료 인하액의 70% 세액공제",
    targetGroup: "임대료를 인하한 건물주 (소상공인 간접 지원)",
    tags: ["임차료", "세액공제", "착한 임대인"],
    purposeText:
      "소상공인 임차료 부담을 완화하기 위해 임대인이 자발적으로 임대료를 인하할 경우 인하액의 70%를 세액공제 혜택으로 제공하여 임대료 인하를 유도합니다.",
    targetConditions: [
      "소상공인과 임대차 계약을 맺은 건물 소유자",
      "2026년 1~12월 중 임대료를 인하한 경우",
      "직전 대비 임대료 인하율 5% 이상",
    ],
    supportItems: [
      { category: "세액공제", amount: "임대료 인하액의 70%", method: "종합소득세 신고 시 공제" },
    ],
    applicationMethod: "종합소득세 신고 시 세액공제 항목 기재 (별도 신청 불필요)",
    exclusions: ["임대료 인하율 5% 미만", "특수관계인 간 임대차 계약"],
    aiSummaryText:
      "건물주가 임대료를 5% 이상 낮춰주면 세금의 70%를 돌려받을 수 있어요. 소상공인 입장에서는 건물주와 협의해 임차료를 낮출 좋은 기회예요. 착한 임대인 제도를 적극 활용하세요.",
    aiHighlights: [
      { icon: "money", label: "절감 효과", content: "임대료 인하액의 최대 70%만큼 임대인 세금 감면" },
      { icon: "check", label: "적용 조건", content: "임대료 5% 이상 인하 + 소상공인 대상 임대차 계약" },
      { icon: "calendar", label: "적용 기간", content: "2026년 1월 1일 ~ 12월 31일" },
    ],
    businessImpact: [
      { label: "임차료", level: 65, direction: "down", tag: "협의", barColor: "bg-green-400", tagColor: "text-blue-500" },
      { label: "운영 비용", level: 50, direction: "down", tag: "절감", barColor: "bg-yellow-400", tagColor: "text-yellow-600" },
      { label: "수익성", level: 55, direction: "up", tag: "개선", barColor: "bg-green-400", tagColor: "text-green-600" },
    ],
    reportData: {
      impactLabel: "긍정적 영향",
      impactStyle: "bg-green-50 text-green-600 border border-green-100",
      summary: "건물주와 협의해 임대료를 5% 낮추면 연간 120만원 이상 절감 효과를 바로 얻을 수 있어요.",
      details: [
        "월 임차료 200만원에서 5%(10만원) 인하 시 연간 120만원 절감이에요. 건물주는 인하액의 70%를 세금으로 돌려받으니 협의 가능성이 높아요.",
        "임대료 인하 계약서는 꼭 서면으로 작성하고, 인하 기간과 조건을 명확히 해두세요.",
      ],
      relatedIds: [3, 6],
    },
  },
  9: {
    announcementNo: "진흥원-2026-0110",
    department: "소상공인시장진흥공단 교육지원부",
    applicationPeriod: "2026.01.01 ~ 2026.12.31",
    supportScale: "무료 수강 (100종)",
    targetGroup: "소상공인 전체",
    tags: ["교육", "무료 강의", "경영 역량"],
    purposeText:
      "소상공인의 경영 역량 강화를 지원하기 위해 마케팅, 세무, 인사관리 등 경영 전반에 걸친 온라인 교육 과정 100종을 무료로 제공합니다.",
    targetConditions: [
      "사업자등록증을 보유한 소상공인 (상시 근로자 5인 미만)",
      "소상공인 마당 회원 가입 후 수강 신청",
      "업종 제한 없음",
    ],
    supportItems: [
      { category: "온라인 강의", amount: "100종 전 과정 무료", method: "플랫폼 온라인 수강" },
      { category: "수료증 발급", amount: "과정 완료 시 수강증 발급", method: "온라인 발급" },
    ],
    applicationMethod: "소상공인 마당(sbiz.or.kr) 회원가입 후 온라인 수강 신청",
    exclusions: ["상시 근로자 5인 이상 사업장"],
    aiSummaryText:
      "마케팅, 세무, SNS 활용 등 100가지 온라인 강의를 무료로 들을 수 있어요. 수료 시 수강증도 발급받을 수 있어요. 소상공인 마당 회원이라면 누구나 바로 신청할 수 있어요.",
    aiHighlights: [
      { icon: "money", label: "수강 비용", content: "전 과정 100% 무료 (100종 강의)" },
      { icon: "check", label: "신청 자격", content: "사업자등록증 보유 소상공인 (업종 무관)" },
      { icon: "calendar", label: "수강 기간", content: "연중 상시 수강 가능" },
    ],
    businessImpact: [
      { label: "경영 지식", level: 80, direction: "up", tag: "향상", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "교육 비용", level: 100, direction: "down", tag: "절감", barColor: "bg-green-400", tagColor: "text-blue-500" },
      { label: "사업 역량", level: 70, direction: "up", tag: "강화", barColor: "bg-green-400", tagColor: "text-green-600" },
    ],
    reportData: {
      impactLabel: "긍정적 영향",
      impactStyle: "bg-green-50 text-green-600 border border-green-100",
      summary: "SNS 마케팅·세무 교육 수강 후 외부 대행 비용 월 30~50만원을 직접 절감할 수 있어요.",
      details: [
        "마케팅 과정(블로그·인스타그램·배달앱 최적화)은 외부 대행사 비용을 절감하고, 세무 교육 수료 후 신고 오류를 줄이면 세무사 자문비도 줄어들어요.",
        "수강 수료증은 향후 지원사업 신청 시 가점으로 활용 가능하니 꼭 보관해 두세요.",
      ],
      relatedIds: [5, 7],
    },
  },
  10: {
    announcementNo: "중기부-2026-0315",
    department: "중소벤처기업부 전통시장과",
    applicationPeriod: "2026.04.01 ~ 2026.08.31",
    supportScale: "설비 비용의 90%",
    targetGroup: "부산 전통시장 상인",
    tags: ["디지털", "전통시장", "스마트화"],
    purposeText:
      "전통시장의 경쟁력을 강화하고 고객 편의를 높이기 위해 Wi-Fi, CCTV, 무인결제 단말기 등 스마트 인프라 구축 비용을 시장 단위로 지원합니다.",
    targetConditions: [
      "부산광역시 소재 전통시장 (시장 단위 신청)",
      "시장관리공단 또는 상인회가 주체로 신청",
      "100개 점포 이상 시장 우선 지원",
    ],
    supportItems: [
      { category: "무선 Wi-Fi 설치", amount: "설치비의 90%", method: "직접 시공 지원" },
      { category: "CCTV 설치", amount: "설치비의 90%", method: "직접 시공 지원" },
      { category: "무인결제 단말기", amount: "구입비의 90%", method: "바우처 지급" },
    ],
    applicationMethod: "부산시 소상공인지원센터 통해 시장 단위 신청 (개인 신청 불가)",
    exclusions: ["개인 점포 단독 신청", "100개 점포 미만 소규모 시장"],
    aiSummaryText:
      "부산 전통시장 상인이라면 스마트 인프라 구축 비용의 90%를 지원받을 수 있어요. Wi-Fi부터 무인결제 단말기까지 지원 범위가 넓어요. 시장 단위로 신청해야 하니 상인회에 문의하세요.",
    aiHighlights: [
      { icon: "money", label: "지원 비율", content: "스마트 인프라 설비 비용의 최대 90% 지원" },
      { icon: "check", label: "신청 자격", content: "부산시 전통시장 + 시장관리공단·상인회 단위 신청" },
      { icon: "calendar", label: "신청 기간", content: "2026년 4월 1일 ~ 8월 31일" },
    ],
    businessImpact: [
      { label: "인프라 비용", level: 90, direction: "up", tag: "지원", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "고객 편의", level: 75, direction: "up", tag: "향상", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "매출", level: 55, direction: "up", tag: "기대", barColor: "bg-yellow-400", tagColor: "text-green-600" },
    ],
    reportData: {
      impactLabel: "긍정적 영향",
      impactStyle: "bg-green-50 text-green-600 border border-green-100",
      summary: "스마트 인프라 구축 후 온라인 결제 비율이 높아지고 고객 유입이 20% 이상 증가해요.",
      details: [
        "Wi-Fi 설치 후 배달앱·모바일 주문 연동이 가능해져 비대면 매출 채널을 새롭게 열 수 있어요.",
        "CCTV와 무인결제 단말기 도입으로 야간 운영 안전성이 높아지고, 결제 처리 속도가 빨라져 고객 만족도도 올라가요.",
      ],
      relatedIds: [5],
    },
  },
  11: {
    announcementNo: "고용부-2026-0215",
    department: "고용노동부 고용보험기획과",
    applicationPeriod: "2026.03.01 ~ 상시",
    supportScale: "실업급여 수급 가능",
    targetGroup: "자영업자 전체",
    tags: ["노동·복지", "고용보험", "자영업자"],
    purposeText:
      "자영업자의 사회안전망을 강화하기 위해 고용보험 특례 가입 대상을 확대하고, 폐업 시 실업급여 수급 요건을 완화하여 소상공인의 재기를 지원합니다.",
    targetConditions: [
      "사업자등록증을 보유한 자영업자 (전 업종)",
      "고용보험 임의 가입 신청 후 1년 이상 보험료 납부",
      "폐업 또는 매출 급감(전년 대비 20% 이상) 인정 시",
    ],
    supportItems: [
      { category: "실업급여", amount: "월 최대 198만원 (최대 8개월)", method: "현금 지급" },
      { category: "재취업 지원", amount: "직업훈련비 무료 지원", method: "바우처 지급" },
    ],
    applicationMethod: "관할 고용센터 방문 또는 고용보험 홈페이지 임의 가입 신청",
    exclusions: ["고용보험 납부 기간 1년 미만", "자진 폐업(경영 악화 외)"],
    aiSummaryText:
      "이제 자영업자도 고용보험에 가입하면 폐업 시 월 최대 198만원의 실업급여를 최대 8개월 받을 수 있어요. 가입 요건도 완화됐으니 아직 가입 전이라면 지금 바로 신청하는 게 유리해요.",
    aiHighlights: [
      { icon: "money", label: "실업급여", content: "폐업 시 월 최대 198만원, 최대 8개월 수급" },
      { icon: "check", label: "가입 조건", content: "사업자등록 보유 + 임의 가입 후 1년 이상 보험료 납부" },
      { icon: "calendar", label: "신청 기간", content: "2026년 3월 1일부터 상시 신청 가능" },
    ],
    businessImpact: [
      { label: "폐업 위험", level: 60, direction: "down", tag: "대비", barColor: "bg-yellow-400", tagColor: "text-yellow-600" },
      { label: "안전망", level: 85, direction: "up", tag: "강화", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "재기 가능성", level: 70, direction: "up", tag: "향상", barColor: "bg-green-400", tagColor: "text-green-600" },
    ],
    reportData: {
      impactLabel: "혼합 영향",
      impactStyle: "bg-yellow-50 text-yellow-700 border border-yellow-100",
      summary: "월 보험료 약 3~5만원 납부로 폐업 시 월 최대 198만원의 실업급여를 최대 8개월 받을 수 있어요.",
      details: [
        "보험료 대비 수령 가능 실업급여 비율을 계산하면, 1년 납부 시 약 1,600만원의 안전망을 확보하는 효과예요.",
        "임의 해지 후 재가입 시 대기 기간이 생기니, 한 번 가입하면 유지하는 것이 유리해요.",
      ],
      relatedIds: [2, 1],
    },
  },
  12: {
    announcementNo: "진흥원-2026-0125",
    department: "소상공인시장진흥공단 긴급지원팀",
    applicationPeriod: "재난 발생 후 60일 이내",
    supportScale: "최대 5천만원",
    targetGroup: "재난·재해 피해 소상공인",
    tags: ["대출·자금", "긴급 지원", "재난 복구"],
    purposeText:
      "재난 및 재해로 피해를 입은 소상공인이 신속하게 경영을 재개할 수 있도록 긴급 경영안정자금을 우선 지원하여 생계와 사업 지속성을 보호합니다.",
    targetConditions: [
      "자연재해, 화재 등 재난으로 피해를 입은 소상공인",
      "지자체 재난 피해 확인서 발급 사업장",
      "상시 근로자 5인 미만 소상공인",
    ],
    supportItems: [
      { category: "긴급 운전자금", amount: "최대 3천만원 (연 1.5%)", method: "직접 대출" },
      { category: "시설 복구자금", amount: "최대 5천만원 (연 1.5%)", method: "직접 대출" },
    ],
    applicationMethod: "소상공인시장진흥공단 지역 센터 긴급 방문 접수 (재난 발생 60일 이내)",
    exclusions: ["재난 피해 확인서 미발급 사업장", "이미 타 긴급지원을 받은 사업장"],
    aiSummaryText:
      "화재나 자연재해로 피해를 입었다면 연 1.5%의 저금리로 최대 5천만원을 긴급 대출받을 수 있어요. 재난 발생 60일 이내에 신청해야 하니 피해 즉시 지자체 확인서를 받고 신청하세요.",
    aiHighlights: [
      { icon: "money", label: "대출 한도", content: "시설 복구자금 최대 5천만원, 연 1.5% 초저금리" },
      { icon: "check", label: "신청 자격", content: "지자체 재난 피해 확인서 발급 소상공인" },
      { icon: "calendar", label: "신청 기간", content: "재난 발생 후 60일 이내 (선착순 지원)" },
    ],
    businessImpact: [
      { label: "복구 자금", level: 90, direction: "up", tag: "지원", barColor: "bg-green-400", tagColor: "text-green-600" },
      { label: "이자 부담", level: 20, direction: "down", tag: "최저", barColor: "bg-yellow-400", tagColor: "text-yellow-600" },
      { label: "사업 재개", level: 80, direction: "up", tag: "가능", barColor: "bg-green-400", tagColor: "text-green-600" },
    ],
    reportData: {
      impactLabel: "긍정적 영향",
      impactStyle: "bg-green-50 text-green-600 border border-green-100",
      summary: "재난 발생 즉시 연 1.5% 초저금리로 최대 5천만원을 빌려 사업을 빠르게 재개할 수 있어요.",
      details: [
        "5천만원을 연 1.5%로 대출받으면 월 이자는 약 62,500원이에요. 시중은행(연 5~6%) 대비 이자 부담이 70% 이상 낮아요.",
        "재난 발생 60일 이내에 신청해야 하므로, 피해 확인서 발급을 최우선으로 처리하고 진흥공단에 즉시 연락하세요.",
      ],
      relatedIds: [3],
    },
  },
};
