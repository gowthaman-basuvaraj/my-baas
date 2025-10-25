const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

interface RequestOptions {
  method?: string;
  body?: any;
  authToken: string;
  tenantHost: string;
}

async function request(endpoint: string, options: RequestOptions) {
  const { method = 'GET', body, authToken, tenantHost } = options;

  const headers: HeadersInit = {
    'Authorization': authToken,
    'Host': tenantHost,
  };

  if (body) {
    headers['Content-Type'] = 'application/json';
  }

  const config: RequestInit = {
    method,
    headers,
  };

  if (body) {
    config.body = JSON.stringify(body);
  }

  const response = await fetch(`${BASE_URL}${endpoint}`, config);

  if (response.status === 204) {
    return null;
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'Request failed' }));
    throw new Error(error.message || `HTTP ${response.status}`);
  }

  return response.json();
}

// Application APIs
export const applicationApi = {
  list: (authToken: string, tenantHost: string) =>
    request('/api/apps', { authToken, tenantHost }),

  get: (id: string, authToken: string, tenantHost: string) =>
    request(`/api/apps/${id}`, { authToken, tenantHost }),

  create: (data: any, authToken: string, tenantHost: string) =>
    request('/api/apps', { method: 'POST', body: data, authToken, tenantHost }),

  update: (id: string, data: any, authToken: string, tenantHost: string) =>
    request(`/api/apps/${id}`, { method: 'PATCH', body: data, authToken, tenantHost }),

  delete: (id: string, authToken: string, tenantHost: string) =>
    request(`/api/apps/${id}`, { method: 'DELETE', authToken, tenantHost }),
};

// Schema APIs
export const schemaApi = {
  list: (appName: string, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/schemas`, { authToken, tenantHost }),

  get: (appName: string, id: string, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/schemas/${id}`, { authToken, tenantHost }),

  create: (appName: string, data: any, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/schemas`, { method: 'POST', body: data, authToken, tenantHost }),

  update: (appName: string, id: string, data: any, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/schemas/${id}`, { method: 'PUT', body: data, authToken, tenantHost }),

  delete: (appName: string, id: string, dropTable: boolean, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/schemas/${id}${dropTable ? '?dropTable=true' : ''}`, {
      method: 'DELETE',
      authToken,
      tenantHost
    }),
};

// Tenant/Settings APIs
export const settingsApi = {
  get: (authToken: string) =>
    request('/api/settings', { authToken, tenantHost: '' }),

  update: (data: any, authToken: string) =>
    request('/api/settings', { method: 'POST', body: data, authToken, tenantHost: '' }),
};

// Data APIs
export const dataApi = {
  list: (appName: string, entityName: string, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/data/${entityName}`, { authToken, tenantHost }),

  get: (appName: string, entityName: string, uniqueId: string, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/data/${entityName}/${uniqueId}`, { authToken, tenantHost }),

  create: (appName: string, entityName: string, version: string, data: any, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/data/${entityName}/${version}`, {
      method: 'POST',
      body: data,
      authToken,
      tenantHost
    }),

  update: (appName: string, entityName: string, version: string, uniqueId: string, data: any, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/data/${entityName}/${version}/${uniqueId}`, {
      method: 'PUT',
      body: data,
      authToken,
      tenantHost
    }),

  patch: (appName: string, entityName: string, version: string, uniqueId: string, data: any, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/data/${entityName}/${version}/${uniqueId}`, {
      method: 'PATCH',
      body: data,
      authToken,
      tenantHost
    }),

  delete: (appName: string, entityName: string, version: string, uniqueId: string, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/data/${entityName}/${version}/${uniqueId}`, {
      method: 'DELETE',
      authToken,
      tenantHost
    }),

  search: (appName: string, entityName: string, searchCriteria: any, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/data/${entityName}/search`, {
      method: 'POST',
      body: searchCriteria,
      authToken,
      tenantHost
    }),

  validate: (appName: string, entityName: string, version: string, data: any, authToken: string, tenantHost: string) =>
    request(`/api/app/${appName}/data/${entityName}/${version}/validate`, {
      method: 'POST',
      body: data,
      authToken,
      tenantHost
    }),
};
