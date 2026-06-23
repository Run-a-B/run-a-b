import { createContext, useContext, useState, useEffect } from "react";

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

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);

  // 새로고침 시 localStorage에서 user 복원
  useEffect(() => {
    const stored = localStorage.getItem("user");
    if (stored) {
      try {
        setUser(JSON.parse(stored));
      } catch {
        localStorage.removeItem("user");
      }
    }
  }, []);

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

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}