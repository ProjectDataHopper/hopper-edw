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
package org.hopper.edw.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvBusinessTableTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void defaultsToViewAndDbtStyle() {
    BvBusinessTable table = new BvBusinessTable();
    assertEquals(BvTableType.BUSINESS_TABLE, table.getTableType());
    assertEquals(BvSqlMaterialization.VIEW, table.getMaterializationOrDefault());
    assertEquals(BvSqlReferenceStyle.DBT, table.getReferenceStyleOrDefault());
  }

  @Test
  void xmlRoundTripPreservesSqlMaterializationAndSources() throws Exception {
    BvBusinessTable original = new BvBusinessTable();
    original.setName("product_enriched");
    original.setTableName("product_enriched");
    original.setSqlQuery("SELECT * FROM {{ ref('s_product') }}");
    original.setSchemaName("marts");
    original.setOriginDbtPath("models/marts/product_enriched.sql");
    original.getColumnNotes().add(new BvSqlColumnNote("product_id", "Product key"));
    original.setMaterialization(BvSqlMaterialization.TABLE);
    original.setReferenceStyle(BvSqlReferenceStyle.DBT);
    original.getSources().add(new BvSqlSource("refdata", "Vault", "ref", "lookup"));
    original.getSqlRefs().add(new BvSqlRef(null, "s_product"));

    String xml = XmlHandler.aroundTag("table", XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "table");

    BvBusinessTable restored = new BvBusinessTable();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BvBusinessTable.class, restored, null);

    assertEquals("SELECT * FROM {{ ref('s_product') }}", restored.getSqlQuery());
    assertEquals("marts", restored.getSchemaName());
    assertEquals("models/marts/product_enriched.sql", restored.getOriginDbtPath());
    assertEquals(1, restored.getColumnNotes().size());
    assertEquals("product_id", restored.getColumnNotes().get(0).getName());
    assertEquals(BvSqlMaterialization.TABLE, restored.getMaterializationOrDefault());
    assertEquals(1, restored.getSources().size());
    assertEquals("refdata", restored.getSources().get(0).getSourceName());
    assertEquals(1, restored.getSqlRefs().size());
    assertEquals("s_product", restored.getSqlRefs().get(0).getObjectName());
  }

  @Test
  void checkRequiresSqlAndSourceDeclaration() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    BvBusinessTable table = new BvBusinessTable();
    table.setName("v1");
    table.setTableName("v1");
    table.setSqlQuery("SELECT * FROM {{ source('refdata', 't1') }}");
    model.getTables().add(table);

    List<ICheckResult> remarks = new ArrayList<>();
    table.check(remarks, null, new Variables(), model, new DataVaultModel());

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().toLowerCase().contains("source")));
  }

  @Test
  void checkPassesWhenRefAndSourceResolve() {
    BusinessVaultModel model = new BusinessVaultModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("s_product");
    scd2.setTableName("s_product");
    model.getTables().add(scd2);

    BvBusinessTable table = new BvBusinessTable();
    table.setName("product_v");
    table.setTableName("product_v");
    table.setSqlQuery(
        "SELECT * FROM {{ ref('s_product') }} p JOIN {{ source('refdata', 'lookup') }} l ON 1=1");
    table.getSources().add(new BvSqlSource("refdata", null, "ref", "lookup"));
    model.getTables().add(table);

    List<ICheckResult> remarks = new ArrayList<>();
    table.check(remarks, null, new Variables(), model, new DataVaultModel());

    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> remarks.toString());
  }

  @Test
  void buildCreateStatementSupportsViewAndTable() throws Exception {
    BvBusinessTable table = new BvBusinessTable();
    table.setName("t1");
    table.setTableName("t1");
    table.setMaterialization(BvSqlMaterialization.VIEW);
    // null DatabaseMeta defaults to Postgres-style dialect
    String viewDdl =
        BvSqlViewPipelineSupport.buildCreateStatement(table, null, new Variables(), "SELECT 1");
    assertTrue(viewDdl.startsWith("CREATE OR REPLACE VIEW"));
    assertTrue(viewDdl.contains("AS\nSELECT 1"));

    table.setMaterialization(BvSqlMaterialization.TABLE);
    String tableDdl =
        BvSqlViewPipelineSupport.buildCreateStatement(table, null, new Variables(), "SELECT 2;");
    // Postgres TABLE: drop + CREATE TABLE AS (no CREATE OR REPLACE TABLE)
    assertTrue(tableDdl.contains("DROP TABLE IF EXISTS t1;"));
    assertTrue(tableDdl.contains("CREATE TABLE t1 AS\nSELECT 2"));
    assertFalse(tableDdl.contains("CREATE OR REPLACE TABLE"));
  }
}
