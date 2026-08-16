{% set methods = ['card'] %}
select
  {{ cents_to_dollars('amount') }} as dollars,
  *
from {{ ref('stg_customers') }}
