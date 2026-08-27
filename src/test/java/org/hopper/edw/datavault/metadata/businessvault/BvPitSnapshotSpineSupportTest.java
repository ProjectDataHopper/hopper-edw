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

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class BvPitSnapshotSpineSupportTest {

  private static final LocalDate REFERENCE_DATE = LocalDate.of(2024, 1, 10);

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveBoundsAppliesHorizonToEndDate() throws Exception {
    BvPitSnapshotSchedule schedule = defaultSchedule();
    schedule.setHorizonDays(2);

    var bounds =
        BvPitSnapshotSpineSupport.resolveBounds(
            schedule,
            new Variables(),
            REFERENCE_DATE,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 2));

    assertEquals(LocalDate.of(2024, 1, 1), bounds.startDate());
    assertEquals(LocalDate.of(2024, 1, 8), bounds.endDate());
    assertTrue(bounds.isValid());
  }

  @Test
  void resolveBoundsUsesFixedDatesWhenConfigured() throws Exception {
    BvPitSnapshotSchedule schedule = defaultSchedule();
    schedule.setRangeStart(BvPitRangeStart.FIXED_DATE);
    schedule.setRangeStartFixed("2024-01-03");
    schedule.setRangeEnd(BvPitRangeEnd.FIXED_DATE);
    schedule.setRangeEndFixed("2024-01-05 00:00:00");

    var bounds =
        BvPitSnapshotSpineSupport.resolveBounds(
            schedule, new Variables(), REFERENCE_DATE, null, null);

    assertEquals(LocalDate.of(2024, 1, 3), bounds.startDate());
    assertEquals(LocalDate.of(2024, 1, 5), bounds.endDate());
  }

  @Test
  void resolveBoundsUsesEarliestParticipatingSatelliteLoad() throws Exception {
    BvPitSnapshotSchedule schedule = defaultSchedule();

    var bounds =
        BvPitSnapshotSpineSupport.resolveBounds(
            schedule,
            new Variables(),
            REFERENCE_DATE,
            LocalDate.of(2024, 1, 4),
            LocalDate.of(2024, 1, 2));

    assertEquals(LocalDate.of(2024, 1, 4), bounds.startDate());
  }

  @Test
  void resolveBoundsUsesEarliestHubLoad() throws Exception {
    BvPitSnapshotSchedule schedule = defaultSchedule();
    schedule.setRangeStart(BvPitRangeStart.EARLIEST_HUB_LOAD);

    var bounds =
        BvPitSnapshotSpineSupport.resolveBounds(
            schedule,
            new Variables(),
            REFERENCE_DATE,
            LocalDate.of(2024, 1, 4),
            LocalDate.of(2024, 1, 2));

    assertEquals(LocalDate.of(2024, 1, 2), bounds.startDate());
  }

  @Test
  void generateDailySpineUsesEndOfDayAnchor() throws Exception {
    BvPitSnapshotSchedule schedule = defaultSchedule();
    var bounds =
        new BvPitSnapshotSpineSupport.SpineBounds(
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3));

    List<LocalDateTime> spine = BvPitSnapshotSpineSupport.generateSnapshotSpine(bounds, schedule);

    assertEquals(3, spine.size());
    assertEquals(LocalDateTime.of(2024, 1, 1, 23, 59, 59), spine.get(0));
    assertEquals(LocalDateTime.of(2024, 1, 3, 23, 59, 59), spine.get(2));
  }

  @Test
  void generateDailySpineUsesStartOfDayAnchor() throws Exception {
    BvPitSnapshotSchedule schedule = defaultSchedule();
    schedule.setSnapshotAnchor(BvPitSnapshotAnchor.START_OF_PERIOD);
    var bounds =
        new BvPitSnapshotSpineSupport.SpineBounds(
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));

    List<LocalDateTime> spine = BvPitSnapshotSpineSupport.generateSnapshotSpine(bounds, schedule);

    assertEquals(2, spine.size());
    assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0), spine.get(0));
    assertEquals(LocalDateTime.of(2024, 1, 2, 0, 0), spine.get(1));
  }

  @Test
  void invalidBoundsProduceEmptySpine() throws Exception {
    var bounds =
        new BvPitSnapshotSpineSupport.SpineBounds(
            LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 1));

    List<LocalDateTime> spine =
        BvPitSnapshotSpineSupport.generateSnapshotSpine(bounds, defaultSchedule());

    assertTrue(spine.isEmpty());
    assertFalse(bounds.isValid());
  }

  @Test
  void filterIncrementalSpineKeepsOnlyNewSnapshots() throws Exception {
    BvPitSnapshotSchedule schedule = defaultSchedule();
    var bounds =
        new BvPitSnapshotSpineSupport.SpineBounds(
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3));
    List<LocalDateTime> spine = BvPitSnapshotSpineSupport.generateSnapshotSpine(bounds, schedule);

    List<LocalDateTime> incremental =
        BvPitSnapshotSpineSupport.filterIncrementalSpine(
            spine, LocalDateTime.of(2024, 1, 2, 23, 59, 59));

    assertEquals(1, incremental.size());
    assertEquals(LocalDateTime.of(2024, 1, 3, 23, 59, 59), incremental.get(0));
  }

  @Test
  void buildPostgresSnapshotSpineCteUsesGenerateSeries() throws Exception {
    var bounds =
        new BvPitSnapshotSpineSupport.SpineBounds(
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));

    String sql =
        BvPitSnapshotSpineSupport.buildPostgresSnapshotSpineCte(
            "snapshot_spine", "snapshot_date", bounds, BvPitSnapshotAnchor.END_OF_PERIOD);

    assertTrue(sql.contains("snapshot_spine AS ("));
    assertTrue(
        sql.contains("generate_series(DATE '2024-01-01', DATE '2024-01-02', INTERVAL '1 day')"));
    assertTrue(sql.contains("23 hours 59 minutes 59 seconds"));
    assertTrue(sql.contains("AS snapshot_date"));
  }

  @Test
  void buildDynamicSnapshotSpineCteRoutesMysqlAwayFromPostgresGenerateSeries() {
    DatabaseMeta mysql = new TestDatabaseMeta("Vault", "MYSQL");

    String sql =
        BvPitSnapshotSpineSupport.buildDynamicSnapshotSpineCte(
            mysql, "snapshot_spine", "snapshot_date", "bounds", BvPitSnapshotAnchor.END_OF_PERIOD);

    assertTrue(sql.contains("WITH RECURSIVE days AS ("));
    assertTrue(sql.contains("DATE_ADD(d.spine_day, INTERVAL 1 DAY)"));
    assertTrue(sql.contains("INTERVAL 23 HOUR"));
    assertFalse(sql.contains("generate_series"));
    assertFalse(sql.contains("::timestamp"));
  }

  @Test
  void mysqlLiteralsAvoidPostgresAndSqlServerSyntax() {
    DatabaseMeta mysql = new TestDatabaseMeta("Vault", "MYSQL");

    assertEquals(
        "CAST('1900-01-01 00:00:00' AS DATETIME)",
        BvPitSnapshotSpineSupport.timestampLiteral(mysql, "1900-01-01 00:00:00"));
    assertEquals("CAST(NULL AS DATETIME)", BvPitSnapshotSpineSupport.nullTimestampLiteral(mysql));
    assertEquals(
        "DATE(earliest_load)",
        BvPitSnapshotSpineSupport.castToDateExpression(mysql, "earliest_load"));
    assertEquals(
        "DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY)",
        BvPitSnapshotSpineSupport.currentDateMinusDaysExpression(mysql, 1));
    assertFalse(BvPitSnapshotSpineSupport.timestampLiteral(mysql, "x").contains("TIMESTAMP '"));
  }

  @Test
  void buildDynamicSnapshotSpineCteUsesNonRecursiveNumberSpineForSingleStore() {
    DatabaseMeta singlestore = new TestDatabaseMeta("Vault", "SINGLESTORE");

    String sql =
        BvPitSnapshotSpineSupport.buildDynamicSnapshotSpineCte(
            singlestore,
            "snapshot_spine",
            "snapshot_date",
            "bounds",
            BvPitSnapshotAnchor.START_OF_PERIOD);

    assertFalse(
        sql.contains("WITH RECURSIVE"), "SingleStore recursive CTEs cannot base on CTE bounds");
    assertTrue(sql.contains("day_offsets AS ("));
    assertTrue(sql.contains("digits AS ("));
    assertTrue(sql.contains("DATEDIFF(b.end_date, b.start_date)"));
    assertTrue(sql.contains("DATE_ADD(b.start_date, INTERVAL o.n DAY)"));
    assertTrue(sql.contains("TIMESTAMP(spine_day)"));
    assertFalse(sql.contains("generate_series"));
    assertFalse(sql.contains("DATEADD"));
    assertEquals(
        BvPitSnapshotSpineSupport.PitSqlDialect.SINGLESTORE,
        BvPitSnapshotSpineSupport.resolveDialect(singlestore));
  }

  @Test
  void singleStoreLiteralsMatchMysqlFamily() {
    DatabaseMeta singlestore = new TestDatabaseMeta("Vault", "SINGLESTORE");

    assertEquals(
        "CAST('1900-01-01 00:00:00' AS DATETIME)",
        BvPitSnapshotSpineSupport.timestampLiteral(singlestore, "1900-01-01 00:00:00"));
    assertEquals(
        "DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY)",
        BvPitSnapshotSpineSupport.currentDateMinusDaysExpression(singlestore, 1));
    assertTrue(BvPitSnapshotSpineSupport.isMysqlFamily(singlestore));
  }

  @Test
  void buildEarliestParticipatingSatelliteLoadSqlUnionsSatellites() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvSatellite satCustomer = (DvSatellite) dvModel.findTable("sat_customer");
    DvSatellite satProduct = (DvSatellite) dvModel.findTable("sat_product");
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");

    String sql =
        BvPitSnapshotSpineSupport.buildEarliestParticipatingSatelliteLoadSql(
            databaseMeta, new Variables(), List.of(satCustomer, satProduct), "x_load_ts");

    assertTrue(sql.contains("SELECT MIN(min_load) AS earliest_load FROM ("));
    assertTrue(sql.contains("SELECT MIN(x_load_ts) AS min_load FROM sat_customer"));
    assertTrue(sql.contains("SELECT MIN(x_load_ts) AS min_load FROM sat_product"));
  }

  @Test
  void buildEarliestHubLoadSqlTargetsHubTable() throws Exception {
    DataVaultModel dvModel = loadVault1Model();
    DvHub hub = (DvHub) dvModel.findTable("hub_customer");
    DatabaseMeta databaseMeta = new TestDatabaseMeta("Vault");

    String sql =
        BvPitSnapshotSpineSupport.buildEarliestHubLoadSql(
            databaseMeta, new Variables(), hub, "x_load_ts");

    assertEquals("SELECT MIN(x_load_ts) AS earliest_load FROM hub_customer", sql);
  }

  @Test
  void buildIncrementalSnapshotFilterSqlUsesPositionalParameter() {
    assertEquals(
        "snapshot_spine.snapshot_date > ?",
        BvPitSnapshotSpineSupport.buildIncrementalSnapshotFilterSql(
            "snapshot_spine.snapshot_date"));
  }

  @Test
  void normalizeAnsiTimestampLiteralsRewritesForSqlServer() {
    DatabaseMeta sqlServer = new TestDatabaseMeta("Vault", "MSSQLNATIVE");
    String sql = "COALESCE(LEAD(x) OVER (ORDER BY t), TIMESTAMP '9999-12-31 23:59:59')";

    String normalized = BvPitSnapshotSpineSupport.normalizeAnsiTimestampLiterals(sqlServer, sql);

    assertEquals(
        "COALESCE(LEAD(x) OVER (ORDER BY t), CAST('9999-12-31 23:59:59' AS datetime2))",
        normalized);
  }

  @Test
  void buildDynamicSnapshotSpineCteUsesGeneratorForSnowflake() {
    DatabaseMeta snowflake = new TestDatabaseMeta("Vault", "SNOWFLAKE");

    String sql =
        BvPitSnapshotSpineSupport.buildDynamicSnapshotSpineCte(
            snowflake,
            "snapshot_spine",
            "snapshot_date",
            "bounds",
            BvPitSnapshotAnchor.END_OF_PERIOD);

    assertEquals(
        BvPitSnapshotSpineSupport.PitSqlDialect.SNOWFLAKE,
        BvPitSnapshotSpineSupport.resolveDialect(snowflake));
    assertTrue(sql.contains("GENERATOR(ROWCOUNT =>"));
    assertTrue(sql.contains("DATEADD(day, g.n, b.start_date)"));
    assertTrue(sql.contains("TIMESTAMP_NTZ"));
    assertFalse(sql.contains("generate_series"));
    assertFalse(sql.contains("WITH RECURSIVE"));
  }

  @Test
  void snowflakeLiteralsKeepAnsiTimestamps() {
    DatabaseMeta snowflake = new TestDatabaseMeta("Vault", "SNOWFLAKE");
    assertEquals(
        "TIMESTAMP '1900-01-01 00:00:00'",
        BvPitSnapshotSpineSupport.timestampLiteral(snowflake, "1900-01-01 00:00:00"));
    assertEquals(
        "DATEADD(day, -1, CURRENT_DATE)",
        BvPitSnapshotSpineSupport.currentDateMinusDaysExpression(snowflake, 1));
    assertEquals(
        "CAST(earliest_load AS DATE)",
        BvPitSnapshotSpineSupport.castToDateExpression(snowflake, "earliest_load"));
    String sql = "SELECT TIMESTAMP '9999-12-31 23:59:59'";
    assertEquals(sql, BvPitSnapshotSpineSupport.normalizeAnsiTimestampLiterals(snowflake, sql));
  }

  @Test
  void normalizeAnsiTimestampLiteralsLeavesPostgresUnchanged() {
    DatabaseMeta postgres = new TestDatabaseMeta("Vault", "POSTGRESQL");
    String sql = "SELECT TIMESTAMP '1900-01-01 00:00:00'";

    assertEquals(sql, BvPitSnapshotSpineSupport.normalizeAnsiTimestampLiterals(postgres, sql));
  }

  @Test
  void buildIncrementalSnapshotWatermarkSqlUsesMaxOnBvTable() {
    DatabaseMeta databaseMeta = new TestDatabaseMeta("BusinessVault");

    String sql =
        BvPitSnapshotSpineSupport.buildIncrementalSnapshotWatermarkSql(
            databaseMeta, new Variables(), "pit_customer", "snapshot_date");

    assertTrue(sql.contains("SELECT MAX(snapshot_date)"));
    assertTrue(sql.contains("FROM pit_customer"));
    assertFalse(sql.contains("TIMESTAMP '"));
    assertFalse(sql.contains("COALESCE"));
  }

  @Test
  void resolveLoadDateFieldUsesDvConfiguration() {
    DataVaultConfiguration dvConfig = new DataVaultConfiguration();
    dvConfig.setLoadDateField("x_load_ts");

    assertEquals(
        "x_load_ts", BvPitSnapshotSpineSupport.resolveLoadDateField(dvConfig, new Variables()));
  }

  private static BvPitSnapshotSchedule defaultSchedule() {
    BvPitSnapshotSchedule schedule = new BvPitSnapshotSchedule();
    schedule.setCadence(BvPitCadence.DAILY);
    schedule.setSnapshotAnchor(BvPitSnapshotAnchor.END_OF_PERIOD);
    schedule.setHorizonDays(1);
    schedule.setRangeStart(BvPitRangeStart.EARLIEST_PARTICIPATING_SATELLITE_LOAD);
    schedule.setRangeEnd(BvPitRangeEnd.NOW_MINUS_HORIZON);
    return schedule;
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
