{% macro cents_to_dollars(col) -%}
  (({{ col }}) / 100.0)
{%- endmacro %}
