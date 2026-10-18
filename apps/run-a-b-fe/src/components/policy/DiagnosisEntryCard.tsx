import { Link } from "react-router-dom";

interface DiagnosisEntryCardProps {
  policyId: number;
}

export function DiagnosisEntryCard({ policyId }: DiagnosisEntryCardProps) {
  return (
    <section className="bg-white rounded-2xl border border-gray-200 overflow-hidden">
      <div className="px-5 py-4 border-b border-gray-100">
        <h2 className="text-sm font-bold text-gray-800">60초 정책 진단</h2>
      </div>
      <div className="px-5 py-5">
        <p className="text-sm text-gray-600 leading-6 mb-4">
          내 사업 정보와 공고 조건을 비교하고, 항목별 판정 근거를 확인해 보세요.
        </p>
        <Link
          to={`/policies/${policyId}/diagnosis`}
          className="flex items-center justify-center w-full bg-primary-600 hover:bg-primary-700 text-white text-sm font-semibold py-3 rounded-xl transition-colors"
        >
          내 조건으로 진단하기
        </Link>
      </div>
    </section>
  );
}
