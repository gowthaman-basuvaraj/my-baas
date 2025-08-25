-- drop dependencies
alter table if exists data_model drop constraint if exists fk_data_model_schema_id;
drop index if exists ix_data_model_data;
drop index if exists ix_data_model_tenant_id;
drop index if exists search_value_numeric;
drop index if exists search_value_text;
drop index if exists search_value_boolean;
drop index if exists search_value_gin;
drop index if exists search_gin;
drop index if exists ix_data_search_model_tenant_id;
drop index if exists ix_data_search_model_entity_name;
