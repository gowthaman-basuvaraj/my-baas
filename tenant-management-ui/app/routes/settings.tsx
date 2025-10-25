import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import type { Route } from "./+types/settings";
import { Layout } from "../components/Layout";
import { useAuth } from "../contexts/AuthContext";
import { settingsApi } from "../lib/api";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Settings - MyBaaS" }];
}

interface TenantSettings {
  id: string;
  name: string;
  allowedIps: string[];
  settings: any;
  config: any;
}

interface PageState {
  loading: boolean;
  error: string | null;
  tenantSettings: TenantSettings | null;
  isEditing: boolean;
  formData: {
    name: string;
    allowedIps: string;
    settings: string;
    config: string;
  };
}

export default function Settings() {
  const { isAuthenticated, authToken } = useAuth();
  const navigate = useNavigate();

  const [state, setState] = useState<PageState>({
    loading: true,
    error: null,
    tenantSettings: null,
    isEditing: false,
    formData: {
      name: "",
      allowedIps: "",
      settings: "",
      config: "",
    },
  });

  useEffect(() => {
    if (!isAuthenticated) {
      navigate("/");
      return;
    }
    loadSettings();
  }, [isAuthenticated, navigate]);

  const loadSettings = async () => {
    if (!authToken) return;

    try {
      setState(prev => ({ ...prev, loading: true, error: null }));
      const data = await settingsApi.get(authToken);
      setState(prev => ({
        ...prev,
        loading: false,
        tenantSettings: data,
        formData: {
          name: data.name || "",
          allowedIps: (data.allowedIps || []).join("\n"),
          settings: JSON.stringify(data.settings || {}, null, 2),
          config: JSON.stringify(data.config || {}, null, 2),
        },
      }));
    } catch (err: any) {
      setState(prev => ({ ...prev, error: err.message, loading: false }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!authToken) return;

    try {
      const payload = {
        name: state.formData.name,
        allowedIps: state.formData.allowedIps
          .split("\n")
          .map((ip) => ip.trim())
          .filter((ip) => ip),
        settings: JSON.parse(state.formData.settings),
        config: JSON.parse(state.formData.config),
      };

      await settingsApi.update(payload, authToken);
      setState(prev => ({ ...prev, isEditing: false }));
      await loadSettings();
    } catch (err: any) {
      alert(err.message);
    }
  };

  const updateFormData = (field: keyof PageState['formData'], value: string) => {
    setState(prev => ({
      ...prev,
      formData: { ...prev.formData, [field]: value },
    }));
  };

  const handleCancelEdit = () => {
    if (!state.tenantSettings) return;

    setState(prev => ({
      ...prev,
      isEditing: false,
      formData: {
        name: state.tenantSettings!.name || "",
        allowedIps: (state.tenantSettings!.allowedIps || []).join("\n"),
        settings: JSON.stringify(state.tenantSettings!.settings || {}, null, 2),
        config: JSON.stringify(state.tenantSettings!.config || {}, null, 2),
      },
    }));
  };

  if (!isAuthenticated) return null;

  return (
    <Layout>
      <div className="px-4 sm:px-6 lg:px-8">
        <div className="sm:flex sm:items-center">
          <div className="sm:flex-auto">
            <h1 className="text-3xl font-semibold text-gray-900">Tenant Settings</h1>
            <p className="mt-2 text-sm text-gray-700">
              Manage your tenant configuration and settings
            </p>
          </div>
          {!state.isEditing && (
            <div className="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
              <button
                onClick={() => setState(prev => ({ ...prev, isEditing: true }))}
                className="block rounded-md bg-indigo-600 px-3 py-2 text-center text-sm font-semibold text-white hover:bg-indigo-500"
              >
                Edit Settings
              </button>
            </div>
          )}
        </div>

        {state.loading && <div className="mt-8 text-center">Loading...</div>}
        {state.error && (
          <div className="mt-8 bg-red-50 border border-red-200 text-red-800 rounded-md p-4">
            {state.error}
          </div>
        )}

        {!state.loading && !state.error && state.tenantSettings && (
          <div className="mt-8">
            {state.isEditing ? (
              <form onSubmit={handleSubmit} className="space-y-6 bg-white shadow sm:rounded-lg p-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Tenant Name
                  </label>
                  <input
                    type="text"
                    required
                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border"
                    value={state.formData.name}
                    onChange={(e) => updateFormData('name', e.target.value)}
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Allowed IPs (one per line)
                  </label>
                  <textarea
                    rows={4}
                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border font-mono text-xs"
                    value={state.formData.allowedIps}
                    onChange={(e) => updateFormData('allowedIps', e.target.value)}
                    placeholder="192.168.1.0/24&#10;10.0.0.0/8"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Settings (JSON)
                  </label>
                  <textarea
                    rows={6}
                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border font-mono text-xs"
                    value={state.formData.settings}
                    onChange={(e) => updateFormData('settings', e.target.value)}
                    placeholder="{}"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Config (JSON)
                  </label>
                  <textarea
                    rows={6}
                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border font-mono text-xs"
                    value={state.formData.config}
                    onChange={(e) => updateFormData('config', e.target.value)}
                    placeholder='{"jwksUri": "..."}'
                  />
                </div>

                <div className="flex justify-end space-x-3">
                  <button
                    type="button"
                    onClick={handleCancelEdit}
                    className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500"
                  >
                    Save Changes
                  </button>
                </div>
              </form>
            ) : (
              <div className="bg-white shadow sm:rounded-lg">
                <div className="px-4 py-5 sm:p-6">
                  <dl className="grid grid-cols-1 gap-x-4 gap-y-8 sm:grid-cols-2">
                    <div className="sm:col-span-1">
                      <dt className="text-sm font-medium text-gray-500">Tenant ID</dt>
                      <dd className="mt-1 text-sm text-gray-900">{state.tenantSettings.id}</dd>
                    </div>
                    <div className="sm:col-span-1">
                      <dt className="text-sm font-medium text-gray-500">Tenant Name</dt>
                      <dd className="mt-1 text-sm text-gray-900">{state.tenantSettings.name}</dd>
                    </div>
                    <div className="sm:col-span-2">
                      <dt className="text-sm font-medium text-gray-500">Allowed IPs</dt>
                      <dd className="mt-1 text-sm text-gray-900">
                        {state.tenantSettings.allowedIps?.length > 0 ? (
                          <ul className="list-disc list-inside">
                            {state.tenantSettings.allowedIps.map((ip, idx) => (
                              <li key={idx}>{ip}</li>
                            ))}
                          </ul>
                        ) : (
                          <span className="text-gray-400">No IP restrictions</span>
                        )}
                      </dd>
                    </div>
                    <div className="sm:col-span-2">
                      <dt className="text-sm font-medium text-gray-500">Settings</dt>
                      <dd className="mt-1 text-sm text-gray-900">
                        <pre className="bg-gray-50 p-4 rounded-md overflow-x-auto">
                          <code>{JSON.stringify(state.tenantSettings.settings, null, 2)}</code>
                        </pre>
                      </dd>
                    </div>
                    <div className="sm:col-span-2">
                      <dt className="text-sm font-medium text-gray-500">Config</dt>
                      <dd className="mt-1 text-sm text-gray-900">
                        <pre className="bg-gray-50 p-4 rounded-md overflow-x-auto">
                          <code>{JSON.stringify(state.tenantSettings.config, null, 2)}</code>
                        </pre>
                      </dd>
                    </div>
                  </dl>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </Layout>
  );
}
