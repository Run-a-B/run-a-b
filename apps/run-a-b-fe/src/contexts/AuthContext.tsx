import { createContext, useContext, useEffect, useState } from "react";

interface User {
  name: string;
  email: string;
  age?: number;
  industry: string;
  region: string;
  bizStatus: string;
  revenue?: string;
  employees?: string;
}

interface AuthContextType {
  user: User | null;
  login: (user: User) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

function loadStoredUser(): User | null {
  const stored = localStorage.getItem("user");
  if (!stored) return null;

  try {
    return JSON.parse(stored) as User;
  } catch {
    return null;
  }
}

function removeMalformedStoredUser() {
  const stored = localStorage.getItem("user");
  if (!stored) return;

  try {
    JSON.parse(stored);
  } catch {
    localStorage.removeItem("user");
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(loadStoredUser);

  // localStorage 정리는 외부 시스템 동기화이므로 렌더 이후에 수행한다.
  useEffect(removeMalformedStoredUser, []);

  return (
    <AuthContext.Provider value={{
      user,
      login: (u) => {
        setUser(u);
        localStorage.setItem("user", JSON.stringify(u));
      },
      logout: () => {
        setUser(null);
        localStorage.removeItem("user");
        localStorage.removeItem("access_token");
      },
    }}>
      {children}
    </AuthContext.Provider>
  );
}

// AuthProvider와 함께 두어 기존 import 경로를 유지한다.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
