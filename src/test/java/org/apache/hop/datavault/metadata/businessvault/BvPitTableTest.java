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
package org.apache.hop.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvPitTableTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void targetLayoutIncludesHubSnapshotAndSatellitePointerColumns() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    BvPitTable table = validPitTable();

    var layout =
        table.getTargetTableLayout(null, new Variables(), new BusinessVaultModel(), dvModel);

    assertEquals(3, layout.size());
    assertEquals("customer_hk", layout.getValueMeta(0).getName());
    assertEquals("snapshot_date", layout.getValueMeta(1).getName());
    assertTrue(layout.getValueMeta(1) instanceof ValueMetaTimestamp);
    assertEquals("sat_customer_ldts", layout.getValueMeta(2).getName());
    assertTrue(layout.getValueMeta(2) instanceof ValueMetaTimestamp);
  }

  @Test
  void rejectsMultipleHubDerivatives() throws Exception {
    BvPitTable table = validPitTable();
    table.getDerivatives().add(new BvDerivativeRef("hub_product", DvTableType.HUB));

    List<ICheckResult> remarks = check(table, loadVault1Model());

    assertTrue(hasError(remarks, "exactly one Data Vault hub"));
  }

  @Test
  void rejectsSatelliteFromDifferentHub() throws Exception {
    BvPitTable table = validPitTable();
    table.getDerivatives().clear();
    table.getDerivatives().add(new BvDerivativeRef("hub_customer", DvTableType.HUB));
    table.getDerivatives().add(new BvDerivativeRef("sat_product", DvTableType.SATELLITE));

    List<ICheckResult> remarks = check(table, loadVault1Model());

    assertTrue(hasError(remarks, "does not belong to hub"));
  }

  @Test
  void warnsWhenCadenceIsNotDaily() throws Exception {
    BvPitTable table = validPitTable();
    table.getSnapshotScheduleOrDefault().setCadence(BvPitCadence.WEEKLY);

    List<ICheckResult> remarks = check(table, loadVault1Model());

    assertTrue(hasWarning(remarks, "only DAILY snapshots are implemented"));
  }

  @Test
  void requiresFixedRangeStartDate() throws Exception {
    BvPitTable table = validPitTable();
    table.getSnapshotScheduleOrDefault().setRangeStart(BvPitRangeStart.FIXED_DATE);

    List<ICheckResult> remarks = check(table, loadVault1Model());

    assertTrue(hasError(remarks, "fixed range start date"));
  }

  @Test
  void xmlRoundTripPreservesSnapshotSchedule() throws Exception {
    BvPitTable original = validPitTable();
    original.getSnapshotScheduleOrDefault().setHorizonDays(3);
    original.getSnapshotScheduleOrDefault().setCadence(BvPitCadence.DAILY);
    original.getSnapshotScheduleOrDefault().setRangeEnd(BvPitRangeEnd.NOW_MINUS_HORIZON);

    String xml = XmlHandler.aroundTag("table", XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "table");

    BvPitTable restored = new BvPitTable();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BvPitTable.class, restored, null);

    assertEquals(3, restored.getSnapshotScheduleOrDefault().getHorizonDays());
    assertEquals(BvPitCadence.DAILY, restored.getSnapshotScheduleOrDefault().getCadence());
    assertEquals(
        BvPitRangeEnd.NOW_MINUS_HORIZON, restored.getSnapshotScheduleOrDefault().getRangeEnd());
    assertEquals("snapshot_date", restored.getSnapshotDateField());
  }

  @Test
  void buildPitPipelineNameUsesConfiguredPrefix() {
    BusinessVaultConfiguration config = new BusinessVaultConfiguration();
    config.setPitPipelineNamePrefix("pit-build-");

    assertEquals(
        "pit-build-pit_customer", config.buildPitPipelineName(new Variables(), "pit_customer"));
  }

  @Test
  void validPitTablePassesDerivativeValidation() throws Exception {
    List<ICheckResult> remarks = check(validPitTable(), loadVault1Model());

    assertFalse(hasError(remarks, "exactly one Data Vault hub"));
    assertFalse(hasError(remarks, "does not belong to hub"));
    assertFalse(hasError(remarks, "fixed range start date"));
  }

  private static BvPitTable validPitTable() {
    BvPitTable table = new BvPitTable();
    table.setName("pit_customer");
    table.setTableName("pit_customer");
    table.getDerivatives().add(new BvDerivativeRef("hub_customer", DvTableType.HUB));
    table.getDerivatives().add(new BvDerivativeRef("sat_customer", DvTableType.SATELLITE));
    return table;
  }

  private static List<ICheckResult> check(BvPitTable table, DataVaultModel dvModel) {
    List<ICheckResult> remarks = new ArrayList<>();
    BusinessVaultModel bvModel = new BusinessVaultModel();
    table.check(remarks, null, new Variables(), bvModel, dvModel);
    return remarks;
  }

  private static boolean hasError(List<ICheckResult> remarks, String fragment) {
    return remarks.stream()
        .anyMatch(
            remark ->
                remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                    && remark.getText() != null
                    && remark.getText().contains(fragment));
  }

  private static boolean hasWarning(List<ICheckResult> remarks, String fragment) {
    return remarks.stream()
        .anyMatch(
            remark ->
                remark.getType() == ICheckResult.TYPE_RESULT_WARNING
                    && remark.getText() != null
                    && remark.getText().contains(fragment));
  }

  private static DataVaultModel loadVault1Model() throws Exception {
    Path dvPath = Path.of("integration-tests/tests/basic/vault1.hdv").toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(dvPath.toFile());
    Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    return model;
  }
}
