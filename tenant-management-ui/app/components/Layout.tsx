import { Link, useLocation } from 'react-router';
import type { ReactNode } from 'react';
import { useAuth } from '../contexts/AuthContext';

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  const { logout } = useAuth();
  const location = useLocation();

  const isActive = (path: string) => location.pathname === path;

  const navLinkClass = (path: string) =>
    `px-3 py-2 rounded-md text-sm font-medium ${
      isActive(path)
        ? 'bg-indigo-700 text-white'
        : 'text-indigo-100 hover:bg-indigo-600 hover:text-white'
    }`;

  return (
    <div className="min-h-screen bg-gray-100">
      <nav className="bg-indigo-600">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center">
              <div className="flex-shrink-0">
                <h1 className="text-white font-bold text-xl">MyBaaS</h1>
              </div>
              <div className="ml-10 flex items-baseline space-x-4">
                <Link to="/applications" className={navLinkClass('/applications')}>
                  Applications
                </Link>
                <Link to="/schemas" className={navLinkClass('/schemas')}>
                  Schemas
                </Link>
                <Link to="/data" className={navLinkClass('/data')}>
                  Data
                </Link>
                <Link to="/settings" className={navLinkClass('/settings')}>
                  Settings
                </Link>
              </div>
            </div>
            <div>
              <button
                onClick={logout}
                className="text-indigo-100 hover:bg-indigo-600 hover:text-white px-3 py-2 rounded-md text-sm font-medium"
              >
                Logout
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        {children}
      </main>
    </div>
  );
}
