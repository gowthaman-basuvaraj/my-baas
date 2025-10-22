-- apply changes
create table application_model (
  id                            uuid not null,
  tenant_id                     uuid not null,
  is_active                     boolean default false not null,
  deleted                       boolean default false not null,
  version                       integer not null,
  when_created                  timestamptz not null,
  when_modified                 timestamptz not null,
  application_name              varchar not null,
  description                   varchar,
  who_created                   varchar not null,
  who_modified                  varchar not null,
  constraint uq_application_model_application_name_tenant_id unique (application_name,tenant_id),
  constraint pk_application_model primary key (id)
);

create table audit_log (
  id                            uuid not null,
  tenant_id                     uuid not null,
  created_at                    timestamptz(255) not null,
  deleted                       boolean default false not null,
  version                       integer not null,
  when_created                  timestamptz not null,
  when_modified                 timestamptz not null,
  entity_name                   varchar(255) not null,
  unique_identifier             varchar(255) not null,
  action                        varchar(255) not null,
  user_id                       varchar(255) not null,
  client_ip                     varchar(255) not null,
  old_data                      jsonb,
  new_data                      jsonb,
  notes                         varchar(255),
  who_created                   varchar not null,
  who_modified                  varchar not null,
  constraint ck_audit_log_action check ( action in ('CREATE','UPDATE','PATCH','DELETE','MIGRATE','RE_INDEX')),
  constraint pk_audit_log primary key (id)
);

create table data_model (
  id                            uuid not null,
  tenant_id                     uuid not null,
  application_id                uuid,
  schema_id                     uuid not null,
  deleted                       boolean default false not null,
  version                       integer not null,
  when_created                  timestamptz not null,
  when_modified                 timestamptz not null,
  data                          jsonb not null,
  unique_identifier             varchar not null,
  entity_name                   varchar not null,
  version_name                  varchar not null,
  who_created                   varchar not null,
  who_modified                  varchar not null
) partition by range (tenant_id, application_id, schema_id, when_created);

