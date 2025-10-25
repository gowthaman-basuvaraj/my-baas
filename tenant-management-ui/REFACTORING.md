# Code Refactoring Summary

## Overview

This document describes the refactoring work done to improve state management and reduce code duplication across the tenant management UI.

## Key Changes

### 1. Created AppContext for Shared State

**File**: `app/contexts/AppContext.tsx`

**Purpose**: Centralize shared application data and reduce duplicate API calls across components.

**Shared State**:
- `applications`: List of all applications
- `selectedApp`: Currently selected application name
- `schemas`: List of schemas for the selected application
- `loading`: Loading state
- `error`: Error messages

**Benefits**:
- Applications list is fetched once and shared across all pages
- Schemas are automatically loaded when selectedApp changes
- Eliminates duplicate `applicationApi.list()` calls
- Provides helper functions like `getSchemasByApp()` and `getApplicationByName()`

### 2. Consolidated Multiple useState Calls

**Before** (Multiple State Variables):
```typescript
const [applications, setApplications] = useState<Application[]>([]);
const [loading, setLoading] = useState(true);
const [error, setError] = useState<string | null>(null);
const [showModal, setShowModal] = useState(false);
const [editingApp, setEditingApp] = useState<Application | null>(null);
const [formData, setFormData] = useState({...});
```

**After** (Single State Object):
```typescript
interface PageState {
  showModal: boolean;
  editingApp: Application | null;
  formData: {
    applicationName: string;
    description: string;
    isActive: boolean;
  };
}

const [state, setState] = useState<PageState>({
  showModal: false,
  editingApp: null,
  formData: { ... },
});
```

**Benefits**:
- Better organization and readability
- Type-safe state updates with interfaces
- Easier to track all component state in one place
- Simpler state updates using `setState(prev => ({ ...prev, ... }))`

### 3. Component-Specific Refactoring

#### Applications Page (`app/routes/applications.tsx`)
- **State**: Single `PageState` object for modal and form data
- **AppContext Usage**: Gets `applications`, `loading`, `error` from context
- **Benefits**: No longer needs to fetch applications list independently

#### Schemas Page (`app/routes/schemas.tsx`)
- **State**: Single `PageState` object for modal and form data
- **AppContext Usage**: Gets `applications`, `selectedApp`, `schemas`, `loading`, `error`
- **Benefits**: Automatically gets updated schemas when app selection changes

#### Data Page (`app/routes/data.tsx`)
- **State**: Single `PageState` object including data list, modals, and search filters
- **AppContext Usage**: Gets `applications`, `selectedApp`, `schemas`
- **Benefits**: Doesn't need to fetch apps and schemas independently

#### Settings Page (`app/routes/settings.tsx`)
- **State**: Single `PageState` object for loading, editing state, and form data
- **AppContext Usage**: None (settings are tenant-specific, not shared)
- **Benefits**: Cleaner state management with single object

### 4. Helper Functions for State Updates

All components now use helper functions to update specific fields:

```typescript
const updateFormData = (field: keyof PageState['formData'], value: any) => {
  setState(prev => ({
    ...prev,
    formData: { ...prev.formData, [field]: value },
  }));
};
```

This provides:
- Type-safe field updates
- Consistent update patterns
- Less repetitive code

## Architecture Benefits

### Before
```
Applications Page
  ├─ Fetches applications list
  └─ Manages own state (6+ useState calls)

Schemas Page
  ├─ Fetches applications list (duplicate!)
  ├─ Fetches schemas when app selected
  └─ Manages own state (8+ useState calls)

Data Page
  ├─ Fetches applications list (duplicate!)
  ├─ Fetches schemas when app selected (duplicate!)
  └─ Manages own state (10+ useState calls)
```

### After
```
AppContext
  ├─ Fetches applications once
  ├─ Manages selected app
  └─ Fetches schemas for selected app

Applications Page
  ├─ Uses AppContext (applications, loading, error)
  └─ Single PageState object (3 properties)

Schemas Page
  ├─ Uses AppContext (applications, schemas, selectedApp)
  └─ Single PageState object (2 properties + formData)

Data Page
  ├─ Uses AppContext (applications, schemas, selectedApp)
  └─ Single PageState object (8 properties unified)
```

## Performance Improvements

1. **Reduced API Calls**:
   - Before: 3-4 calls to fetch applications list
   - After: 1 call, shared across all pages

2. **Automatic Schema Loading**:
   - Schemas automatically reload when selectedApp changes
   - No manual coordination needed between pages

3. **Better Memory Usage**:
   - Single source of truth for shared data
   - Less state duplication

## Code Quality Improvements

1. **Type Safety**: All state is properly typed with interfaces
2. **Consistency**: All components follow the same state management pattern
3. **Maintainability**: Easier to update and debug with centralized state
4. **Readability**: Single state object is easier to understand than multiple variables
5. **Testability**: State updates are predictable and easier to test

## Migration Notes

If you need to add new components:

1. **If using shared data** (applications, schemas):
   - Import and use `useApp()` hook
   - Don't fetch applications/schemas again

2. **For component-specific state**:
   - Define a `PageState` interface
   - Use single `useState` with that interface
   - Create helper functions for updates

Example:
```typescript
import { useApp } from '../contexts/AppContext';

interface PageState {
  // Define all your local state here
}

export default function MyPage() {
  const { applications, selectedApp, schemas } = useApp();
  const [state, setState] = useState<PageState>({ ... });

  // Your component logic
}
```

## Testing

All changes have been:
- ✅ Type-checked with `pnpm run typecheck`
- ✅ Built successfully with `pnpm run build`
- ✅ Verified to maintain existing functionality

## Files Modified

1. `app/contexts/AppContext.tsx` - New file
2. `app/root.tsx` - Added AppProvider
3. `app/routes/applications.tsx` - Refactored state management
4. `app/routes/schemas.tsx` - Refactored state management
5. `app/routes/data.tsx` - Refactored state management
6. `app/routes/settings.tsx` - Refactored state management

## Summary

This refactoring significantly improves:
- Code organization and maintainability
- Performance through shared state
- Developer experience with consistent patterns
- Type safety throughout the application

The application remains fully functional while being more maintainable and efficient.
