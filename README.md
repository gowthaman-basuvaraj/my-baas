# MyBaaS - Backend as a Service Platform

## Introduction

MyBaaS is a multi-tenant Backend as a Service (BaaS) platform designed to provide application developers with a complete backend infrastructure without requiring them to implement common backend functionalities repeatedly. The system is built using Kotlin with Javalin framework and utilises PostgreSQL with JSONB columns for flexible data storage.

## Problem Statement

Modern application development typically requires implementing the same fundamental backend capabilities across different projects: user authentication, data persistence with validation, search functionality, audit logging, and reporting mechanisms. Each implementation involves significant development effort, testing, and maintenance overhead. Furthermore, evolving schema requirements require complex migration strategies, and multi-tenancy adds another layer of complexity with tenant isolation, resource allocation, and configuration management requirements.

The absence of a unified platform results in duplicated efforts, inconsistent implementations across projects, and increased time-to-market for new applications.

## Solution Approach

MyBaaS addresses these challenges by providing a comprehensive backend platform where applications can define their data structures using JSON Schema, manage tenant-specific configurations, and utilise pre-built CRUD operations, authentication mechanisms, and reporting capabilities through REST APIs. The platform handles multi-tenancy at the database level with proper isolation between tenants, while allowing each tenant to manage their applications, schemas, and data independently.

## Architecture Overview

The platform is structured around three primary concepts:

1. **Tenant**: Represents an organisation or customer using the platform. Each tenant has isolated data, configurations, and access controls.

2. **Application**: Within each tenant, multiple applications can be created. Each application represents a distinct product or service with its own data schemas and configurations.

3. **Schema**: Each application defines one or more schemas (entities) with versioning support. Schemas are defined using JSON Schema specification and can evolve over time with version management.

## Core Capabilities

### Schema Management

The platform allows definition of JSON Schema for each entity within an application. Key features include:

- **Version Control**: Multiple versions of the same entity schema can coexist, allowing gradual migration of data between schema versions.
- **Validation**: Data written to the system is validated against the defined schema if validation is enabled.
- **Indexed Fields**: Specific JSON paths can be designated for indexing, enabling efficient search operations.
- **Unique Identifier Formatting**: Customisable unique identifier generation using template-based formatting with field substitution.

### Data Operations

Standard CRUD (Create, Read, Update, Delete) operations are available through REST APIs:

- **Create**: Insert new data records with automatic validation against the schema.
- **Read**: Retrieve individual records by unique identifier or list all records for an entity.
- **Update**: Full (PUT) or partial (PATCH) updates to existing records.
- **Delete**: Remove records from the system.
- **Search**: Query data using filter conditions with support for various operators (equals, greater than, less than, contains, array operations).

All data is stored in PostgreSQL JSONB columns with appropriate indexing based on defined indexed paths.

### Lifecycle Hooks

The platform supports JavaScript-based lifecycle hooks that execute during various stages of data operations:

- **BEFORE_SAVE**: Executes before persisting data, allowing validation or transformation.
- **AFTER_SAVE**: Executes after successful data persistence, useful for triggering side effects.
- **BEFORE_DELETE**: Executes before deletion, allowing conditional deletion logic.
- **AFTER_DELETE**: Executes after successful deletion.
- **AFTER_LOAD**: Executes when data is retrieved, allowing transformation before returning.
- **MIGRATE_VERSION**: Executes during schema version migration, transforming data from old version to new version format.

### Authentication and Authorisation

Multi-tenant authentication is implemented with:

- **Tenant Identification**: Requests include tenant host information for proper routing.
- **Token-based Authentication**: JWT or similar token mechanisms for API access.
- **IP Whitelisting**: Tenant-specific IP address restrictions.
- **Configuration Management**: Integration with external authentication providers through configurable JWKS URIs.

### Audit Logging

All data modifications are recorded in audit logs, maintaining a complete history of changes including:

- Operation type (CREATE, UPDATE, DELETE)
- Timestamp of operation
- User/client information
- Previous and new values
- Entity and application context

### Reporting System

A comprehensive reporting subsystem provides:

- **Report Query Definition**: SQL-based or DSL-based query definitions for generating reports.
- **Scheduled Execution**: Cron-based scheduling for periodic report generation.
- **Ad-hoc Execution**: On-demand report generation through API calls.
- **Job Tracking**: Status monitoring for long-running report generation jobs.
- **Result Storage**: Generated reports stored in local filesystem or MinIO/S3 object storage.
- **Post-execution Actions**: Configurable actions after report generation including email delivery, SFTP upload, or S3 upload.
- **Tenant Limits**: Resource allocation and usage limits per tenant.

### Real-time Updates

WebSocket-based real-time update streaming allows clients to:

- Subscribe to updates for specific entities or entire applications.
- Receive notifications when data is created, updated, or deleted.
- Implement reactive user interfaces without polling.

