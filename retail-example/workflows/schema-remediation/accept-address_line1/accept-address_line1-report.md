# Schema remediation: expand models and schemas from catalog length

- **Remediation name:** accept-address_line1
- **Workflow:** `workflows/schema-remediation/accept-address_line1/accept-address_line1-apply-ddl.hwf`
- **SQL script:** `workflows/schema-remediation/accept-address_line1/accept-address_line1-apply-ddl.sql`

## What changed

- Expanding models and target DDL for field 'address_line1' to catalog length 75. The catalog was not modified.
- Downstream target BV customer_360_bv.cust_address (from catalog field address_line1, length 75, confidence EXPLICIT_MAP)
- Downstream target DM d_customer.cust_address (from catalog field address_line1, length 75, confidence DERIVED_VIA_BV)
- SQL script written (3 statement(s) for table(s) sat_customer_address, customer_360_bv, d_customer): workflows/schema-remediation/accept-address_line1/accept-address_line1-apply-ddl.sql
- DDL workflow written: workflows/schema-remediation/accept-address_line1/accept-address_line1-apply-ddl.hwf
- Catalog was not modified by this remediation.
