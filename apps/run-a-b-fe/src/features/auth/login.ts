import type { AxiosInstance, AxiosResponse } from "axios";

export function createEmailLoginSubmitter(api: AxiosInstance, storage: Pick<Storage, "setItem" | "removeItem">) {
  let pending: Promise<AxiosResponse> | null = null;
  return (email: string, password: string, rememberEmail: boolean) => {
    if (pending) return pending;
    const normalizedEmail = email.trim();
    pending = api.post("/api/v1/auth/login", { email: normalizedEmail, password })
      .then(response => {
        if (rememberEmail) storage.setItem("saved_login_email", normalizedEmail);
        else storage.removeItem("saved_login_email");
        return response;
      })
      .finally(() => { pending = null; });
    return pending;
  };
}
