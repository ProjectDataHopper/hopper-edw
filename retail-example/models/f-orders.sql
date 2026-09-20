SELECT
  d_customer.cust_channel AS cust_channel,
  d_order.order_status AS order_status,
  d_order_date."year" AS year,
  d_order_date."month" AS month,
  f_orders.total_amount AS total_amount
FROM f_orders AS f_orders
JOIN d_customer AS d_customer ON f_orders.customer_hk = d_customer.customer_hk
JOIN d_order AS d_order ON f_orders.order_hk = d_order.order_hk
JOIN d_date AS d_order_date ON f_orders.order_date_key = d_order_date.date_key 
limit 1000