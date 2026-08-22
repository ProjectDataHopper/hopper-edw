/*
 * Copyright 2026 i-Bridge bv
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmFactDimensionJoinValidationSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void reportsMismatchWhenHashKeyJoinsToIntegerNaturalKey() {
    RowMeta sourceRowMeta = new RowMeta();
    sourceRowMeta.addValueMeta(new ValueMetaString("customer_hk"));

    RowMeta dimensionLayout = new RowMeta();
    dimensionLayout.addValueMeta(new ValueMetaInteger("customer_id"));

    DmFactDimensionRole role = new DmFactDimensionRole();
    role.setDimensionTableName("d_customer");
    role.setForeignKeyColumn("customer_hk");
    role.setSourceFieldName("customer_hk");

    DmFact fact = new DmFact();
    fact.setName("f_orders");

    List<ICheckResult> remarks = new ArrayList<>();
    DmFactDimensionJoinValidationSupport.validateJoinKeyTypes(
        remarks,
        sourceRowMeta,
        dimensionLayout,
        "f_orders",
        "d_customer",
        role,
        "customer_id",
        fact,
        new Variables());

    assertEquals(1, remarks.size());
    assertEquals(ICheckResult.TYPE_RESULT_ERROR, remarks.get(0).getType());
    assertTrue(remarks.get(0).getText().contains("customer_hk"));
    assertTrue(remarks.get(0).getText().contains("customer_id"));
    assertTrue(remarks.get(0).getText().contains("String"));
    assertTrue(remarks.get(0).getText().contains("Integer"));
  }

  @Test
  void acceptsMatchingIntegerJoinKeys() {
    RowMeta sourceRowMeta = new RowMeta();
    sourceRowMeta.addValueMeta(new ValueMetaInteger("customer_id"));

    RowMeta dimensionLayout = new RowMeta();
    dimensionLayout.addValueMeta(new ValueMetaInteger("customer_id"));

    DmFactDimensionRole role = new DmFactDimensionRole();
    role.setDimensionTableName("d_customer");
    role.setForeignKeyColumn("customer_hk");
    role.setSourceFieldName("customer_id");

    DmFact fact = new DmFact();
    fact.setName("f_orders");

    List<ICheckResult> remarks = new ArrayList<>();
    DmFactDimensionJoinValidationSupport.validateJoinKeyTypes(
        remarks,
        sourceRowMeta,
        dimensionLayout,
        "f_orders",
        "d_customer",
        role,
        "customer_id",
        fact,
        new Variables());

    assertTrue(remarks.isEmpty());
  }

  @Test
  void dateRoleRequiresDateSourceAndIntegerNaturalKey() {
    RowMeta sourceRowMeta = new RowMeta();
    sourceRowMeta.addValueMeta(new ValueMetaString("order_date"));

    RowMeta dimensionLayout = new RowMeta();
    dimensionLayout.addValueMeta(new ValueMetaString("date_key"));

    DmFactDimensionRole role = new DmFactDimensionRole();
    role.setDimensionTableName("d_order_date");
    role.setForeignKeyColumn("order_date_key");
    role.setSourceFieldName("order_date");
    role.setTruncateToDateKey(true);

    DmFact fact = new DmFact();
    fact.setName("f_orders");

    List<ICheckResult> remarks = new ArrayList<>();
    DmFactDimensionJoinValidationSupport.validateJoinKeyTypes(
        remarks,
        sourceRowMeta,
        dimensionLayout,
        "f_orders",
        "d_order_date",
        role,
        "date_key",
        fact,
        new Variables());

    assertEquals(2, remarks.size());
    assertTrue(remarks.stream().anyMatch(r -> r.getText().contains("Date or Timestamp")));
    assertTrue(remarks.stream().anyMatch(r -> r.getText().contains("must be Integer")));
  }

  @Test
  void dateRoleAcceptsDateSourceAndIntegerNaturalKey() {
    RowMeta sourceRowMeta = new RowMeta();
    sourceRowMeta.addValueMeta(new ValueMetaDate("order_date"));

    RowMeta dimensionLayout = new RowMeta();
    dimensionLayout.addValueMeta(new ValueMetaInteger("date_key"));

    DmFactDimensionRole role = new DmFactDimensionRole();
    role.setDimensionTableName("d_order_date");
    role.setForeignKeyColumn("order_date_key");
    role.setSourceFieldName("order_date");
    role.setTruncateToDateKey(true);

    DmFact fact = new DmFact();
    fact.setName("f_orders");

    List<ICheckResult> remarks = new ArrayList<>();
    DmFactDimensionJoinValidationSupport.validateJoinKeyTypes(
        remarks,
        sourceRowMeta,
        dimensionLayout,
        "f_orders",
        "d_order_date",
        role,
        "date_key",
        fact,
        new Variables());

    assertTrue(remarks.isEmpty());
  }
}
