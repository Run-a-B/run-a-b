import axios from "axios";

// 로컬 개발: localhost:8080 직접 호출
// 배포(production): Nginx가 같은 도메인에서 /api/ 요청을 프록시하므로 상대경로 사용
const api = axios.create({
  baseURL: import.meta.env.PROD ? "" : "http://localhost:8080",
});

// 요청 인터셉터: localStorage의 토큰을 모든 요청 헤더에 자동 첨부
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("access_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 응답 인터셉터: 401(토큰 만료/무효)이면 토큰 지우고 로그인으로
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("access_token");
      // 로그인 페이지가 아니면 이동
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);

export default api;