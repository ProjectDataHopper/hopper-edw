{{ config(alias='stg_customers') }}

select
  id as customer_id,
  name
from {{ source('jaffle', 'raw_customers') }}
