import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import type { Route } from "./+types/schemas";
import { Layout } from "../components/Layout";
import { useAuth } from "../contexts/AuthContext";
import { useApp } from "../contexts/AppContext";
import { schemaApi } from "../lib/api";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Schemas - MyBaaS" }];
}

interface Schema {
  id: string;
  entityName: string;
  versionName: string;
  jsonSchema: any;
  uniqueIdentifierFormatter: string;
  indexedJsonPaths: string[];
  lifecycleScripts?: any;
  isValidationEnabled: boolean;
}

interface PageState {
  showModal: boolean;
  editingSchema: Schema | null;
  formData: {
    entityName: string;
    versionName: string;
    jsonSchema: string;
    uniqueIdentifierFormatter: string;
    indexedJsonPaths: string;
    isValidationEnabled: boolean;
  };
}

export default function Schemas() {
  const { isAuthenticated, authToken, tenantHost } = useAuth();
  const { applications, selectedApp, schemas, loading, error, setSelectedApp, reloadSchemas } = useApp();
  const navigate = useNavigate();

  const [state, setState] = useState<PageState>({
    showModal: false,
    editingSchema: null,
    formData: {
      entityName: "",
      versionName: "",
      jsonSchema: "",
      uniqueIdentifierFormatter: "",
      indexedJsonPaths: "",
      isValidationEnabled: true,
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
      editingSchema: null,
      formData: {
        entityName: "",
        versionName: "",
        jsonSchema: "",
        uniqueIdentifierFormatter: "",
        indexedJsonPaths: "",
        isValidationEnabled: true,
      },
    });
  };

  const handleEdit = (schema: Schema) => {
    setState({
      showModal: true,
      editingSchema: schema,
      formData: {
        entityName: schema.entityName,
        versionName: schema.versionName,
        jsonSchema: JSON.stringify(schema.jsonSchema, null, 2),
        uniqueIdentifierFormatter: schema.uniqueIdentifierFormatter,
        indexedJsonPaths: schema.indexedJsonPaths.join("\n"),
        isValidationEnabled: schema.isValidationEnabled,
      },
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedApp) return;

    try {
      const payload = {
        entityName: state.formData.entityName,
        versionName: state.formData.versionName,
        jsonSchema: JSON.parse(state.formData.jsonSchema),
        uniqueIdentifierFormatter: state.formData.uniqueIdentifierFormatter,
        indexedJsonPaths: state.formData.indexedJsonPaths
          .split("\n")
          .map((p) => p.trim())
          .filter((p) => p),
        isValidationEnabled: state.formData.isValidationEnabled,
      };

      if (state.editingSchema) {
        await schemaApi.update(selectedApp, state.editingSchema.id, payload, authToken!, tenantHost!);
      } else {
        await schemaApi.create(selectedApp, payload, authToken!, tenantHost!);
      }
      setState(prev => ({ ...prev, showModal: false }));
      await reloadSchemas();
    } catch (err: any) {
      alert(err.message);
    }
  };

  const handleDelete = async (id: string) => {
    if (!selectedApp) return;
    if (!confirm("Are you sure you want to delete this schema?")) return;
    const dropTable = confirm("Do you also want to drop the associated table?");
    try {
      await schemaApi.delete(selectedApp, id, dropTable, authToken!, tenantHost!);
      await reloadSchemas();
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
            <h1 className="text-3xl font-semibold text-gray-900">Schemas</h1>
            <p className="mt-2 text-sm text-gray-700">
              Manage schemas for your applications
            </p>
          </div>
          <div className="mt-4 sm:mt-0 sm:ml-16 sm:flex-none space-x-2">
            <select
              className="rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border"
              value={selectedApp || ""}
              onChange={(e) => setSelectedApp(e.target.value)}
            >
              <option value="">Select Application</option>
              {applications.map((app) => (
                <option key={app.id} value={app.applicationName}>
                  {app.applicationName}
                </option>
              ))}
            </select>
            <button
              onClick={handleCreate}
              disabled={!selectedApp}
              className="rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:bg-gray-400"
            >
              Create Schema
            </button>
          </div>
        </div>

        {loading && <div className="mt-8 text-center">Loading...</div>}
        {error && (
          <div className="mt-8 bg-red-50 border border-red-200 text-red-800 rounded-md p-4">
            {error}
          </div>
        )}

        {!loading && !error && selectedApp && (
          <div className="mt-8 flow-root">
            <div className="-mx-4 -my-2 overflow-x-auto sm:-mx-6 lg:-mx-8">
              <div className="inline-block min-w-full py-2 align-middle sm:px-6 lg:px-8">
                <div className="overflow-hidden shadow ring-1 ring-black ring-opacity-5 sm:rounded-lg">
                  <table className="min-w-full divide-y divide-gray-300">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-6">
                          Entity Name
                        </th>
                        <th className="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">
                          Version
                        </th>
                        <th className="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">
                          Unique ID Formatter
                        </th>
                        <th className="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">
                          Validation
                        </th>
                        <th className="relative py-3.5 pl-3 pr-4 sm:pr-6">
                          <span className="sr-only">Actions</span>
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200 bg-white">
                      {schemas.map((schema) => (
                        <tr key={schema.id}>
                          <td className="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6">
                            {schema.entityName}
                          </td>
                          <td className="whitespace-nowrap px-3 py-4 text-sm text-gray-500">
                            {schema.versionName}
                          </td>
                          <td className="px-3 py-4 text-sm text-gray-500">
                            {schema.uniqueIdentifierFormatter}
                          </td>
                          <td className="whitespace-nowrap px-3 py-4 text-sm">
                            <span
                              className={`inline-flex rounded-full px-2 text-xs font-semibold leading-5 ${
                                schema.isValidationEnabled
                                  ? "bg-green-100 text-green-800"
                                  : "bg-gray-100 text-gray-800"
                              }`}
                            >
                              {schema.isValidationEnabled ? "Enabled" : "Disabled"}
                            </span>
                          </td>
                          <td className="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                            <button
                              onClick={() => handleEdit(schema)}
                              className="text-indigo-600 hover:text-indigo-900 mr-4"
                            >
                              Edit
                            </button>
                            <button
                              onClick={() => handleDelete(schema.id)}
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
          <div className="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center p-4 overflow-y-auto">
            <div className="bg-white rounded-lg max-w-2xl w-full p-6 my-8">
              <h2 className="text-lg font-semibold mb-4">
                {state.editingSchema ? "Edit Schema" : "Create Schema"}
              </h2>
              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700">
                      Entity Name
                    </label>
                    <input
                      type="text"
                      required
                      className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border"
                      value={state.formData.entityName}
                      onChange={(e) => updateFormData('entityName', e.target.value)}
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700">
                      Version Name
                    </label>
                    <input
                      type="text"
                      required
                      className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border"
                      value={state.formData.versionName}
                      onChange={(e) => updateFormData('versionName', e.target.value)}
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    JSON Schema (Valid JSON)
                  </label>
                  <textarea
                    required
                    rows={10}
                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border font-mono text-xs"
                    value={state.formData.jsonSchema}
                    onChange={(e) => updateFormData('jsonSchema', e.target.value)}
                    placeholder='{"type": "object", "properties": {...}}'
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Unique Identifier Formatter
                  </label>
                  <input
                    type="text"
                    required
                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border"
                    value={state.formData.uniqueIdentifierFormatter}
                    onChange={(e) => updateFormData('uniqueIdentifierFormatter', e.target.value)}
                    placeholder="{email}-{timestamp}"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Indexed JSON Paths (one per line)
                  </label>
                  <textarea
                    rows={4}
                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm px-3 py-2 border font-mono text-xs"
                    value={state.formData.indexedJsonPaths}
                    onChange={(e) => updateFormData('indexedJsonPaths', e.target.value)}
                    placeholder="name&#10;email&#10;address.city"
                  />
                </div>
                <div className="flex items-center">
                  <input
                    type="checkbox"
                    className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                    checked={state.formData.isValidationEnabled}
                    onChange={(e) => updateFormData('isValidationEnabled', e.target.checked)}
                  />
                  <label className="ml-2 block text-sm text-gray-900">
                    Enable Validation
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
                    {state.editingSchema ? "Update" : "Create"}
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
