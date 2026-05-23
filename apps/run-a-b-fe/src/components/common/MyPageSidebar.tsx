import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";

const PersonIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
    <circle cx="12" cy="7" r="4"/>
  </svg>
);

const BriefcaseIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
    <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
  </svg>
);

const ReportIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
    <polyline points="14 2 14 8 20 8"/>
    <line x1="16" y1="13" x2="8" y2="13"/>
    <line x1="16" y1="17" x2="8" y2="17"/>
    <polyline points="10 9 9 9 8 9"/>
  </svg>
);

const SearchIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="11" cy="11" r="8"/>
    <line x1="21" y1="21" x2="16.65" y2="16.65"/>
  </svg>
);

const LogoutIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
    <polyline points="16 17 21 12 16 7"/>
    <line x1="21" y1="12" x2="9" y2="12"/>
  </svg>
);


const NAV_ITEMS = [
  { label: "내 정보",      to: "/mypage",           icon: <PersonIcon />,    group: 1 },
  { label: "내 사업 정보", to: "/mypage/business",   icon: <BriefcaseIcon />, group: 1 }
];

export default function MyPageSidebar() {
  const { pathname } = useLocation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <aside className="w-52 shrink-0 border-r border-gray-100 bg-white flex flex-col pt-8 px-4 min-h-[calc(100vh-60px)]">

      {/* Profile */}
      <div className="flex flex-col items-center mb-7">
        <div className="relative w-20 h-20">
          <div className="w-full h-full rounded-full bg-[linear-gradient(135deg,var(--color-primary-300),var(--color-primary-500))] flex items-center justify-center">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
          </div>
        </div>

        <p className="mt-3 font-bold text-gray-900 text-base">{user?.name ?? "-"}</p>
        <p className="text-xs text-gray-400 mt-0.5">{user?.email ?? "-"}</p>

        <div className="flex gap-1.5 mt-2.5">
          {user?.industry && <span className="text-xs bg-primary-100 text-primary-600 font-semibold px-2.5 py-1 rounded-full">{user.industry}</span>}
          {user?.region && <span className="text-xs bg-gray-100 text-gray-500 font-semibold px-2.5 py-1 rounded-full">{user.region}</span>}
        </div>
      </div>

      <div className="w-full h-px bg-gray-100 mb-3" />

      {/* Navigation */}
      <nav className="flex flex-col gap-0.5 flex-1">
        {NAV_ITEMS.map((item, idx) => {
          const isActive = pathname === item.to;
          const prevGroup = idx > 0 ? NAV_ITEMS[idx - 1].group : item.group;
          const showDivider = idx > 0 && item.group !== prevGroup;

          return (
            <div key={item.to}>
              {showDivider && <div className="w-full h-px bg-gray-100 my-2" />}
              <Link
                to={item.to}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                  isActive
                    ? "bg-primary-100 text-primary-600"
                    : "text-gray-600 hover:bg-gray-50"
                }`}
              >
                <span className={isActive ? "text-primary-500" : "text-gray-400"}>{item.icon}</span>
                {item.label}
              </Link>
            </div>
          );
        })}

        <div className="flex-1" />
        <div className="w-full h-px bg-gray-100 my-2" />

        <button onClick={handleLogout} className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-red-400 hover:bg-red-50 transition-colors w-full mb-4">
          <LogoutIcon />
          로그아웃
        </button>
      </nav>
    </aside>
  );
}
