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
package org.hopper.edw.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvIdentityMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvLookupIndexDdlTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void satelliteLookupIndexIsTheGrainAndIsSkippedWhenThePrimaryKeyCoversIt() throws Exception {
    DataVaultModel model = satelliteModel(false);
    DvSatellite satellite = (DvSatellite) model.findTable("sat_customer");
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    IRowMeta layout = satelliteLayout();
    Variables variables = new Variables();

    List<String> logical =
        DvConstraintDdlSupport.resolveDvLogicalKeyColumns(
            satellite, model, config, variables, layout);
    assertEquals(List.of("customer_hk", "phone_type", "LOAD_DATE"), logical);

    config.setUseLoadEndDate(true);
    assertEquals(
        logical,
        DvConstraintDdlSupport.resolveDvLogicalKeyColumns(
            satellite, model, config, variables, layout));

    IHopMetadataProvider metadataProvider = h2MetadataProvider("sat_idx_");
    DatabaseMeta databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load("Vault");
    try (Database database =
        new Database(
            new SimpleLoggingObject("DvLookupIndexDdlTest", LoggingObjectType.GENERAL, null),
            variables,
            databaseMeta)) {
      database.connect();
      List<String> withoutPrimaryKey = new ArrayList<>();
      withoutPrimaryKey.add("CREATE TABLE sat_customer (customer_hk VARCHAR(50));");
      DvIndexDdlSupport.appendDataVaultIndex(
          withoutPrimaryKey,
          database,
          databaseMeta,
          variables,
          "sat_customer",
          satellite,
          model,
          config,
          metadataProvider,
          layout);
      String sql = String.join("\n", withoutPrimaryKey).toLowerCase(Locale.ROOT);
      assertTrue(sql.contains("idx_sat_customer_lookup"), sql);
      assertTrue(sql.contains("customer_hk"), sql);
      assertTrue(sql.contains("phone_type"), sql);
      assertTrue(sql.contains("load_date"), sql);
      assertFalse(sql.contains("idx_sat_customer_hk"), sql);

      config.setGeneratePrimaryKeys(true);
      List<String> withPrimaryKey = new ArrayList<>();
      withPrimaryKey.add("CREATE TABLE sat_customer (customer_hk VARCHAR(50));");
      DvIndexDdlSupport.appendDataVaultIndex(
          withPrimaryKey,
          database,
          databaseMeta,
          variables,
          "sat_customer",
          satellite,
          model,
          config,
          metadataProvider,
          layout);
      assertEquals(1, withPrimaryKey.size());
    }
  }

  @Test
  void hubIndexesHashKeyOnlyWhenPrimaryKeyDdlIsOff() throws Exception {
    IHopMetadataProvider withoutPk = h2MetadataProvider("hub_nopk_");
    String open =
        join(hub("hub_customer").generateUpdateDdl(withoutPk, new Variables(), hubModel(false)));
    assertTrue(open.contains("idx_hub_customer_hk"), open);
    assertTrue(open.contains("customer_hk"), open);
    assertFalse(open.contains("primary key"), open);

    IHopMetadataProvider withPk = h2MetadataProvider("hub_pk_");
    String keyed =
        join(hub("hub_customer").generateUpdateDdl(withPk, new Variables(), hubModel(true)));
    assertFalse(keyed.contains("idx_hub_customer_hk"), keyed);
    assertTrue(keyed.contains("primary key"), keyed);
  }

  @Test
  void identityMapIndexesRawHashWhenPrimaryKeysAreOff() throws Exception {
    IHopMetadataProvider metadataProvider = h2MetadataProvider("idmap_");
    BusinessVaultModel model = new BusinessVaultModel();
    BusinessVaultConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");
    config.setGeneratePrimaryKeys(false);
    BvIdentityMap identityMap = new BvIdentityMap();
    identityMap.setName("bv_customer_identity");
    identityMap.setTableName("bv_customer_identity");
    model.getTables().add(identityMap);

    String sql =
        join(
            identityMap.generateBuildDdl(
                metadataProvider, new Variables(), model, new DataVaultModel()));

    assertTrue(sql.contains("create table"), sql);
    assertTrue(sql.contains("idx_bv_customer_identity_hk"), sql);
    assertTrue(sql.contains("hk_raw"), sql);
  }

  @Test
  void snowflakeSkipsSecondaryIndexes() throws HopException {
    DatabaseMeta snowflake =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID;
          }
        };
    assertFalse(DvIndexDdlSupport.supportsSecondaryIndexes(snowflake));

    List<String> result = new ArrayList<>();
    DvIndexDdlSupport.appendIndex(
        result,
        null,
        snowflake,
        new Variables(),
        null,
        "d_date",
        DvIndexDdlSupport.SUFFIX_NATURAL_KEY,
        List.of("date_key"),
        List.of(),
        null);
    assertTrue(result.isEmpty());
  }

  @Test
  void longIndexNamesStayWithinTheEngineLimitAndDoNotCollide() {
    DatabaseMeta postgres =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID;
          }
        };
    String left = "sat_" + "a".repeat(80);
    String right = "sat_" + "b".repeat(80);
    String leftName = DvIndexDdlSupport.indexName(left, DvIndexDdlSupport.SUFFIX_LOOKUP, postgres);
    String rightName =
        DvIndexDdlSupport.indexName(right, DvIndexDdlSupport.SUFFIX_LOOKUP, postgres);

    assertTrue(leftName.length() <= DvIdentifierLimitSupport.POSTGRES_MAX, leftName);
    assertTrue(rightName.length() <= DvIdentifierLimitSupport.POSTGRES_MAX, rightName);
    assertFalse(leftName.equals(rightName));
  }

  private static DataVaultModel satelliteModel(boolean generatePrimaryKeys) {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    config.setGeneratePrimaryKeys(generatePrimaryKeys);
    config.setLoadDateField("LOAD_DATE");
    DvHub hub = new DvHub();
    hub.setName("h_customer");
    hub.setHashKeyFieldName("customer_hk");
    model.getTables().add(hub);
    DvSatellite satellite = new DvSatellite();
    satellite.setName("sat_customer");
    satellite.setTableName("sat_customer");
    satellite.setHubName("h_customer");
    satellite.setDrivingKey("phone_type");
    satellite.setDrivingKeySourceField("phone_type");
    model.getTables().add(satellite);
    return model;
  }

  private static DataVaultModel hubModel(boolean generatePrimaryKeys) {
    DataVaultModel model = new DataVaultModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    model.getConfigurationOrDefault().setGeneratePrimaryKeys(generatePrimaryKeys);
    model.getTables().add(hub("hub_customer"));
    return model;
  }

  private static DvHub hub(String tableName) {
    DvHub hub = new DvHub();
    hub.setName(tableName);
    hub.setTableName(tableName);
    hub.setHashKeyFieldName("customer_hk");
    BusinessKey businessKey = new BusinessKey("customer_id");
    businessKey.setDataType("String");
    businessKey.setLength("20");
    hub.getBusinessKeys().add(businessKey);
    return hub;
  }

  private static IRowMeta satelliteLayout() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("customer_hk"));
    rowMeta.addValueMeta(new ValueMetaString("phone_type"));
    rowMeta.addValueMeta(new ValueMetaString("phone_number"));
    rowMeta.addValueMeta(new ValueMetaTimestamp("LOAD_DATE"));
    return rowMeta;
  }

  private static String join(List<String> ddl) {
    return String.join("\n", ddl).toLowerCase(Locale.ROOT);
  }

  private static IHopMetadataProvider h2MetadataProvider(String prefix) throws HopException {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("Vault");
    databaseMeta.setDatabaseType("H2");
    databaseMeta.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    databaseMeta.setDBName("mem:" + prefix + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    databaseMeta.setHostname("");
    databaseMeta.setPort("");
    databaseMeta.setUsername("sa");
    databaseMeta.setPassword("");
    provider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return provider;
  }
}
