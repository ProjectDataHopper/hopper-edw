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
package org.hopper.edw.datavault.transform.sqlexpression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.expression.SqlExpressionSpec;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SqlExpressionBvTableSupportTest {

  private static final String HBV =
      Path.of("retail-example/models/retail-360.hbv").toAbsolutePath().normalize().toString();

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void inlineSpecsWhenUnbound() throws Exception {
    SqlExpressionMeta meta = new SqlExpressionMeta();
    meta.getFields().add(new SqlExpressionField("out", "UPPER(name)"));
    List<SqlExpressionSpec> specs = meta.resolveSpecs(new Variables(), null);
    assertEquals(1, specs.size());
    assertEquals("out", specs.get(0).getFieldName());
  }

  @Test
  void cloneCopiesBusinessVaultReferences() {
    SqlExpressionMeta meta = new SqlExpressionMeta();
    meta.setBusinessVaultModelFilename("${PROJECT_HOME}/models/retail-360.hbv");
    meta.setScd2TableName("customer_360_bv");
    SqlExpressionMeta copy = meta.clone();
    assertEquals(meta.getBusinessVaultModelFilename(), copy.getBusinessVaultModelFilename());
    assertEquals(meta.getScd2TableName(), copy.getScd2TableName());
  }

  @Test
  void loadsCalculationsFromRetailScd2Table() throws Exception {
    SqlExpressionMeta meta = SqlExpressionMetaFactory.createFromBvTable(HBV, "customer_360_bv");
    assertTrue(meta.usesBusinessVaultTable(new Variables()));
    List<SqlExpressionSpec> specs = meta.resolveSpecs(new Variables(), null);
    assertFalse(specs.isEmpty());
    assertEquals("online_indicator", specs.get(0).getFieldName());
    assertTrue(specs.get(0).getExpression().contains("cust_segment"));
  }

  @Test
  void listsScd2TablesFromRetailModel() throws Exception {
    BvScd2Table table =
        SqlExpressionBvTableSupport.loadScd2Table(HBV, "customer_360_bv", new Variables(), null);
    assertEquals("customer_360_bv", table.getName());
    List<String> names =
        SqlExpressionBvTableSupport.listScd2TableNames(
            SqlExpressionBvTableSupport.loadModel(HBV, new Variables(), null));
    assertTrue(names.contains("customer_360_bv"));
  }
}
