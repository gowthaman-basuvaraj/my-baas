What is this?
=

This is yet another attempt to build a Backend as a Service (BaaS)

Goals
-

- Define JsonSchema & version using an Admin UI
  - Define Search Fields
- Define Authentication
- CRUD APIs
  - Validate with Schema
  - Search Using the Search Fields (search fields are indexed separately in another table, yet to find another solution where jsonb can be indexed and searched easily)
- Optional JavaScript function execution
  - beforeSave
  - afterSave
  - beforeDelete
  - afterDelete
  - migrateVersion
- Audit Log
- Define Reporting Queries
  - Schedule Reports
  - AdHoc Reports  
  - Job-based execution with status tracking
  - Post Execution actions (Upload to MinIO/S3, Upload to SFTP, Email)
  - Local and MinIO object storage for results
  - Tenant-specific limits and configuration management

How
-

* We Shall Use Postgres with JsonB columns for
  * Saving JsonSchema
  * Saving the Data
  * Saving Search Fields (with appropriate indexing)
  * Report execution logs and job tracking
* Realtime websocket streaming
  * Clients will subscribe to updates
  * either for entire entities or for a particular entity
