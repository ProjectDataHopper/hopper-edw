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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvSourceCalendarTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void xmlRoundTripPreservesWindowsAndLegBinding() throws Exception {
    BvSourceCalendar calendar = calendar("person_sources");
    BvSourceCalendarEntry oracle = window("ORACLE_A", "1900-01-01", "2024-03-01", "10", "full");
    BvSourceCalendarEntry kafka = window("KAFKA_B", "2024-03-01", "", "20", "delta");
    calendar.getEntries().add(oracle);
    calendar.getEntries().add(kafka);

    BvSourceCalendar restored = roundTrip(calendar);
    assertEquals(BvTableType.SOURCE_CALENDAR, restored.getTableType());
    assertEquals("person_sources", restored.getName());
    assertEquals(2, restored.getEntries().size());
    assertEquals("ORACLE_A", restored.getEntries().get(0).getSourceId());
    assertEquals("2024-03-01", restored.getEntries().get(0).getEffectiveTo());
    assertEquals(BvSourceCalendarMode.FULL, restored.getEntries().get(0).getMode());
    assertEquals(BvSourceCalendarMode.DELTA, restored.getEntries().get(1).getMode());
    assertEquals("", restored.getTableName() == null ? "" : restored.getTableName());

    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("customer_360");
    scd2.setTableName("customer_360");
    scd2.setSourceCalendarName("person_sources");
    scd2.getDerivatives().add(new BvDerivativeRef("sat_person_ora", DvTableType.SATELLITE));
    BvScd2SatelliteConfig config = new BvScd2SatelliteConfig("sat_person_ora");
    config.setSourceId("ORACLE_A");
    config.setOp(BvLegOperation.UPSERT);
    config.setNullPolicyDefault(BvNullPolicy.APPLY);
    config.setPriorityOverride("11");
    scd2.getSatelliteConfigs().add(config);
    BvSourceQueryRef seed = new BvSourceQueryRef("sas_person_seed");
    seed.setSourceId("SAS_SEED");
    seed.setOp(BvLegOperation.SEED);
    seed.setNullPolicyDefault(BvNullPolicy.INHERIT);
    scd2.getSourceQueryRefs().add(seed);

    String xml = XmlHandler.aroundTag("table", XmlMetadataUtil.serializeObjectToXml(scd2));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "table");
    BvScd2Table restoredScd2 = new BvScd2Table();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BvScd2Table.class, restoredScd2, null);

    assertEquals("person_sources", restoredScd2.getSourceCalendarName());
    assertEquals("ORACLE_A", restoredScd2.getSatelliteConfigs().get(0).getSourceId());
    assertEquals(BvLegOperation.UPSERT, restoredScd2.getSatelliteConfigs().get(0).getOp());
    assertEquals(BvNullPolicy.APPLY, restoredScd2.getSatelliteConfigs().get(0).getNullPolicyDefault());
    assertEquals("11", restoredScd2.getSatelliteConfigs().get(0).getPriorityOverride());
    assertEquals(BvLegOperation.SEED, restoredScd2.getSourceQueryRefs().get(0).getOp());
    assertEquals("SAS_SEED", restoredScd2.getSourceQueryRefs().get(0).getSourceId());
  }

  @Test
  void touchingWindowsAreNotOverlapsAndInvertedWindowsFail() {
    BvSourceCalendar calendar = calendar("person_sources");
    calendar.getEntries().add(window("ORACLE_A", "1900-01-01", "2024-03-01", "10", "full"));
    calendar.getEntries().add(window("ORACLE_A", "2024-03-01", "9999-12-31", "10", "full"));

    List<ICheckResult> remarks = new ArrayList<>();
    calendar.check(remarks, null, new Variables(), model(calendar), null);
    assertFalse(hasError(remarks));

    calendar.getEntries().clear();
    calendar.getEntries().add(window("ORACLE_A", "2024-03-01", "2024-01-01", "", "full"));
    remarks.clear();
    calendar.check(remarks, null, new Variables(), model(calendar), null);
    assertTrue(hasError(remarks));

    calendar.getEntries().clear();
    calendar.getEntries().add(window("ORACLE_A", "2020-01-01", "2024-01-01", "", "full"));
    calendar.getEntries().add(window("ORACLE_A", "2023-01-01", "2025-01-01", "", "full"));
    remarks.clear();
    calendar.check(remarks, null, new Variables(), model(calendar), null);
    assertTrue(hasError(remarks));
  }

  @Test
  void scd2WithoutCalendarStaysValidAndNamedCalendarRequiresKnownSourceIds() {
    BvScd2Table plain = scd2("customer_360");
    List<ICheckResult> remarks = new ArrayList<>();
    BvSourceCalendarSupport.validateScd2(remarks, plain, new BusinessVaultModel(), new Variables());
    assertFalse(hasError(remarks));

    BvSourceCalendar calendar = calendar("person_sources");
    calendar.getEntries().add(window("ORACLE_A", "1900-01-01", "2024-03-01", "", "full"));
    BusinessVaultModel model = model(calendar);

    BvScd2Table missing = scd2("customer_360");
    missing.setSourceCalendarName("missing_calendar");
    remarks.clear();
    BvSourceCalendarSupport.validateScd2(remarks, missing, model, new Variables());
    assertTrue(hasError(remarks));

    BvScd2Table unbound = scd2("customer_360");
    unbound.setSourceCalendarName("person_sources");
    remarks.clear();
    BvSourceCalendarSupport.validateScd2(remarks, unbound, model, new Variables());
    assertTrue(hasError(remarks));
    assertTrue(
        remarks.stream()
            .anyMatch(
                remark ->
                    remark.getText() != null && remark.getText().contains("Satellite settings")));

    BvScd2Table unknownSource = scd2("customer_360");
    unknownSource.setSourceCalendarName("person_sources");
    BvScd2SatelliteConfig config = new BvScd2SatelliteConfig("sat_person_ora");
    config.setSourceId("NO_SUCH");
    unknownSource.getSatelliteConfigs().add(config);
    remarks.clear();
    BvSourceCalendarSupport.validateScd2(remarks, unknownSource, model, new Variables());
    assertTrue(hasError(remarks));

    BvScd2Table dangling = scd2("customer_360");
    BvScd2SatelliteConfig danglingConfig = new BvScd2SatelliteConfig("sat_person_ora");
    danglingConfig.setSourceId("ORACLE_A");
    dangling.getSatelliteConfigs().add(danglingConfig);
    remarks.clear();
    BvSourceCalendarSupport.validateScd2(remarks, dangling, model, new Variables());
    assertTrue(hasError(remarks));

    BvScd2Table seedOnSatellite = scd2("customer_360");
    seedOnSatellite.setSourceCalendarName("person_sources");
    BvScd2SatelliteConfig seedConfig = new BvScd2SatelliteConfig("sat_person_ora");
    seedConfig.setSourceId("ORACLE_A");
    seedConfig.setOp(BvLegOperation.SEED);
    seedOnSatellite.getSatelliteConfigs().add(seedConfig);
    remarks.clear();
    BvSourceCalendarSupport.validateScd2(remarks, seedOnSatellite, model, new Variables());
    assertTrue(hasError(remarks));

    BvScd2Table ready = scd2("customer_360");
    ready.setSourceCalendarName("person_sources");
    BvScd2SatelliteConfig readyConfig = new BvScd2SatelliteConfig("sat_person_ora");
    readyConfig.setSourceId("ORACLE_A");
    ready.getSatelliteConfigs().add(readyConfig);
    remarks.clear();
    BvSourceCalendarSupport.validateScd2(remarks, ready, model, new Variables());
    assertFalse(hasError(remarks));
  }

  @Test
  void tableInputSqlIncludesHalfOpenWindowAndSkipsUnconfiguredTables() throws Exception {
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");
    DvSatellite satellite = new DvSatellite("sat_person_ora");
    BvScd2Table scd2 = scd2("customer_360");
    BvScd2SatelliteConfig config = new BvScd2SatelliteConfig("sat_person_ora");
    config.setSourceId("ORACLE_A");
    scd2.getSatelliteConfigs().add(config);

    BvSourceCalendar calendar = calendar("person_sources");
    calendar.getEntries().add(window("ORACLE_A", "1900-01-01", "2024-03-01", "10", "full"));
    calendar.getEntries().add(window("KAFKA_B", "2024-03-01", "", "20", "delta"));
    BusinessVaultModel model = model(calendar);
    model.getTables().add(scd2);
    scd2.setSourceCalendarName("person_sources");

    BvScd2PipelineSupport.Scd2BuildContext withCalendar =
        context(scd2, satellite, model, databaseMeta);
    String filtered =
        BvScd2PipelineSupport.buildLegTableInputSql(withCalendar, withCalendar.legs.get(0));
    assertTrue(filtered.contains("x_load_ts >= '1900-01-01 00:00:00'"));
    assertTrue(filtered.contains("x_load_ts < '2024-03-01 00:00:00'"));
    assertFalse(filtered.contains("KAFKA_B"));
    assertFalse(filtered.contains("2024-03-01 00:00:00' AND"));

    scd2.setBuildMode(BvScd2BuildMode.INCREMENTAL);
    BvScd2PipelineSupport.Scd2BuildContext incremental =
        context(scd2, satellite, model, databaseMeta);
    String delta = BvScd2PipelineSupport.buildDeltaHashKeysSubquerySql(incremental);
    assertTrue(delta.contains("x_load_ts > ?"));
    assertTrue(delta.contains("x_load_ts >= '1900-01-01 00:00:00'"));

    scd2.setBuildMode(BvScd2BuildMode.FULL_REBUILD);
    scd2.setSourceCalendarName(null);
    BvScd2PipelineSupport.Scd2BuildContext plain = context(scd2, satellite, model, databaseMeta);
    String unfiltered = BvScd2PipelineSupport.buildLegTableInputSql(plain, plain.legs.get(0));
    assertFalse(unfiltered.contains("1900-01-01"));
    assertFalse(unfiltered.toUpperCase().contains("WHERE"));

    config.setSourceId("MISSING");
    scd2.setSourceCalendarName("person_sources");
    BvScd2PipelineSupport.Scd2BuildContext blocked = context(scd2, satellite, model, databaseMeta);
    String blockedSql = BvScd2PipelineSupport.buildLegTableInputSql(blocked, blocked.legs.get(0));
    assertTrue(blockedSql.contains("1 = 0"));

    calendar.getEntries().clear();
    calendar.getEntries().add(window("ORACLE_A", "2010-01-01", "", "", "full"));
    config.setSourceId("ORACLE_A");
    BvScd2PipelineSupport.Scd2BuildContext openEnded =
        context(scd2, satellite, model, databaseMeta);
    String openSql =
        BvScd2PipelineSupport.buildLegTableInputSql(openEnded, openEnded.legs.get(0));
    assertTrue(openSql.contains("x_load_ts >= '2010-01-01 00:00:00'"));
    assertFalse(openSql.contains("x_load_ts <"));
  }

  @Test
  void warehouseDialectsQuoteTheSameIsoTimestampLiteral() throws Exception {
    Date bound = BvSourceCalendarSupport.parseTimestamp("2024-03-01", new Variables());
    for (String pluginId : List.of("POSTGRESQL", "MYSQL")) {
      DatabaseMeta databaseMeta = new DatabaseMeta();
      databaseMeta.setName("Vault");
      databaseMeta.setDatabaseType(pluginId);
      assertEquals(
          "'2024-03-01 00:00:00'",
          BvSourceCalendarSqlSupport.timestampLiteral(databaseMeta, bound),
          pluginId);
    }
    assertNull(BvSourceCalendarSupport.parseTimestamp("March 1", new Variables()));
  }

  private static BvScd2PipelineSupport.Scd2BuildContext context(
      BvScd2Table scd2,
      DvSatellite satellite,
      BusinessVaultModel model,
      DatabaseMeta databaseMeta) {
    return new BvScd2PipelineSupport.Scd2BuildContext(
        scd2,
        List.of(
            new BvScd2PipelineSupport.SatelliteLeg(
                satellite, "sat_person_ora", "ORACLE_A", "x_load_ts", List.of())),
        false,
        List.of(),
        model,
        null,
        model.getConfigurationOrDefault(),
        null,
        null,
        new Variables(),
        databaseMeta,
        "Vault",
        databaseMeta,
        "Vault",
        "sat_person_ora",
        "customer_360",
        "bv-scd2-customer_360",
        "person_hk",
        null,
        List.of(),
        "x_load_ts",
        "valid_from",
        "valid_to",
        "x_record_source",
        BusinessVaultConfiguration.DEFAULT_OPEN_START_SENTINEL,
        BusinessVaultConfiguration.DEFAULT_OPEN_END_SENTINEL,
        true);
  }

  private static BvSourceCalendar roundTrip(BvSourceCalendar original) throws Exception {
    String xml = XmlHandler.aroundTag("table", XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "table");
    BvSourceCalendar restored = new BvSourceCalendar();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BvSourceCalendar.class, restored, null);
    return restored;
  }

  private static BvSourceCalendar calendar(String name) {
    BvSourceCalendar calendar = new BvSourceCalendar();
    calendar.setName(name);
    calendar.setDescription("Person cutover");
    return calendar;
  }

  private static BvSourceCalendarEntry window(
      String sourceId, String from, String to, String priority, String mode) {
    BvSourceCalendarEntry entry = new BvSourceCalendarEntry();
    entry.setSourceId(sourceId);
    entry.setEffectiveFrom(from);
    entry.setEffectiveTo(to);
    entry.setPriority(priority);
    entry.setMode(BvSourceCalendarMode.lookupCode(mode));
    return entry;
  }

  private static BvScd2Table scd2(String name) {
    BvScd2Table table = new BvScd2Table();
    table.setName(name);
    table.setTableName(name);
    table.getDerivatives().add(new BvDerivativeRef("sat_person_ora", DvTableType.SATELLITE));
    return table;
  }

  private static BusinessVaultModel model(BvSourceCalendar calendar) {
    BusinessVaultModel model = new BusinessVaultModel();
    model.getTables().add(calendar);
    return model;
  }

  private static boolean hasError(List<ICheckResult> remarks) {
    for (ICheckResult remark : remarks) {
      if (remark.getType() == ICheckResult.TYPE_RESULT_ERROR) {
        return true;
      }
    }
    return false;
  }
}
