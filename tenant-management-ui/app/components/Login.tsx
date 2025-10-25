import { useState } from 'react';
import { useAuth } from '../contexts/AuthContext';

export function Login() {
  const [authToken, setAuthToken] = useState('');
  const [tenantHost, setTenantHost] = useState('');
  const { setAuthToken: saveAuthToken, setTenantHost: saveTenantHost } = useAuth();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (authToken && tenantHost) {
      saveAuthToken(authToken);
      saveTenantHost(tenantHost);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-lg shadow-md">
        <div>
          <h2 className="text-center text-3xl font-extrabold text-gray-900">
            MyBaaS Tenant Management
          </h2>
          <p className="mt-2 text-center text-sm text-gray-600">
            Please enter your credentials to continue
          </p>
        </div>
        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          <div className="space-y-4">
            <div>
              <label htmlFor="authToken" className="block text-sm font-medium text-gray-700">
                Authentication Token
              </label>
              <input
                id="authToken"
                name="authToken"
                type="text"
                required
                className="mt-1 appearance-none relative block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                placeholder="Enter your auth token"
                value={authToken}
                onChange={(e) => setAuthToken(e.target.value)}
              />
            </div>
            <div>
              <label htmlFor="tenantHost" className="block text-sm font-medium text-gray-700">
                Tenant Host
              </label>
              <input
                id="tenantHost"
                name="tenantHost"
                type="text"
                required
                className="mt-1 appearance-none relative block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                placeholder="e.g., tenant1.mybaas.com"
                value={tenantHost}
                onChange={(e) => setTenantHost(e.target.value)}
              />
            </div>
          </div>

          <div>
            <button
              type="submit"
              className="group relative w-full flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
            >
              Sign in
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