## Technical Implementation

### Database Design

PostgreSQL serves as the primary datastore with the following characteristics:

- **JSONB Columns**: Primary data storage using PostgreSQL JSONB type for schema flexibility.
- **Indexing Strategy**:
  - B-tree indexes on specified search fields for exact match and range queries.
  - GIN (Generalized Inverted Index) indexes on first and second level nested JSON paths for efficient searching.
- **Schema Tables**: Separate tables for schema definitions, metadata, and versioning information.
- **Tenant Isolation**: Database-level or schema-level separation between tenants for data isolation.

### API Structure

RESTful API endpoints follow a hierarchical structure:

```
/api/apps                                    - Application management
/api/apps/{appId}                           - Specific application operations
/api/app/{appName}/schemas                  - Schema management
/api/app/{appName}/schemas/{schemaId}       - Specific schema operations
/api/app/{appName}/data/{entity}/{version}  - Data CRUD operations
/api/app/{appName}/data/{entity}/search     - Search operations
/api/settings                               - Tenant configuration
```

### Technology Stack

- **Backend Framework**: Kotlin with Javalin framework
- **Database**: PostgreSQL with JSONB support
- **Authentication**: JWT-based with JWKS integration
- **Scripting Engine**: JavaScript execution for lifecycle hooks
- **Object Storage**: MinIO or S3-compatible storage for report results
- **Communication**: REST APIs and WebSocket for real-time updates

## Use Cases

The platform is suitable for:

1. **SaaS Application Development**: Build multi-tenant SaaS products without implementing common backend infrastructure.

2. **Internal Tools**: Rapidly develop internal business applications with custom data models.

3. **Mobile/Web Backends**: Provide backend services for mobile or web applications with dynamic schema requirements.

4. **Data Collection Systems**: Implement form-based data collection with validation and reporting.

5. **Content Management**: Build content management systems with flexible content types.

6. **IoT Data Storage**: Store and query time-series or event data from IoT devices.

## Limitations and Considerations

- **Query Complexity**: Complex joins across entities may be limited compared to traditional relational databases.
- **Performance**: JSONB query performance depends on proper indexing strategy.
- **Schema Migrations**: Large-scale data migrations between schema versions may require careful planning.
- **JavaScript Execution**: Lifecycle hooks execute synchronously and may impact request latency.
- **Tenant Scaling**: Resource allocation and isolation strategies need consideration for large tenant bases.

## Project Structure

```
MyBaaS/
├── src/main/kotlin/my/baas/          # Backend application source
│   ├── Application.kt                # Main application entry point
│   ├── controllers/                  # REST API controllers
│   ├── services/                     # Business logic layer
│   ├── repositories/                 # Data access layer
│   ├── models/                       # Domain models
│   └── config/                       # Configuration classes
├── api-tests/                        # HTTP test files
│   ├── application-apis.http         # Application management tests
│   ├── schema-apis.http             # Schema management tests
│   ├── data-apis.http               # Data operation tests
│   └── tenat-apis.http              # Tenant configuration tests
└── tenant-management-ui/            # React-based UI for tenant self-service
    ├── app/                         # Application source
    │   ├── contexts/                # React contexts
    │   ├── routes/                  # Page components
    │   └── lib/                     # Utility functions
    └── public/                      # Static assets
```

## Development Setup

The backend application requires:

- JDK 21 or higher
- PostgreSQL 18 or higher with JSONB support
- Gradle for dependency management and builds

The frontend tenant management UI requires:

- Node.js 20 or higher
- pnpm package manager

Detailed setup instructions are available in respective component directories.

## API Documentation

Comprehensive API documentation is available through the HTTP test files in the `api-tests/` directory. These files demonstrate all available endpoints with example requests and responses. The test files can be executed using IntelliJ IDEA's HTTP Client or similar tools supporting the HTTP request format.

## Tenant Management Interface

A React-based web interface is provided for tenant self-service operations including:

- Application creation and management
- Schema definition and versioning
- Data browsing and searching
- Tenant configuration management

This interface eliminates the need for API interaction for common administrative tasks.

## Future Enhancements

Potential areas for expansion include:

- GraphQL API support for more flexible data querying
- Enhanced role-based access control with fine-grained permissions
- Built-in caching layer for improved read performance
- Bulk data import/export capabilities
- Visual schema builder in the management interface
- Advanced analytics and monitoring dashboard
- Support for additional database backends
- Distributed deployment with horizontal scaling

## Conclusion

MyBaaS provides a pragmatic solution for building backend services with minimal development effort. By abstracting common backend concerns into a reusable platform, development teams can focus on business logic rather than infrastructure implementation. The flexible schema definition, versioning support, and comprehensive API coverage make it suitable for a wide range of application requirements.
