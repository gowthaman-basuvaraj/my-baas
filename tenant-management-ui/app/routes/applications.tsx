import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import type { Route } from "./+types/applications";
import { Layout } from "../components/Layout";
import { useAuth } from "../contexts/AuthContext";
import { useApp } from "../contexts/AppContext";
import { applicationApi } from "../lib/api";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Applications - MyBaaS" }];
}

interface Application {
  id: string;
  applicationName: string;
  description: string;
  isActive: boolean;
}

interface PageState {
  showModal: boolean;
  editingApp: Application | null;
  formData: {
    applicationName: string;
    description: string;
    isActive: boolean;
  };
}

export default function Applications() {
  const { isAuthenticated, authToken, tenantHost } = useAuth();
  const { applications, loading, error, reloadApplications } = useApp();
  const navigate = useNavigate();

  const [state, setState] = useState<PageState>({
    showModal: false,
    editingApp: null,
    formData: {
      applicationName: "",
      description: "",
      isActive: true,
    },
  });

  useEffect(() => {
    if (!isAuthenticated) {
      navigate("/");
    }
  }, [isAuthenticated, navigate]);

  const handleCreate = () => {
    setState({
      showModal: true,
      editingApp: null,
      formData: { applicationName: "", description: "", isActive: true },
    });
  };

  const handleEdit = (app: Application) => {
    setState({
      showModal: true,
      editingApp: app,
      formData: {
        applicationName: app.applicationName,
        description: app.description,
        isActive: app.isActive,
      },
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (state.editingApp) {
        await applicationApi.update(
          state.editingApp.id,
          { description: state.formData.description, isActive: state.formData.isActive },
          authToken!,
          tenantHost!
        );
      } else {
        await applicationApi.create(state.formData, authToken!, tenantHost!);
      }
      setState(prev => ({ ...prev, showModal: false }));
      await reloadApplications();
    } catch (err: any) {
      alert(err.message);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Are you sure you want to delete this application?")) return;
    try {
      await applicationApi.delete(id, authToken!, tenantHost!);
      await reloadApplications();
    } catch (err: any) {
      alert(err.message);
    }
  };

  const updateFormData = (field: keyof PageState['formData'], value: any) => {
    setState(prev => ({
      ...prev,
      formData: { ...prev.formData, [field]: value },
    }));
  };

  if (!isAuthenticated) return null;

  return (
    <Layout>
      <div className="px-4 sm:px-6 lg:px-8">
        <div className="sm:flex sm:items-center">
          <div className="sm:flex-auto">
            <h1 className="text-3xl font-semibold text-gray-900">Applications</h1>
            <p className="mt-2 text-sm text-gray-700">
              Manage your applications and their configurations
            </p>
          </div>
          <div className="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
            <button
              onClick={handleCreate}
              className="block rounded-md bg-indigo-600 px-3 py-2 text-center text-sm font-semibold text-white hover:bg-indigo-500"
            >
              Create Application
            </button>
          </div>
        </div>

        {loading && <div className="mt-8 text-center">Loading...</div>}
        {error && (
          <div className="mt-8 bg-red-50 border border-red-200 text-red-800 rounded-md p-4">
            {error}
          </div>
        )}

        {!loading && !error && (
          <div className="mt-8 flow-root">
            <div className="-mx-4 -my-2 overflow-x-auto sm:-mx-6 lg:-mx-8">
              <div className="inline-block min-w-full py-2 align-middle sm:px-6 lg:px-8">
                <div className="overflow-hidden shadow ring-1 ring-black ring-opacity-5 sm:rounded-lg">
                  <table className="min-w-full divide-y divide-gray-300">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-6">
                          Name
                        </th>
                        <th className="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">
                          Description
                        </th>
                        <th className="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">
                          Status
                        </th>
                        <th className="relative py-3.5 pl-3 pr-4 sm:pr-6">
                          <span className="sr-only">Actions</span>
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200 bg-white">
                      {applications.map((app) => (
                        <tr key={app.id}>
                          <td className="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6">
                            {app.applicationName}
                          </td>
                          <td className="px-3 py-4 text-sm text-gray-500">
                            {app.description}
                          </td>
                          <td className="whitespace-nowrap px-3 py-4 text-sm">
                            <span
                              className={`inline-flex rounded-full px-2 text-xs font-semibold leading-5 ${
                                app.isActive
                                  ? "bg-green-100 text-green-800"
                                  : "bg-red-100 text-red-800"
                              }`}
                            >
                              {app.isActive ? "Active" : "Inactive"}
                            </span>
                          </td>
                          <td className="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                            <button
                              onClick={() => handleEdit(app)}
                              className="text-indigo-600 hover:text-indigo-900 mr-4"
                            >
                              Edit
                            </button>
                            <button
                              onClick={() => handleDelete(app.id)}
                              className="text-red-600 hover:text-red-900"
                            >
                              Delete
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        )}

        {state.showModal && (
          <div className="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center p-4">
            <div className="bg-white rounded-lg max-w-md w-full p-6">
              <h2 className="text-lg font-semibold mb-4">
                {state.editingApp ? "Edit Application" : "Create Application"}
              </h2>
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Application Name
                  </label>
                  <input
                    type="text"
                    required
                    disabled={!!state.editingApp}
                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border"
                    value={state.formData.applicationName}
                    onChange={(e) => updateFormData('applicationName', e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Description
                  </label>
                  <textarea
                    required
                    rows={3}
                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border"
                    value={state.formData.description}
                    onChange={(e) => updateFormData('description', e.target.value)}
                  />
                </div>
                <div className="flex items-center">
                  <input
                    type="checkbox"
                    className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                    checked={state.formData.isActive}
                    onChange={(e) => updateFormData('isActive', e.target.checked)}
                  />
                  <label className="ml-2 block text-sm text-gray-900">
                    Active
                  </label>
                </div>
                <div className="flex justify-end space-x-3 mt-6">
                  <button
                    type="button"
                    onClick={() => setState(prev => ({ ...prev, showModal: false }))}
                    className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500"
                  >
                    {state.editingApp ? "Update" : "Create"}
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
