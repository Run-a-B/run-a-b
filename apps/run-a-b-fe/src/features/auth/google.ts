import type { AxiosInstance } from "axios";

export function loginWithGoogleCredential(api: AxiosInstance, credential?: string) {
  if (!credential) return Promise.reject(new Error("구글 로그인 정보를 확인하지 못했어요"));
  return api.post("/api/v1/auth/google", { id_token: credential });
}
