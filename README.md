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

## Configuration

The application uses environment variables that map to configuration keys in `application.properties`:

### Database
- `DATABASE_URL` → `database.url`
- `DATABASE_USERNAME` → `database.username` 
- `DATABASE_PASSWORD` → `database.password`

### Authentication
- `AUTH_WELLKNOWNURL` → `auth.wellKnownUrl`

### Redis (for multi-instance WebSocket events)
- `REDIS_ENABLED` → `redis.enabled`
- `REDIS_HOST` → `redis.host`
- `REDIS_PORT` → `redis.port`
- `REDIS_PASSWORD` → `redis.password`

### Report System
- `REPORT_LOCAL_STORAGE_PATH` → `report.local.storage.path`
- `REPORT_MAX_CONCURRENT_JOBS` → `report.max.concurrent.jobs`
- `REPORT_JOB_TIMEOUT_MINUTES` → `report.job.timeout.minutes`
- `REPORT_RESULT_RETENTION_DAYS` → `report.result.retention.days`

### MinIO Object Storage (for report results)
- `REPORT_ENABLE_MINIO_UPLOAD` → `report.enable.minio.upload`
- `REPORT_MINIO_ENDPOINT` → `report.minio.endpoint`
- `REPORT_MINIO_BUCKET_NAME` → `report.minio.bucket.name`
- `REPORT_MINIO_ACCESS_KEY` → `report.minio.access.key`
- `REPORT_MINIO_SECRET_KEY` → `report.minio.secret.key`
- `REPORT_MINIO_REGION` → `report.minio.region`
- `REPORT_MINIO_PREFIX` → `report.minio.prefix`
- `REPORT_MINIO_SECURE` → `report.minio.secure`

### Email (for report completion notifications)
- `EMAIL_SMTP_HOST` → `email.smtp.host`
- `EMAIL_SMTP_PORT` → `email.smtp.port`
- `EMAIL_SMTP_USERNAME` → `email.smtp.username`
- `EMAIL_SMTP_PASSWORD` → `email.smtp.password`
- `EMAIL_SMTP_AUTH` → `email.smtp.auth`
- `EMAIL_SMTP_STARTTLS_ENABLE` → `email.smtp.starttls.enable`
- `EMAIL_FROM_ADDRESS` → `email.from.address`
- `EMAIL_FROM_NAME` → `email.from.name`