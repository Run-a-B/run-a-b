import axios from "axios";

// 로컬 개발: VITE_API_BASE_URL(미설정 시 localhost:8082)로 직접 호출
// 배포(production): Nginx가 같은 도메인에서 /api/ 요청을 프록시하므로 상대경로 사용
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL
    ?? (import.meta.env.PROD ? "" : "http://localhost:8082"),
});

const publicAuthPaths = new Set([
  "/api/v1/auth/login", "/api/v1/auth/signup", "/api/v1/auth/signup/complete",
  "/api/v1/auth/email/send", "/api/v1/auth/email/verify", "/api/v1/auth/google",
]);

function isPublicAuthRequest(url = "") {
  const path = url.replace(/^https?:\/\/[^/]+/, "").split("?")[0].replace(/\/+$/, "");
  return publicAuthPaths.has(path);
}

// 명시된 TEMP 등 요청 자격 증명은 유지하고, 공개 인증 요청에는 기존 세션을 첨부하지 않는다.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("access_token");
  if (token && !config.headers.has("Authorization") && !isPublicAuthRequest(config.url)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 현재 ACCESS 세션이 거절된 경우만 정리한다. 로그인/OTP 실패는 해당 화면에서 처리한다.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const token = localStorage.getItem("access_token");
    const requestAuthorization = axios.AxiosHeaders.from(error.config?.headers).get("Authorization");
    if (error.response?.status === 401 && !isPublicAuthRequest(error.config?.url)
      && token && requestAuthorization === `Bearer ${token}`) {
      localStorage.removeItem("access_token");
      localStorage.removeItem("user");
      // 로그인 페이지가 아니면 이동
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);

export default api;
