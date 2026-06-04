import axios from "axios";

// 백엔드 베이스 URL (스프링 8080)
const api = axios.create({
  baseURL: "http://localhost:8080",
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