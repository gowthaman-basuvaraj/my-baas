# MyBaaS API Testing Suite

This directory contains comprehensive API tests for MyBaaS, organized by functionality for better maintainability and clarity.

## File Structure

```
api-tests/
├── README.md                    # This file
├── admin-apis.http             # Admin-only operations
├── application-apis.http       # Application CRUD operations
├── schema-apis.http           # Schema management
├── data-apis.http            # Data operations and queries
└── error-testing-apis.http   # Error scenarios and edge cases
```

## Test Categories

### 🏢 Admin APIs (`admin-apis.http`)
- **Purpose**: System administration and tenant management
- **Scope**: Admin-level operations requiring special privileges
- **Key Features**:
  - Tenant CRUD operations
  - System configuration
  - Admin authentication testing

### 📱 Application APIs (`application-apis.http`)
- **Purpose**: Application lifecycle management within tenant scope
- **Scope**: Tenant-scoped application operations
- **Key Features**:
  - Application creation, updates, deletion
  - Multi-application scenarios
  - Application status management

### 📊 Schema APIs (`schema-apis.http`)
- **Purpose**: Data schema definition and management
- **Scope**: Application-scoped schema operations
- **Key Features**:
  - JSON Schema validation
  - Schema versioning
  - Lifecycle scripts and hooks
  - Indexing configuration

### 💾 Data APIs (`data-apis.http`)
- **Purpose**: Actual data management and querying
- **Scope**: Schema-scoped data operations
- **Key Features**:
  - Data CRUD operations
  - Advanced search and filtering
  - Data validation
  - Migration between schema versions

### ❌ Error Testing APIs (`error-testing-apis.http`)
- **Purpose**: Error handling and edge case validation
- **Scope**: Cross-functional error scenarios
- **Key Features**:
  - Authentication/authorization failures
  - Validation errors
  - 404 and HTTP error codes
  - Malformed requests

## API Architecture

### Hierarchical Structure
```
Tenant
└── Application (ecommerce-app, analytics-app, etc.)
    └── Schema (users:v1, events:v1, etc.)
        └── Data (actual records)
```

### URL Patterns
- **Admin**: `/admin/tenants`
- **Applications**: `/api/applications`
- **Schemas**: `/api/applications/{applicationName}/schemas`
- **Data**: `/api/applications/{applicationName}/data/{entityName}/{versionName?}`

## Multi-Application Examples

The test suite demonstrates two complete applications:

### 🛒 E-commerce App (`ecommerce-app`)
- **Users Schema**: Name, email, age, address, tags
- **Products Schema**: Name, description, price, category, stock
- **Use Cases**: User management, product catalog, customer data

### 📈 Analytics App (`analytics-app`)
- **Events Schema**: Event type, user ID, session, timestamp, properties
- **Use Cases**: Event tracking, user behavior analysis, metrics collection

## Configuration

Set these variables in your HTTP client:

```properties
baseUrl = http://localhost:8080
authToken = Bearer your-jwt-token  
tenantHost = your-tenant.example.com
```

## Testing Workflow

### 1. Setup (Admin APIs)
1. Create tenant(s)
2. Configure tenant settings
3. Verify admin access controls

### 2. Application Setup
1. Create applications (ecommerce-app, analytics-app)
2. Test application lifecycle
3. Verify application isolation

### 3. Schema Definition
1. Define schemas for each application
2. Test schema validation
3. Create multiple versions for migration testing

### 4. Data Operations
1. Create sample data in each application
2. Test CRUD operations
3. Verify search and filtering
4. Test cross-application isolation

### 5. Error Validation
1. Test all error scenarios
2. Verify proper HTTP status codes
3. Validate error messages

## Key Testing Scenarios

### Multi-Tenancy
- ✅ Tenant isolation (data, applications, schemas)
- ✅ Cross-tenant access prevention
- ✅ Tenant-specific configuration

### Multi-Application
- ✅ Application isolation within tenant
- ✅ Separate data tables per application
- ✅ Independent schema management

### Schema Management
- ✅ JSON Schema validation
- ✅ Version control and migration
- ✅ Indexing and performance optimization
- ✅ Lifecycle hooks and scripts

### Data Integrity
- ✅ CRUD operations with validation
- ✅ Complex search queries
- ✅ Data migration between versions
- ✅ Referential integrity

### Security & Authorization
- ✅ JWT token validation
- ✅ Tenant-scoped access control
- ✅ Admin privilege separation
- ✅ API endpoint protection

## Advanced Features Tested

- **Dynamic Table Creation**: Automatic table generation per schema
- **JSON Path Indexing**: Custom indexing for JSONB queries
- **Lifecycle Scripts**: JavaScript execution on data events  
- **Schema Migration**: Automated data transformation between versions
- **Rich Search**: Complex filtering with multiple operators
- **Audit Trails**: Complete change tracking and versioning

## Error Handling Coverage

- **Authentication**: Invalid tokens, missing headers
- **Authorization**: Cross-tenant access, privilege escalation
- **Validation**: Schema validation, required fields, format checking
- **Not Found**: Missing resources at all levels
- **Conflicts**: Duplicate names, constraint violations
- **Malformed Requests**: Invalid JSON, wrong content types

This comprehensive test suite ensures MyBaaS functions correctly across all scenarios and maintains data integrity, security, and performance standards.