create table report_execution_log (
  id                            uuid not null,
  tenant_id                     uuid not null,
  report_id                     uuid not null,
  started_at                    timestamptz,
  completed_at                  timestamptz,
  execution_time_ms             bigint,
  error_message                 TEXT,
  row_count                     integer,
  file_size_bytes               bigint,
  deleted                       boolean default false not null,
  version                       integer not null,
  when_created                  timestamptz not null,
  when_modified                 timestamptz not null,
  job_id                        varchar,
  status                        varchar(9) not null,
  execution_type                varchar(11),
  local_file_path               varchar,
  s3_bucket_name                varchar,
  s3_object_key                 varchar,
  file_format                   varchar(4) not null,
  storage_location              varchar(5) not null,
  execution_metadata            json not null,
  requested_by                  varchar,
  scheduler_job_id              varchar,
  who_created                   varchar not null,
  who_modified                  varchar not null,
  constraint ck_report_execution_log_status check ( status in ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED')),
  constraint ck_report_execution_log_execution_type check ( execution_type in ('MANUAL','SCHEDULED','API_REQUEST')),
  constraint ck_report_execution_log_file_format check ( file_format in ('CSV','JSON','XLS','XLSX')),
  constraint ck_report_execution_log_storage_location check ( storage_location in ('NONE','LOCAL','S3','BOTH')),
  constraint report_execution_job_id_idx unique (job_id),
  constraint pk_report_execution_log primary key (id)
);

create table report_model (
  id                            uuid not null,
  tenant_id                     uuid not null,
  sql                           TEXT not null,
  is_active                     boolean default false not null,
  deleted                       boolean default false not null,
  version                       integer not null,
  when_created                  timestamptz not null,
  when_modified                 timestamptz not null,
  name                          varchar(255) not null,
  execution_type                varchar(255) not null,
  cron_schedule                 varchar,
  completion_actions            jsonb not null,
  file_format                   varchar(255) not null,
  parameters                    jsonb not null,
  who_created                   varchar not null,
  who_modified                  varchar not null,
  constraint ck_report_model_execution_type check ( execution_type in ('ADHOC','SCHEDULED','BOTH')),
  constraint ck_report_model_file_format check ( file_format in ('CSV','JSON','XLS','XLSX')),
  constraint report_name_idx unique (name,tenant_id),
  constraint pk_report_model primary key (id)
);

create table schema_model (
  id                            uuid not null,
  tenant_id                     uuid not null,
  application_id                uuid,
  is_validation_enabled         boolean default false not null,
  deleted                       boolean default false not null,
  version                       integer not null,
  when_created                  timestamptz not null,
  when_modified                 timestamptz not null,
  entity_name                   varchar not null,
  json_schema                   jsonb not null,
  version_name                  varchar not null,
  unique_identifier_formatter   varchar not null,
  indexed_json_paths            jsonb not null,
  lifecycle_scripts             jsonb not null,
  partition_by                  varchar(5) not null,
  unique_id                     varchar not null,
  who_created                   varchar not null,
  who_modified                  varchar not null,
  constraint ck_schema_model_partition_by check ( partition_by in ('YEAR','MONTH')),
  constraint uq_schema_model_entity_name_version_name_tenant_id_applic_1 unique (entity_name,version_name,tenant_id,application_id),
  constraint pk_schema_model primary key (id)
);

create table tenant_model (
  id                            uuid not null,
  is_active                     boolean default false not null,
  deleted                       boolean default false not null,
  version                       integer not null,
  when_created                  timestamptz not null,
  when_modified                 timestamptz not null,
  name                          varchar not null,
  domain                        varchar not null,
  description                   varchar,
  settings                      jsonb not null,
  config                        jsonb not null,
  allowed_ips                   jsonb,
  who_created                   varchar not null,
  who_modified                  varchar not null,
  constraint uq_tenant_model_domain unique (domain),
  constraint pk_tenant_model primary key (id)
);

-- foreign keys and indices
create index ix_application_model_tenant_id on application_model (tenant_id);
alter table application_model add constraint fk_application_model_tenant_id foreign key (tenant_id) references tenant_model (id) on delete restrict on update restrict;

create index ix_audit_log_tenant_id on audit_log (tenant_id);
alter table audit_log add constraint fk_audit_log_tenant_id foreign key (tenant_id) references tenant_model (id) on delete restrict on update restrict;

create index ix_data_model_tenant_id on data_model (tenant_id);
alter table data_model add constraint fk_data_model_tenant_id foreign key (tenant_id) references tenant_model (id) on delete restrict on update restrict;

create index ix_data_model_application_id on data_model (application_id);
alter table data_model add constraint fk_data_model_application_id foreign key (application_id) references application_model (id) on delete restrict on update restrict;

create index ix_data_model_schema_id on data_model (schema_id);
alter table data_model add constraint fk_data_model_schema_id foreign key (schema_id) references schema_model (id) on delete restrict on update restrict;

create index ix_report_execution_log_tenant_id on report_execution_log (tenant_id);
alter table report_execution_log add constraint fk_report_execution_log_tenant_id foreign key (tenant_id) references tenant_model (id) on delete restrict on update restrict;

alter table report_execution_log add constraint fk_report_execution_log_report_id foreign key (report_id) references report_model (id) on delete restrict on update restrict;

create index ix_report_model_tenant_id on report_model (tenant_id);
alter table report_model add constraint fk_report_model_tenant_id foreign key (tenant_id) references tenant_model (id) on delete restrict on update restrict;

create index ix_schema_model_tenant_id on schema_model (tenant_id);
alter table schema_model add constraint fk_schema_model_tenant_id foreign key (tenant_id) references tenant_model (id) on delete restrict on update restrict;

create index ix_schema_model_application_id on schema_model (application_id);
alter table schema_model add constraint fk_schema_model_application_id foreign key (application_id) references application_model (id) on delete restrict on update restrict;

create index if not exists ix_audit_log_tenant_id_entity_name_action_created_at_uniq_1 on audit_log (tenant_id,entity_name,action,created_at,unique_identifier,user_id);
create index if not exists data_model_unique_identifier on data_model (unique_identifier,tenant_id,application_id,schema_id);
create index if not exists report_execution_report_id_idx on report_execution_log (report_id);
create index if not exists report_execution_status_idx on report_execution_log (status,storage_location);
