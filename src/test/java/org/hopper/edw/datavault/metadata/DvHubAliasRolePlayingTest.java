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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Hub role-playing: natural physical hub plus one alias for a second role on the same link (issue
 * #103).
 */
class DvHubAliasRolePlayingTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void createAliasDerivesRoleHashKeyFromName() {
    DvHub hub = new DvHub("hub_sales_rep");
    hub.setTableName("hub_sales_rep");
    hub.setHashKeyFieldName("sales_rep_hk");

    DvLinkedTable alias =
        DvLinkedTableSupport.createAlias("hub_secondary_rep", hub, null, null, new Point(10, 10));

    assertNotNull(alias);
    assertEquals("hub_secondary_rep", alias.getName());
    assertEquals("hub_sales_rep", alias.getReferencedTableName());
    assertTrue(Utils.isEmpty(alias.getReferencedModelFilename()));
    assertEquals(DvTableType.HUB, alias.getReferencedTableType());
    assertEquals("secondary_rep_hk", alias.getHashKeyFieldName());
    assertEquals("hub_sales_rep", alias.getTableName());
  }

  @Test
  void listAvailableAllowsSameModelHubsWhenFlagSet() {
    DataVaultModel model = buildSalesRepModel();
    List<String> forCrossModel =
        DvLinkedTableSupport.listAvailableTableNames(model, model, DvTableType.HUB, false);
    List<String> forAlias =
        DvLinkedTableSupport.listAvailableTableNames(model, model, DvTableType.HUB, true);

    // Physical hub already on canvas: classic cross-model picker excludes it.
    assertFalse(forCrossModel.contains("hub_sales_rep"));
    // Same-model alias picker still lists it.
    assertTrue(forAlias.contains("hub_sales_rep"));
    assertTrue(forAlias.contains("hub_order"));
  }

  @Test
  void resolveParticipatingHubHashColumnUsesRoleField() {
    DataVaultModel model = buildSalesRepModel();
    Variables variables = new Variables();

    assertEquals(
        "sales_rep_hk",
        DvTableResolutionSupport.resolveParticipatingHubHashColumn(
            model, "hub_sales_rep", variables, null));
    assertEquals(
        "secondary_rep_hk",
        DvTableResolutionSupport.resolveParticipatingHubHashColumn(
            model, "hub_secondary_rep", variables, null));
    assertEquals(
        "order_hk",
        DvTableResolutionSupport.resolveParticipatingHubHashColumn(
            model, "hub_order", variables, null));
  }

  @Test
  void resolveHubThroughSameModelAlias() {
    DataVaultModel model = buildSalesRepModel();
    DvHub physical =
        DvTableResolutionSupport.resolveHub(model, "hub_secondary_rep", new Variables(), null);
    assertNotNull(physical);
    assertEquals("hub_sales_rep", physical.getName());
    assertEquals("sales_rep_hk", physical.getHashKeyFieldName());
  }

  @Test
  void linkLayoutHasNaturalHubAndAliasColumns() throws Exception {
    DataVaultModel model = buildSalesRepModel();
    DvLink link = (DvLink) model.findTable("lnk_order_rep");
    assertNotNull(link);

    IRowMeta layout =
        link.getTargetTableLayout(new MemoryMetadataProvider(), new Variables(), model);
    assertNotNull(layout);
    assertNotNull(layout.searchValueMeta("lnk_order_rep_hk"));
    assertNotNull(layout.searchValueMeta("order_hk"));
    // Natural hub participation keeps the physical hub hash column name.
    assertNotNull(layout.searchValueMeta("sales_rep_hk"));
    // Extra role uses the alias role column.
    assertNotNull(layout.searchValueMeta("secondary_rep_hk"));
  }

  @Test
  void linkCheckFailsOnDuplicateRoleHashColumns() {
    DataVaultModel model = buildSalesRepModel();
    // Break secondary alias so it collides with the natural hub hash column.
    DvLinkedTable secondary = (DvLinkedTable) model.findTable("hub_secondary_rep");
    secondary.setHashKeyFieldName("sales_rep_hk");

    DvLink link = (DvLink) model.findTable("lnk_order_rep");
    List<ICheckResult> remarks = new ArrayList<>();
    link.check(remarks, null, new Variables(), DvModelCheckOptions.defaults(), model);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("sales_rep_hk")));
  }

  @Test
  void sameModelAliasValidationPasses() {
    DataVaultModel model = buildSalesRepModel();
    DvLinkedTable secondary = (DvLinkedTable) model.findTable("hub_secondary_rep");
    List<ICheckResult> remarks = new ArrayList<>();
    DvLinkedTableValidationSupport.validateLinkedTable(
        remarks, secondary, model, null, new Variables());
    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> remarks.toString());
  }

  @Test
  void deriveRoleHashKeyFieldName() {
    assertEquals(
        "secondary_rep_hk",
        DvTableResolutionSupport.deriveRoleHashKeyFieldName("hub_secondary_rep"));
    assertEquals(
        "to_party_hk", DvTableResolutionSupport.deriveRoleHashKeyFieldName("hub_to_party"));
    assertEquals(
        "from_location_hk", DvTableResolutionSupport.deriveRoleHashKeyFieldName("from_location"));
  }

  /** Natural hub + one alias for the second sales-rep role. */
  private static DataVaultModel buildSalesRepModel() {
    DataVaultModel model = new DataVaultModel();
    model.setName("hub-alias-role-playing");

    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setHashAlgorithm("MD5");
    config.setHashKeyDataType("STRING");
    config.setLoadDateField("load_dts");
    config.setRecordSourceField("record_source");
    model.setConfiguration(config);

    DvHub salesRep = new DvHub("hub_sales_rep");
    salesRep.setTableName("hub_sales_rep");
    salesRep.setHashKeyFieldName("sales_rep_hk");
    BusinessKey repBk = new BusinessKey();
    repBk.setName("rep_id");
    salesRep.getBusinessKeys().add(repBk);
    model.getTables().add(salesRep);

    DvHub order = new DvHub("hub_order");
    order.setTableName("hub_order");
    order.setHashKeyFieldName("order_hk");
    BusinessKey orderBk = new BusinessKey();
    orderBk.setName("order_id");
    order.getBusinessKeys().add(orderBk);
    model.getTables().add(order);

    DvLinkedTable secondary =
        DvLinkedTableSupport.createAlias(
            "hub_secondary_rep", salesRep, null, "secondary_rep_hk", new Point(100, 200));
    model.getTables().add(secondary);

    DvLink link = new DvLink("lnk_order_rep");
    link.setTableName("lnk_order_rep");
    link.setLinkHashKeyFieldName("lnk_order_rep_hk");
    link.getHubNames().add("hub_order");
    link.getHubNames().add("hub_sales_rep");
    link.getHubNames().add("hub_secondary_rep");
    model.getTables().add(link);

    return model;
  }
}
