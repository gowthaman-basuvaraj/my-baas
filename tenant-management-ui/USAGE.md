# MyBaaS Tenant Management UI - Usage Guide

## Quick Start

1. **Install dependencies**:
   ```bash
   pnpm install
   ```

2. **Configure the API URL**:
   Edit `.env` file and set:
   ```
   VITE_API_BASE_URL=http://localhost:8080
   ```

3. **Start development server**:
   ```bash
   pnpm run dev
   ```

4. **Access the application**:
   Navigate to `http://localhost:5173`

## First Time Access

When you first access the application, you'll see a login screen requesting:

- **Authentication Token**: Your API authentication token
- **Tenant Host**: Your tenant hostname (e.g., tenant1.mybaas.com)

These credentials are stored in `sessionStorage` and will persist for the browser session.

## Features Overview

### 1. Applications Management (`/applications`)

**What you can do:**
- View all applications for your tenant
- Create new applications with name, description, and active status
- Edit application descriptions and active status (name cannot be changed)
- Delete applications (with confirmation)

**Fields:**
- Application Name (required, cannot be changed after creation)
- Description (required)
- Active Status (boolean)

### 2. Schemas Management (`/schemas`)

**What you can do:**
- Select an application from the dropdown
- View all schemas for the selected application
- Create new schemas with JSON schema definitions
- Edit existing schemas
- Delete schemas (with option to drop associated table)

**Fields:**
- Entity Name (required)
- Version Name (required)
- JSON Schema (textarea - enter valid JSON schema)
- Unique Identifier Formatter (e.g., `{email}-{timestamp}`)
- Indexed JSON Paths (one per line, e.g., `name`, `email`, `address.city`)
- Validation Enabled (checkbox)

**Note:** The JSON schema field is a simple textarea. Enter valid JSON Schema format manually. A visual schema builder will be added later.

### 3. Data Management (`/data`)

**What you can do:**
- Select application and entity from dropdowns
- View all data records for the selected entity
- Create new data records (JSON format)
- Search data using filters
- Delete individual data records

**Search Features:**
- Multiple filter support
- Operators: Equals, Greater Than, Greater or Equal, Less Than, Less or Equal, Contains, Array Contains
- JSON Path support (e.g., `name`, `age`, `address.city`)

**Creating Data:**
- Enter data in JSON format in the textarea
- Data will be validated against the schema if validation is enabled
- Example:
  ```json
  {
    "name": "John Doe",
    "email": "john@example.com",
    "age": 30,
    "address": {
      "city": "New York",
      "country": "USA"
    }
  }
  ```

### 4. Settings Management (`/settings`)

**What you can do:**
- View current tenant settings
- Edit tenant name, allowed IPs, settings, and config
- Save changes

**Fields:**
- Tenant Name (required)
- Allowed IPs (one per line, CIDR notation)
- Settings (JSON object)
- Config (JSON object, e.g., JWKS URI)

## Navigation

The top navigation bar provides quick access to all sections:
- **Applications**: Manage your applications
- **Schemas**: Define data structures
- **Data**: Manage actual data records
- **Settings**: Configure tenant settings
- **Logout**: Clear authentication and return to login

## API Integration

The application integrates with the MyBaaS API using:
- **Base URL**: Configured in `.env` as `VITE_API_BASE_URL`
- **Headers**:
  - `Authorization`: Your auth token
  - `Host`: Your tenant host
  - `Content-Type`: application/json (for requests with body)

## Development Notes

### Project Structure

```
app/
├── components/
│   ├── Layout.tsx          # Main layout with navigation
│   └── Login.tsx           # Authentication component
├── contexts/
│   └── AuthContext.tsx     # Auth state management
├── lib/
│   └── api.ts              # API client functions
└── routes/
    ├── home.tsx            # Landing/login page
    ├── applications.tsx    # Application management
    ├── schemas.tsx         # Schema management
    ├── data.tsx            # Data management
    └── settings.tsx        # Settings management
```

### Adding New Features

1. **New API endpoint**: Add to `app/lib/api.ts`
2. **New page**: Create in `app/routes/`
3. **New route**: Add to `app/routes.ts`
4. **Shared component**: Add to `app/components/`

### Styling

- Built with Tailwind CSS
- Main color scheme: Indigo for primary actions
- Responsive design for mobile and desktop

## Troubleshooting

### CORS Issues
If you encounter CORS errors, ensure your backend API is configured to allow requests from `http://localhost:5173` during development.

### Authentication Errors
If you get 401/403 errors, verify that:
- Your auth token is valid
- Your tenant host is correct
- The backend API is running and accessible

### Build Errors
Run type checking before building:
```bash
pnpm run typecheck
```

## Future Enhancements

Features planned for future releases:
1. Visual JSON Schema builder
2. Proper authentication flow with login API
3. Data editing (currently only create/delete)
4. Bulk data operations
5. Export/import functionality
6. Advanced search with saved queries
7. Real-time data updates
8. User management
