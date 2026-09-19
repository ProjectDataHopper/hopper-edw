<!--
Copyright 2026 i-Bridge bv

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Models

`adventureworks.hsm` and `adventureworks.hdv` are generated from the live SQL Server restore:

```sh
./scripts/run-adventureworks.sh generate-models
```

That imports `Person`, `HumanResources`, `Production`, `Purchasing`, and `Sales` (skips `dbo` plus document/photo tables), then applies **Generate Data Vault** (about 164 vault tables). Review in Hop GUI: `Customer` is a hub with links to person/store; `SalesOrderDetail` is classified as a hub because it has its own identity column. The generator also adds `SalesOrderDetail.ProductID` → `Product` so `lnk_salesorderdetail_product` exists for the sales fact.

Hand-authored Business Vault and dimensional models (see [docs/plans/adventureworks-sample-plan.md](../../docs/plans/adventureworks-sample-plan.md)):

| File | Role |
|------|------|
| `adventureworks.hbv` | `bv_customer_360`, `bv_product_360`, `pit_employee` / `bv_employee` |
| `adventureworks-conformed-dims.hdm` | `d_date`, `d_customer`, `d_product`, `d_employee`, `d_sales_territory`, `d_vendor` |
| `adventureworks-f-sales.hdm` | `f_sales_order_line` |
| `adventureworks-f-purchasing.hdm` | `f_purchase_order_line` |
