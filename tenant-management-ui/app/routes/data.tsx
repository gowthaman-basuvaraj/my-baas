import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import type { Route } from "./+types/data";
import { Layout } from "../components/Layout";
import { useAuth } from "../contexts/AuthContext";
import { useApp } from "../contexts/AppContext";
import { dataApi } from "../lib/api";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Data Management - MyBaaS" }];
}

interface SearchFilter {
  jsonPath: string;
  operator: string;
  value: string;
}

interface PageState {
  selectedEntity: string;
  selectedVersion: string;
  dataList: any[];
  loading: boolean;
  error: string | null;
  showCreateModal: boolean;
  showSearchModal: boolean;
  createData: string;
  searchFilters: SearchFilter[];
}

export default function Data() {
  const { isAuthenticated, authToken, tenantHost } = useAuth();
  const { applications, selectedApp, schemas, setSelectedApp } = useApp();
  const navigate = useNavigate();

  const [state, setState] = useState<PageState>({
    selectedEntity: "",
    selectedVersion: "",
    dataList: [],
    loading: false,
    error: null,
    showCreateModal: false,
    showSearchModal: false,
    createData: "",
    searchFilters: [{ jsonPath: "", operator: "EQ", value: "" }],
  });

  useEffect(() => {
    if (!isAuthenticated) {
      navigate("/");
    }
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    if (selectedApp && state.selectedEntity) {
      loadData();
    }
  }, [selectedApp, state.selectedEntity]);

  // Update selected version when selectedEntity changes
  useEffect(() => {
    if (state.selectedEntity) {
      const schema = schemas.find((s) => s.entityName === state.selectedEntity);
      if (schema) {
        setState(prev => ({ ...prev, selectedVersion: schema.versionName }));
      }
    }
  }, [state.selectedEntity, schemas]);

  const loadData = async () => {
    if (!selectedApp || !state.selectedEntity || !authToken || !tenantHost) return;

    try {
      setState(prev => ({ ...prev, loading: true, error: null }));
      const data = await dataApi.list(selectedApp, state.selectedEntity, authToken, tenantHost);
      setState(prev => ({
        ...prev,
        dataList: Array.isArray(data) ? data : data.list || data.content || [],
        loading: false,
      }));
    } catch (err: any) {
      setState(prev => ({ ...prev, error: err.message, loading: false }));
    }
  };

  const handleCreateData = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedApp || !state.selectedEntity || !state.selectedVersion) return;

    try {
      const payload = JSON.parse(state.createData);
      await dataApi.create(
        selectedApp,
        state.selectedEntity,
        state.selectedVersion,
        payload,
        authToken!,
        tenantHost!
      );
      setState(prev => ({ ...prev, showCreateModal: false, createData: "" }));
      await loadData();
    } catch (err: any) {
      alert(err.message);
    }
  };

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedApp || !state.selectedEntity) return;

    try {
      setState(prev => ({ ...prev, loading: true }));
      const filters = state.searchFilters
        .filter((f) => f.jsonPath && f.value)
        .map((f) => ({
          jsonPath: f.jsonPath,
          operator: f.operator,
          value: f.value,
        }));

      const searchCriteria = {
        filters,
        pageNo: 1,
        pageSize: 100,
      };

      const data = await dataApi.search(
        selectedApp,
        state.selectedEntity,
        searchCriteria,
        authToken!,
        tenantHost!
      );
      setState(prev => ({
        ...prev,
        dataList: Array.isArray(data) ? data : data.list || data.content || [],
        showSearchModal: false,
        loading: false,
        error: null,
      }));
    } catch (err: any) {
      setState(prev => ({ ...prev, error: err.message, loading: false }));
    }
  };

  const handleDelete = async (uniqueId: string) => {
    if (!selectedApp || !state.selectedEntity || !state.selectedVersion) return;
    if (!confirm("Are you sure you want to delete this data?")) return;

    try {
      await dataApi.delete(
        selectedApp,
        state.selectedEntity,
        state.selectedVersion,
        uniqueId,
        authToken!,
        tenantHost!
      );
      await loadData();
    } catch (err: any) {
      alert(err.message);
    }
  };

  const addSearchFilter = () => {
    setState(prev => ({
      ...prev,
      searchFilters: [...prev.searchFilters, { jsonPath: "", operator: "EQ", value: "" }],
    }));
  };

  const removeSearchFilter = (index: number) => {
    setState(prev => ({
      ...prev,
      searchFilters: prev.searchFilters.filter((_, i) => i !== index),
    }));
  };

  const updateSearchFilter = (index: number, field: keyof SearchFilter, value: string) => {
    setState(prev => ({
      ...prev,
      searchFilters: prev.searchFilters.map((filter, i) =>
        i === index ? { ...filter, [field]: value } : filter
      ),
    }));
  };

  const handleAppChange = (appName: string) => {
    setSelectedApp(appName);
    setState(prev => ({ ...prev, selectedEntity: "", dataList: [] }));
  };

  const handleEntityChange = (entityName: string) => {
    setState(prev => ({ ...prev, selectedEntity: entityName }));
  };

  if (!isAuthenticated) return null;

  return (
    <Layout>
      <div className="px-4 sm:px-6 lg:px-8">
        <div className="sm:flex sm:items-center">
          <div className="sm:flex-auto">
            <h1 className="text-3xl font-semibold text-gray-900">Data Management</h1>
            <p className="mt-2 text-sm text-gray-700">
              Create, search, and manage your application data
            </p>
          </div>
          <div className="mt-4 sm:mt-0 sm:ml-16 flex items-center space-x-2">
            <select
              className="rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm px-3 py-2 border"
              value={selectedApp || ""}
              onChange={(e) => handleAppChange(e.target.value)}
            >
              <option value="">Select App</option>
              {applications.map((app) => (
                <option key={app.id} value={app.applicationName}>
                  {app.applicationName}
                </option>
              ))}
            </select>
            <select
              className="rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm px-3 py-2 border"
              value={state.selectedEntity}
              onChange={(e) => handleEntityChange(e.target.value)}
              disabled={!selectedApp}
            >
              <option value="">Select Entity</option>
              {schemas.map((schema) => (
                <option key={schema.id} value={schema.entityName}>
                  {schema.entityName} ({schema.versionName})
                </option>
              ))}
            </select>
            <button
              onClick={() => setState(prev => ({ ...prev, showSearchModal: true }))}
              disabled={!state.selectedEntity}
              className="rounded-md bg-gray-600 px-3 py-2 text-sm font-semibold text-white hover:bg-gray-500 disabled:bg-gray-400"
            >
              Search
            </button>
            <button
              onClick={() => setState(prev => ({ ...prev, showCreateModal: true }))}
              disabled={!state.selectedEntity}
              className="rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:bg-gray-400"
            >
              Create Data
            </button>
          </div>
        </div>

        {state.loading && <div className="mt-8 text-center">Loading...</div>}
        {state.error && (
          <div className="mt-8 bg-red-50 border border-red-200 text-red-800 rounded-md p-4">
            {state.error}
          </div>
        )}

        {!state.loading && !state.error && state.selectedEntity && (
          <div className="mt-8">
            <div className="bg-white shadow overflow-hidden sm:rounded-lg">
              <div className="px-4 py-5 sm:p-6">
                <h3 className="text-lg leading-6 font-medium text-gray-900 mb-4">
                  Data Records ({state.dataList.length})
                </h3>
                {state.dataList.length === 0 ? (
                  <p className="text-gray-500 text-center py-8">No data found</p>
                ) : (
                  <div className="space-y-4">
                    {state.dataList.map((item, idx) => (
                      <div
                        key={idx}
                        className="border border-gray-200 rounded-lg p-4 hover:bg-gray-50"
                      >
                        <div className="flex justify-between items-start">
                          <div className="flex-1">
                            {item.uniqueIdentifier && (
                              <div className="text-sm font-medium text-gray-900 mb-2">
                                ID: {item.uniqueIdentifier}
                              </div>
                            )}
                            <pre className="text-xs bg-gray-50 p-3 rounded overflow-x-auto">
                              <code>{JSON.stringify(item, null, 2)}</code>
                            </pre>
                          </div>
                          {item.uniqueIdentifier && (
                            <button
                              onClick={() => handleDelete(item.uniqueIdentifier)}
                              className="ml-4 text-red-600 hover:text-red-900 text-sm"
                            >
                              Delete
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {state.showCreateModal && (
          <div className="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center p-4">
            <div className="bg-white rounded-lg max-w-2xl w-full p-6">
              <h2 className="text-lg font-semibold mb-4">Create Data</h2>
              <form onSubmit={handleCreateData} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Data (JSON)
                  </label>
                  <textarea
                    required
                    rows={15}
                    className="block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border font-mono text-xs"
                    value={state.createData}
                    onChange={(e) =>
                      setState(prev => ({ ...prev, createData: e.target.value }))
                    }
                    placeholder='{"name": "John Doe", "email": "john@example.com"}'
                  />
                </div>
                <div className="flex justify-end space-x-3">
                  <button
                    type="button"
                    onClick={() =>
                      setState(prev => ({ ...prev, showCreateModal: false, createData: "" }))
                    }
                    className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500"
                  >
                    Create
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {state.showSearchModal && (
          <div className="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center p-4 overflow-y-auto">
            <div className="bg-white rounded-lg max-w-3xl w-full p-6 my-8">
              <h2 className="text-lg font-semibold mb-4">Search Data</h2>
              <form onSubmit={handleSearch} className="space-y-4">
                {state.searchFilters.map((filter, idx) => (
                  <div key={idx} className="grid grid-cols-12 gap-2">
                    <div className="col-span-4">
                      <input
                        type="text"
                        placeholder="JSON Path (e.g., name)"
                        className="block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm px-3 py-2 border"
                        value={filter.jsonPath}
                        onChange={(e) =>
                          updateSearchFilter(idx, "jsonPath", e.target.value)
                        }
                      />
                    </div>
                    <div className="col-span-3">
                      <select
                        className="block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm px-3 py-2 border"
                        value={filter.operator}
                        onChange={(e) =>
                          updateSearchFilter(idx, "operator", e.target.value)
                        }
                      >
                        <option value="EQ">Equals</option>
                        <option value="GT">Greater Than</option>
                        <option value="GE">Greater or Equal</option>
                        <option value="LT">Less Than</option>
                        <option value="LE">Less or Equal</option>
                        <option value="CONTAINS">Contains</option>
                        <option value="ARRAY_CONTAINS">Array Contains</option>
                      </select>
                    </div>
                    <div className="col-span-4">
                      <input
                        type="text"
                        placeholder="Value"
                        className="block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm px-3 py-2 border"
                        value={filter.value}
                        onChange={(e) => updateSearchFilter(idx, "value", e.target.value)}
                      />
                    </div>
                    <div className="col-span-1">
                      <button
                        type="button"
                        onClick={() => removeSearchFilter(idx)}
                        className="text-red-600 hover:text-red-900 text-sm px-2 py-2"
                      >
                        X
                      </button>
                    </div>
                  </div>
                ))}
                <button
                  type="button"
                  onClick={addSearchFilter}
                  className="text-indigo-600 hover:text-indigo-900 text-sm font-medium"
                >
                  + Add Filter
                </button>
                <div className="flex justify-end space-x-3 mt-6">
                  <button
                    type="button"
                    onClick={() =>
                      setState(prev => ({
                        ...prev,
                        showSearchModal: false,
                        searchFilters: [{ jsonPath: "", operator: "EQ", value: "" }],
                      }))
                    }
                    className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500"
                  >
                    Search
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
}
