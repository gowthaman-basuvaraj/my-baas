import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';

interface AuthContextType {
  authToken: string | null;
  tenantHost: string | null;
  setAuthToken: (token: string) => void;
  setTenantHost: (host: string) => void;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authToken, setAuthTokenState] = useState<string | null>(null);
  const [tenantHost, setTenantHostState] = useState<string | null>(null);

  useEffect(() => {
    // Load from sessionStorage on mount
    const token = sessionStorage.getItem('authToken');
    const host = sessionStorage.getItem('tenantHost');
    if (token) setAuthTokenState(token);
    if (host) setTenantHostState(host);
  }, []);

  const setAuthToken = (token: string) => {
    sessionStorage.setItem('authToken', token);
    setAuthTokenState(token);
  };

  const setTenantHost = (host: string) => {
    sessionStorage.setItem('tenantHost', host);
    setTenantHostState(host);
  };

  const logout = () => {
    sessionStorage.removeItem('authToken');
    sessionStorage.removeItem('tenantHost');
    setAuthTokenState(null);
    setTenantHostState(null);
  };

  return (
    <AuthContext.Provider
      value={{
        authToken,
        tenantHost,
        setAuthToken,
        setTenantHost,
        logout,
        isAuthenticated: !!authToken && !!tenantHost,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
