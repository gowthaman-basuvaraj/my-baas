import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { useAuth } from './AuthContext';
import { applicationApi, schemaApi } from '../lib/api';

interface Application {
  id: string;
  applicationName: string;
  description: string;
  isActive: boolean;
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

interface AppState {
  applications: Application[];
  selectedApp: string | null;
  schemas: Schema[];
  loading: boolean;
  error: string | null;
}

interface AppContextType extends AppState {
  setSelectedApp: (appName: string) => void;
  reloadApplications: () => Promise<void>;
  reloadSchemas: () => Promise<void>;
  getSchemasByApp: (appName: string) => Schema[];
  getApplicationByName: (name: string) => Application | undefined;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

export function AppProvider({ children }: { children: ReactNode }) {
  const { authToken, tenantHost, isAuthenticated } = useAuth();
  const [state, setState] = useState<AppState>({
    applications: [],
    selectedApp: null,
    schemas: [],
    loading: false,
    error: null,
  });

  // Load applications on mount and when auth changes
  useEffect(() => {
    if (isAuthenticated && authToken && tenantHost) {
      loadApplications();
    }
  }, [isAuthenticated, authToken, tenantHost]);

  // Load schemas when selected app changes
  useEffect(() => {
    if (state.selectedApp && authToken && tenantHost) {
      loadSchemas(state.selectedApp);
    }
  }, [state.selectedApp, authToken, tenantHost]);

  const loadApplications = async () => {
    if (!authToken || !tenantHost) return;

    try {
      setState(prev => ({ ...prev, loading: true, error: null }));
      const data = await applicationApi.list(authToken, tenantHost);
      const apps = Array.isArray(data) ? data : data.content || [];
      setState(prev => ({
        ...prev,
        applications: apps,
        loading: false,
        selectedApp: prev.selectedApp || (apps.length > 0 ? apps[0].applicationName : null),
      }));
    } catch (err: any) {
      setState(prev => ({ ...prev, error: err.message, loading: false }));
    }
  };

  const loadSchemas = async (appName: string) => {
    if (!authToken || !tenantHost) return;

    try {
      setState(prev => ({ ...prev, loading: true, error: null }));
      const data = await schemaApi.list(appName, authToken, tenantHost);
      const schemaList = Array.isArray(data) ? data : data.content || [];
      setState(prev => ({ ...prev, schemas: schemaList, loading: false }));
    } catch (err: any) {
      setState(prev => ({ ...prev, error: err.message, loading: false }));
    }
  };

  const setSelectedApp = (appName: string) => {
    setState(prev => ({ ...prev, selectedApp: appName }));
  };

  const reloadApplications = async () => {
    await loadApplications();
  };

  const reloadSchemas = async () => {
    if (state.selectedApp) {
      await loadSchemas(state.selectedApp);
    }
  };

  const getSchemasByApp = (appName: string): Schema[] => {
    if (state.selectedApp === appName) {
      return state.schemas;
    }
    return [];
  };

  const getApplicationByName = (name: string): Application | undefined => {
    return state.applications.find(app => app.applicationName === name);
  };

  return (
    <AppContext.Provider
      value={{
        ...state,
        setSelectedApp,
        reloadApplications,
        reloadSchemas,
        getSchemasByApp,
        getApplicationByName,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useApp() {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useApp must be used within an AppProvider');
  }
  return context;
}
