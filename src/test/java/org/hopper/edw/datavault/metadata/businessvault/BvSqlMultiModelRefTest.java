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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BvSqlMultiModelRefTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void twoArgRelativePathResolvesExternalDvSatellite() throws Exception {
    Path dvDir = tempDir.resolve("dv");
    Path bvDir = tempDir.resolve("bv");
    Files.createDirectories(dvDir);
    Files.createDirectories(bvDir);

    Path hdv = dvDir.resolve("retail-360.hdv");
    Files.writeString(hdv, hdvWithSatellite("retail-360", "sat_product"));

    Path hbv = bvDir.resolve("retail-sql.hbv");
    Files.writeString(hbv, "<!-- placeholder -->");

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setFilename(hbv.toString());
    bvModel.setName("retail-sql");
    bvModel.getConfigurationOrDefault().setTargetDatabase("Vault");

    BvBusinessTable sqlTable = new BvBusinessTable();
    sqlTable.setName("satb_product_hb");
    sqlTable.setTableName("satb_product_hb");
    sqlTable.setSqlQuery("SELECT * FROM {{ ref('../dv/retail-360', 'sat_product') }}");
    bvModel.getTables().add(sqlTable);

    // Linked DV is empty / different model — resolution must use path.
    DataVaultModel linkedDv = new DataVaultModel();
    linkedDv.setFilename(tempDir.resolve("other.hdv").toString());
    linkedDv.setName("other");

    List<BvSqlRef> refs =
        BvSqlRefResolver.syncRefsFromSql(sqlTable, bvModel, linkedDv, new Variables(), null);

    assertEquals(1, refs.size());
    BvSqlRef ref = refs.get(0);
    assertEquals(BvSqlResolvedKind.DV_TABLE, ref.getResolvedKind());
    assertEquals("sat_product", ref.getResolvedTableName());
    assertEquals(DvTableType.SATELLITE, ref.getResolvedDvTableType());
    assertNotNull(ref.getResolvedModelFilename());
    assertTrue(
        ref.getResolvedModelFilename().replace('\\', '/').contains("retail-360.hdv")
            || ref.getResolvedModelFilename().contains("retail-360"));
    assertTrue(BusinessVaultDerivativeSupport.hasDerivative(sqlTable, "sat_product"));
  }

  @Test
  void ensureDvCanvasAliasesStoresReferencedModelFilename() throws Exception {
    Path modelDir = tempDir.resolve("models");
    Files.createDirectories(modelDir);
    Path hdv = modelDir.resolve("core.hdv");
    Files.writeString(hdv, hdvWithSatellite("core", "hub_customer"));
    Path hbv = modelDir.resolve("bv.hbv");
    Files.writeString(hbv, "<!-- -->");

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setFilename(hbv.toString());

    BvBusinessTable sqlTable = new BvBusinessTable();
    sqlTable.setName("v1");
    sqlTable.setTableName("v1");
    sqlTable.setSqlQuery("SELECT * FROM {{ ref('core', 'hub_customer') }}");

    List<BvSqlRef> refs =
        BvSqlRefResolver.syncRefsFromSql(
            sqlTable, bvModel, new DataVaultModel(), new Variables(), null);
    assertEquals(BvSqlResolvedKind.DV_TABLE, refs.get(0).getResolvedKind());

    BvSqlRefResolver.ensureDvCanvasAliases(bvModel, refs, new DataVaultModel());
    assertEquals(1, bvModel.getDvReferences().size());
    BvDvTableReference alias = bvModel.getDvReferences().get(0);
    assertEquals("hub_customer", alias.getDvTableName());
    assertNotNull(alias.getReferencedModelFilename());
    assertTrue(
        alias.getReferencedModelFilename().replace('\\', '/').contains("core.hdv")
            || alias.getReferencedModelFilename().contains("core"));
  }

  /**
   * Same basename for DV + BV: object only in HBV must resolve even when HDV is found first under
   * {@code ${PROJECT_HOME}/models/}.
   */
  @Test
  void sameBasenamePrefersBvWhenObjectOnlyInHbv() throws Exception {
    Path models = tempDir.resolve("models");
    Files.createDirectories(models);
    Path hdv = models.resolve("retail-360.hdv");
    Path hbv = models.resolve("retail-360.hbv");
    Files.writeString(hdv, hdvWithSatellite("retail-360", "sat_product"));
    Files.writeString(hbv, hbvWithBusinessTable("retail-360", "customer_360_bv"));

    Path consumer = tempDir.resolve("product.hbv");
    Files.writeString(consumer, "<!-- consumer -->");

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setFilename(consumer.toString());
    bvModel.setName("product");

    BvBusinessTable sqlTable = new BvBusinessTable();
    sqlTable.setName("customer_product");
    sqlTable.setTableName("customer_product");
    sqlTable.setSqlQuery("SELECT * FROM {{ ref('retail-360', 'customer_360_bv') }}");
    bvModel.getTables().add(sqlTable);

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toString());

    List<BvSqlRef> refs =
        BvSqlRefResolver.syncRefsFromSql(sqlTable, bvModel, new DataVaultModel(), variables, null);

    assertEquals(1, refs.size());
    BvSqlRef ref = refs.get(0);
    assertEquals(BvSqlResolvedKind.BV_TABLE, ref.getResolvedKind());
    assertEquals("customer_360_bv", ref.getResolvedTableName());
    assertNotNull(ref.getResolvedModelFilename());
    assertTrue(
        ref.getResolvedModelFilename().replace('\\', '/').contains("retail-360.hbv")
            || ref.getResolvedModelFilename().contains("retail-360"));
  }

  @Test
  void oneArgRefResolvesSiblingHbvTable() throws Exception {
    Path models = tempDir.resolve("models");
    Files.createDirectories(models);
    Files.writeString(
        models.resolve("staging.hbv"), hbvWithBusinessTable("staging", "stg_customers"));
    Path marts = models.resolve("marts.hbv");
    Files.writeString(marts, "<!-- consumer -->");

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setFilename(marts.toString());
    bvModel.setName("marts");

    BvBusinessTable sqlTable = new BvBusinessTable();
    sqlTable.setName("customers");
    sqlTable.setTableName("customers");
    sqlTable.setSqlQuery("SELECT * FROM {{ ref('stg_customers') }}");
    bvModel.getTables().add(sqlTable);

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toString());

    List<BvSqlRef> refs =
        BvSqlRefResolver.syncRefsFromSql(sqlTable, bvModel, new DataVaultModel(), variables, null);

    assertEquals(1, refs.size());
    assertEquals(BvSqlResolvedKind.BV_TABLE, refs.get(0).getResolvedKind());
    assertEquals("stg_customers", refs.get(0).getResolvedTableName());
    assertNotNull(refs.get(0).getResolvedModelFilename());
    assertTrue(
        refs.get(0).getResolvedModelFilename().replace('\\', '/').contains("staging.hbv")
            || refs.get(0).getResolvedModelFilename().contains("staging"));
  }

  @Test
  void sameBasenameStillResolvesDvObjectInHdv() throws Exception {
    Path models = tempDir.resolve("models");
    Files.createDirectories(models);
    Files.writeString(
        models.resolve("retail-360.hdv"), hdvWithSatellite("retail-360", "sat_product"));
    Files.writeString(
        models.resolve("retail-360.hbv"), hbvWithBusinessTable("retail-360", "customer_360_bv"));

    Path consumer = tempDir.resolve("product.hbv");
    Files.writeString(consumer, "<!-- consumer -->");

    BusinessVaultModel bvModel = new BusinessVaultModel();
    bvModel.setFilename(consumer.toString());

    BvBusinessTable sqlTable = new BvBusinessTable();
    sqlTable.setName("v1");
    sqlTable.setTableName("v1");
    sqlTable.setSqlQuery("SELECT * FROM {{ ref('retail-360', 'sat_product') }}");
    bvModel.getTables().add(sqlTable);

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", tempDir.toString());

    List<BvSqlRef> refs =
        BvSqlRefResolver.syncRefsFromSql(sqlTable, bvModel, new DataVaultModel(), variables, null);

    assertEquals(BvSqlResolvedKind.DV_TABLE, refs.get(0).getResolvedKind());
    assertEquals("sat_product", refs.get(0).getResolvedTableName());
  }

  private static String hdvWithSatellite(String modelName, String satName) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <data-vault-model>
          <name>%s</name>
          <configuration>
            <targetDatabase>Vault</targetDatabase>
          </configuration>
          <tables>
            <table>
              <name>%s</name>
              <tableName>%s</tableName>
              <tableType>SATELLITE</tableType>
            </table>
          </tables>
        </data-vault-model>
        """
        .formatted(modelName, satName, satName);
  }

  private static String hbvWithBusinessTable(String modelName, String tableName) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <business-vault-model>
          <name>%s</name>
          <configuration>
            <targetDatabase>Vault</targetDatabase>
          </configuration>
          <tables>
            <table>
              <name>%s</name>
              <tableName>%s</tableName>
              <tableType>BUSINESS_TABLE</tableType>
              <materialization>VIEW</materialization>
              <sqlQuery>SELECT 1 AS x</sqlQuery>
            </table>
          </tables>
        </business-vault-model>
        """
        .formatted(modelName, tableName, tableName);
  }
}
