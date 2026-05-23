import { Routes, Route, Navigate } from "react-router-dom";
import MyPageSidebar from "@/components/common/MyPageSidebar";
import MyInfoPage from "@/pages/mypage/MyInfoPage";
import MyBusinessPage from "@/pages/mypage/MyBusinessPage";
import Policies from "@/pages/Policies";

export default function MyPage() {
  return (
    <div className="flex pt-15 min-h-screen bg-gray-50">
      <MyPageSidebar />
      <main className="flex-1 px-12 py-10 flex justify-center">
        <Routes>
          <Route index element={<MyInfoPage />} />
          <Route path="info" element={<MyInfoPage />} />
          <Route path="business" element={<MyBusinessPage />} />
          <Route path="policies" element={<Policies />} />
          <Route path="*" element={<Navigate to="/mypage" replace />} />
        </Routes>
      </main>
    </div>
  );
}
