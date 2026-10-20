SELECT dd.year, dd.month, count(*) 
FROM f_orders fo
JOIN d_date dd ON fo.order_date_key = dd.date_key
GROUP BY dd.year, dd.month
ORDER BY 1, 2

