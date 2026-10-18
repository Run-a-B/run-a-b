export const REVENUE_OPTIONS = [
  "1,000만원 미만",
  "1,000만원~5,000만원",
  "5,000만원~1억원",
  "1억원~3억원",
  "3억원~5억원",
  "5억원 이상",
] as const;

export const REGION_OPTIONS = [
  "서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시",
  "대전광역시", "울산광역시", "세종특별자치시", "경기도", "강원특별자치도",
  "충청북도", "충청남도", "전북특별자치도", "전라남도", "경상북도", "경상남도", "제주특별자치도",
] as const;

export const INDUSTRY_OPTIONS = [
  "음식점업", "카페/음료", "의류/패션", "뷰티/미용", "교육/학원",
  "IT/소프트웨어", "제조업", "도소매업", "기타",
] as const;

const regionAliases: Record<string, string> = {
  서울: "서울특별시", 부산: "부산광역시", 대구: "대구광역시", 인천: "인천광역시",
  광주: "광주광역시", 대전: "대전광역시", 울산: "울산광역시", 세종: "세종특별자치시",
  경기: "경기도", 강원: "강원특별자치도", 강원도: "강원특별자치도",
  충북: "충청북도", 충남: "충청남도", 전북: "전북특별자치도", 전라북도: "전북특별자치도",
  전남: "전라남도", 경북: "경상북도", 경남: "경상남도", 제주: "제주특별자치도", 제주도: "제주특별자치도",
};
const industryAliases: Record<string, string> = {
  음식점: "음식점업", "음식점업 (한식·양식·중식 등)": "음식점업", "카페/베이커리": "카페/음료",
  교육: "교육/학원", 제조: "제조업", 도소매: "도소매업",
};

export function normalizeBusinessRegion(value: string | null | undefined): string {
  return value ? regionAliases[value] ?? value : "";
}

export function normalizeBusinessIndustry(value: string | null | undefined): string {
  return value ? industryAliases[value] ?? value : "";
}
