export interface SignupBusinessForm {
  status: string;
  industry: string;
  region: string;
  revenue: string;
  employees: string;
}

export function toSignupBusinessRequest(business: SignupBusinessForm) {
  const operating = business.status === "사업 중";
  return {
    businessStatus: operating,
    jobCategory: business.industry,
    region: business.region,
    annualRevenue: operating ? business.revenue || null : null,
    employeeCount: operating ? business.employees || null : null,
  };
}

export function isSignupBusinessValid(business: SignupBusinessForm): boolean {
  const operating = business.status === "사업 중";
  return business.industry !== "" && business.region !== "" &&
    (!operating || (business.revenue !== "" && business.employees !== ""));
}
