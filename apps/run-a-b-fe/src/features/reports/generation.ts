import { isAxiosError } from "axios";

type ReportClient = { post(url: string): Promise<unknown> };

export async function generatePolicyReport(
  client: ReportClient,
  policyId: number,
  isActive: () => boolean,
  onGenerated: (policyId: number) => void,
  onFailure: (message: string) => void,
) {
  try {
    await client.post(`/api/v1/policies/${policyId}/report`);
    if (isActive()) onGenerated(policyId);
  } catch (error) {
    if (!isActive()) return;
    const message = isAxiosError<{ message?: string }>(error) ? error.response?.data?.message : undefined;
    onFailure(message || "리포트 생성에 실패했어요");
  }
}
