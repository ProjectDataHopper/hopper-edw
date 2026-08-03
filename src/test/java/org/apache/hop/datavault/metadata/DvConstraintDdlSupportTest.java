/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvConstraintDdlSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void defaultsOffProduceNoPrimaryKeyColumns() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = new DataVaultConfiguration();
    DvHub hub = new DvHub();
    hub.setName("h_customer");
    hub.setHashKeyFieldName("customer_hk");

    List<String> pk =
        DvConstraintDdlSupport.resolveDvPrimaryKeyColumns(
            hub, model, config, new Variables(), null, null, false);

    assertTrue(pk.isEmpty());
  }

  @Test
  void hubPrimaryKeyIsHashKeyWhenEnabled() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setGeneratePrimaryKeys(true);
    DvHub hub = new DvHub();
    hub.setName("h_customer");
    hub.setHashKeyFieldName("customer_hk");

    List<String> pk =
        DvConstraintDdlSupport.resolveDvPrimaryKeyColumns(
            hub, model, config, new Variables(), null, null, false);

    assertEquals(List.of("customer_hk"), pk);
  }

  @Test
  void referencePrimaryKeyIsNaturalKeysWhenEnabled() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setGeneratePrimaryKeys(true);
    DvReferenceTable ref = new DvReferenceTable("ref_country");
    BusinessKey code = new BusinessKey("code");
    BusinessKey cdc = new BusinessKey("x_src_cdc_ts");
    ref.setNaturalKeys(List.of(code, cdc));

    List<String> pk =
        DvConstraintDdlSupport.resolveDvPrimaryKeyColumns(
            ref, model, config, new Variables(), null, null, false);

    assertEquals(List.of("code", "x_src_cdc_ts"), pk);
  }

  @Test
  void foreignKeyFlagForcesParentPrimaryKeyWithoutChildPk() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setGenerateForeignKeys(true);
    // PK flag remains false

    DvHub hub = new DvHub();
    hub.setName("h_customer");
    hub.setHashKeyFieldName("customer_hk");

    DvSatellite sat = new DvSatellite();
    sat.setName("sat_customer");
    sat.setHubName("h_customer");

    List<String> hubPk =
        DvConstraintDdlSupport.resolveDvPrimaryKeyColumns(
            hub, model, config, new Variables(), null, null, false);
    List<String> satPk =
        DvConstraintDdlSupport.resolveDvPrimaryKeyColumns(
            sat, model, config, new Variables(), null, satLayout(), false);

    assertEquals(List.of("customer_hk"), hubPk);
    assertTrue(satPk.isEmpty());
  }

  @Test
  void satellitePrimaryKeyIncludesDrivingKeyAndLoadDate() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setGeneratePrimaryKeys(true);
    config.setLoadDateField("LOAD_DATE");

    DvHub hub = new DvHub();
    hub.setName("h_customer");
    hub.setHashKeyFieldName("customer_hk");
    model.getTables().add(hub);

    DvSatellite sat = new DvSatellite();
    sat.setName("sat_customer_phone");
    sat.setHubName("h_customer");
    sat.setDrivingKey("phone_type");
    sat.setDrivingKeySourceField("phone_type");

    List<String> pk =
        DvConstraintDdlSupport.resolveDvPrimaryKeyColumns(
            sat, model, config, new Variables(), null, satLayout(), false);

    assertEquals(List.of("customer_hk", "phone_type", "LOAD_DATE"), pk);
  }

  @Test
  void linkForeignKeysReferenceHubsWhenEnabled() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setGenerateForeignKeys(true);

    DvHub hub = new DvHub();
    hub.setName("h_customer");
    hub.setTableName("hub_customer");
    hub.setHashKeyFieldName("customer_hk");
    model.getTables().add(hub);

    DvLink link = new DvLink();
    link.setName("l_order");
    link.setTableName("link_order");
    link.setLinkHashKeyFieldName("order_lk");
    link.getHubNames().add("h_customer");

    DatabaseMeta postgres =
        databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);
    List<ForeignKeySpec> fks =
        DvConstraintDdlSupport.resolveDvForeignKeys(
            link, model, config, postgres, new Variables(), null, false);

    assertEquals(1, fks.size());
    assertEquals(List.of("customer_hk"), fks.get(0).getChildColumns());
    assertEquals("hub_customer", fks.get(0).getParentTableName());
  }

  @Test
  void foreignKeysSkippedOnSingleStoreEvenWhenEnabled() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setGenerateForeignKeys(true);

    DvHub hub = new DvHub();
    hub.setName("h_customer");
    hub.setHashKeyFieldName("customer_hk");
    model.getTables().add(hub);

    DvLink link = new DvLink();
    link.setName("l_order");
    link.getHubNames().add("h_customer");

    DatabaseMeta singleStore =
        new DatabaseMeta(
            "ss", "SingleStore (MemSQL)", "Native", "", "localhost", "test", "root", "");
    List<ForeignKeySpec> fks =
        DvConstraintDdlSupport.resolveDvForeignKeys(
            link, model, config, singleStore, new Variables(), null, false);

    assertTrue(fks.isEmpty());
  }

  @Test
  void wantsPrimaryKeyDdlLogic() {
    assertFalse(DvConstraintDdlSupport.wantsPrimaryKeyDdl(false, false, true));
    assertTrue(DvConstraintDdlSupport.wantsPrimaryKeyDdl(true, false, false));
    assertTrue(DvConstraintDdlSupport.wantsPrimaryKeyDdl(false, true, true));
    assertFalse(DvConstraintDdlSupport.wantsPrimaryKeyDdl(false, true, false));
  }

  private static IRowMeta satLayout() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("customer_hk"));
    rowMeta.addValueMeta(new ValueMetaString("phone_type"));
    rowMeta.addValueMeta(new ValueMetaString("phone_number"));
    rowMeta.addValueMeta(new ValueMetaTimestamp("LOAD_DATE"));
    return rowMeta;
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return pluginId;
      }
    };
  }
}
