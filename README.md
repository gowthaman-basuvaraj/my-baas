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
  - Search Using the Search Fields
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

How
-

* We Shall Use Postgres with JsonB columns for
  * Saving JsonSchema
  * Saving the Data
  * Saving Search Fields (with appropriate indexing)