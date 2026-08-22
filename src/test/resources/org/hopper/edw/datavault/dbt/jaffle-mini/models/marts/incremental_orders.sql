{{ config(materialized='incremental') }}
select * from {{ ref('stg_customers') }}
