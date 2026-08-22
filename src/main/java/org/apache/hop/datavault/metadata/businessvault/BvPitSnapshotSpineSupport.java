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

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DvBulkLoadPluginSupport;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSatellite;

/** Snapshot spine date math and dialect-specific SQL fragments for Business Vault PIT tables. */
public final class BvPitSnapshotSpineSupport {

  public static final String DEFAULT_INCREMENTAL_SENTINEL = "1900-01-01 00:00:00";
  public static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

  private static final DateTimeFormatter SQL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private BvPitSnapshotSpineSupport() {}

  /** Inclusive calendar bounds for snapshot spine generation. */
  public record SpineBounds(LocalDate startDate, LocalDate endDate) {

    public boolean isValid() {
      return startDate != null && endDate != null && !startDate.isAfter(endDate);
    }
  }

  public static String resolveLoadDateField(DataVaultConfiguration dvConfig, IVariables variables) {
    String field = dvConfig != null ? dvConfig.getLoadDateField() : null;
    if (variables != null) {
      field = variables.resolve(field);
    }
    if (Utils.isEmpty(field)) {
      return "LOAD_DATE";
    }
    return field;
  }

  public static SpineBounds resolveBounds(
      BvPitSnapshotSchedule schedule,
      IVariables variables,
      LocalDate referenceDate,
      LocalDate earliestParticipatingSatelliteLoad,
      LocalDate earliestHubLoad)
      throws HopException {
    if (schedule == null) {
      throw new HopException("PIT snapshot schedule is required");
    }
    if (referenceDate == null) {
      throw new HopException("Reference date is required to resolve PIT snapshot bounds");
    }

    LocalDate endDate = resolveEndDate(schedule, variables, referenceDate);
    LocalDate startDate =
        resolveStartDate(schedule, variables, earliestParticipatingSatelliteLoad, earliestHubLoad);
    return new SpineBounds(startDate, endDate);
  }

  public static List<LocalDateTime> generateSnapshotSpine(
      SpineBounds bounds, BvPitSnapshotSchedule schedule) throws HopException {
    if (bounds == null || !bounds.isValid() || schedule == null) {
      return List.of();
    }
    if (schedule.getCadence() != BvPitCadence.DAILY) {
      throw new HopException(
          "PIT snapshot cadence "
              + schedule.getCadence()
              + " is not implemented; only DAILY is supported in the MVP");
    }

    BvPitSnapshotAnchor anchor =
        schedule.getSnapshotAnchor() != null
            ? schedule.getSnapshotAnchor()
            : BvPitSnapshotAnchor.END_OF_PERIOD;

    List<LocalDateTime> spine = new ArrayList<>();
    for (LocalDate day = bounds.startDate();
        !day.isAfter(bounds.endDate());
        day = day.plusDays(1)) {
      spine.add(anchorSnapshot(day, anchor));
    }
    return spine;
  }

  public static List<LocalDateTime> filterIncrementalSpine(
      List<LocalDateTime> spine, LocalDateTime incrementalAfter) {
    if (spine == null || spine.isEmpty() || incrementalAfter == null) {
      return spine != null ? spine : List.of();
    }
    return spine.stream().filter(snapshot -> snapshot.isAfter(incrementalAfter)).toList();
  }

  public static LocalDate toLocalDate(Timestamp timestamp) {
    if (timestamp == null) {
      return null;
    }
    return timestamp.toLocalDateTime().toLocalDate();
  }

  /**
   * Max inclusive day offsets for SingleStore non-recursive spine (0..9999 ≈ 27 years). Enough for
   * typical PIT horizons without recursive CTEs.
   */
  public static final int SINGLESTORE_SPINE_MAX_DAY_OFFSET = 9999;

  /** SQL dialect used for generated PIT spine / date expressions. */
  public enum PitSqlDialect {
    POSTGRES,
    SQL_SERVER,
    MYSQL,
    /** MySQL-compatible types; non-recursive number spine (recursive CTE limits differ). */
    SINGLESTORE,
    /**
     * Snowflake: ANSI timestamps, {@code GENERATOR}/{@code DATEADD} spine, {@code TIMESTAMP_NTZ}.
     */
    SNOWFLAKE
  }

  public static PitSqlDialect resolveDialect(DatabaseMeta databaseMeta) {
    if (databaseMeta == null || Utils.isEmpty(databaseMeta.getPluginId())) {
      return PitSqlDialect.POSTGRES;
    }
    String pluginId = databaseMeta.getPluginId();
    if (DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)
        || DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)) {
      return PitSqlDialect.SQL_SERVER;
    }
    if (DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)) {
      return PitSqlDialect.SINGLESTORE;
    }
    if (DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)) {
      return PitSqlDialect.MYSQL;
    }
    if (DvBulkLoadPluginSupport.SNOWFLAKE_DB_PLUGIN_ID.equalsIgnoreCase(pluginId)) {
      return PitSqlDialect.SNOWFLAKE;
    }
    // PostgreSQL and unknown engines: Postgres-style SQL (historical default)
    return PitSqlDialect.POSTGRES;
  }

  public static boolean isSqlServer(DatabaseMeta databaseMeta) {
    return resolveDialect(databaseMeta) == PitSqlDialect.SQL_SERVER;
  }

  /** MySQL and SingleStore share date/time function dialects (not always spine strategy). */
  public static boolean isMysqlFamily(DatabaseMeta databaseMeta) {
    PitSqlDialect dialect = resolveDialect(databaseMeta);
    return dialect == PitSqlDialect.MYSQL || dialect == PitSqlDialect.SINGLESTORE;
  }

  public static String nullTimestampLiteral(DatabaseMeta databaseMeta) {
    return switch (resolveDialect(databaseMeta)) {
      case SQL_SERVER -> "CAST(NULL AS datetime2)";
      case MYSQL, SINGLESTORE -> "CAST(NULL AS DATETIME)";
      case POSTGRES, SNOWFLAKE -> "CAST(NULL AS timestamp)";
    };
  }

  public static String timestampLiteral(DatabaseMeta databaseMeta, String value) {
    return switch (resolveDialect(databaseMeta)) {
      case SQL_SERVER -> "CAST('" + value + "' AS datetime2)";
      case MYSQL, SINGLESTORE -> "CAST('" + value + "' AS DATETIME)";
      case POSTGRES, SNOWFLAKE -> "TIMESTAMP '" + value + "'";
    };
  }

  /**
   * Postgres/ANSI {@code TIMESTAMP '…'} literals are invalid on SQL Server and MySQL/SingleStore.
   * Rewrite them to the dialect form from {@link #timestampLiteral(DatabaseMeta, String)} so
   * authoring SQL (BV views/tables) and fixtures can stay portable.
   *
   * <p>Only bare {@code TIMESTAMP 'value'} forms are rewritten (case-insensitive keyword, optional
   * whitespace). Already dialect-specific casts are left unchanged.
   */
  private static final Pattern ANSI_TIMESTAMP_LITERAL =
      Pattern.compile("(?i)\\bTIMESTAMP\\s+'([^']*)'");

  public static String normalizeAnsiTimestampLiterals(DatabaseMeta databaseMeta, String sql) {
    if (Utils.isEmpty(sql) || databaseMeta == null) {
      return sql;
    }
    // Postgres/Snowflake authoring form is already the target dialect — avoid no-op churn.
    PitSqlDialect dialect = resolveDialect(databaseMeta);
    if (dialect == PitSqlDialect.POSTGRES || dialect == PitSqlDialect.SNOWFLAKE) {
      return sql;
    }
    Matcher matcher = ANSI_TIMESTAMP_LITERAL.matcher(sql);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String replacement =
          Matcher.quoteReplacement(timestampLiteral(databaseMeta, matcher.group(1)));
      matcher.appendReplacement(out, replacement);
    }
    matcher.appendTail(out);
    return out.toString();
  }

  public static String dateLiteral(DatabaseMeta databaseMeta, String yyyyMmDd) {
    return switch (resolveDialect(databaseMeta)) {
      case SQL_SERVER -> "CAST('" + yyyyMmDd + "' AS date)";
      case MYSQL, SINGLESTORE -> "CAST('" + yyyyMmDd + "' AS DATE)";
      case POSTGRES, SNOWFLAKE -> "DATE '" + yyyyMmDd + "'";
    };
  }

  public static String currentDateExpression(DatabaseMeta databaseMeta) {
    return switch (resolveDialect(databaseMeta)) {
      case SQL_SERVER -> "CAST(GETDATE() AS date)";
      case MYSQL, SINGLESTORE, POSTGRES, SNOWFLAKE -> "CURRENT_DATE";
    };
  }

  public static String castToDateExpression(DatabaseMeta databaseMeta, String expression) {
    return switch (resolveDialect(databaseMeta)) {
      case SQL_SERVER -> "CAST(" + expression + " AS date)";
      case MYSQL, SINGLESTORE -> "DATE(" + expression + ")";
      case POSTGRES -> expression + "::date";
      case SNOWFLAKE -> "CAST(" + expression + " AS DATE)";
    };
  }

  /** Subtract {@code days} from the current date (returns a date expression). */
  public static String currentDateMinusDaysExpression(DatabaseMeta databaseMeta, int days) {
    int horizon = Math.max(0, days);
    return switch (resolveDialect(databaseMeta)) {
      case SQL_SERVER ->
          "DATEADD(day, -" + horizon + ", " + currentDateExpression(databaseMeta) + ")";
      case MYSQL, SINGLESTORE -> "DATE_SUB(CURRENT_DATE, INTERVAL " + horizon + " DAY)";
      case POSTGRES -> "(CURRENT_DATE - INTERVAL '" + horizon + " day')::date";
      case SNOWFLAKE -> "DATEADD(day, -" + horizon + ", CURRENT_DATE)";
    };
  }

  public static String buildEarliestParticipatingSatelliteLoadSql(
      DatabaseMeta databaseMeta,
      IVariables variables,
      List<DvSatellite> satellites,
      String loadDateField) {
    if (databaseMeta == null || satellites == null || satellites.isEmpty()) {
      return "SELECT " + nullTimestampLiteral(databaseMeta) + " AS earliest_load";
    }
    String quotedLoadDate = databaseMeta.quoteField(loadDateField);
    List<String> branches = new ArrayList<>();
    for (DvSatellite satellite : satellites) {
      if (satellite == null) {
        continue;
      }
      String tableName = BvPitLayoutSupport.resolveSatellitePhysicalName(satellite);
      String quotedTable = databaseMeta.getQuotedSchemaTableCombination(variables, null, tableName);
      branches.add("SELECT MIN(" + quotedLoadDate + ") AS min_load FROM " + quotedTable);
    }
    if (branches.isEmpty()) {
      return "SELECT " + nullTimestampLiteral(databaseMeta) + " AS earliest_load";
    }
    if (branches.size() == 1) {
      return branches.get(0).replace("min_load", "earliest_load");
    }
    StringBuilder sql = new StringBuilder("SELECT MIN(min_load) AS earliest_load FROM (");
    sql.append(String.join(" UNION ALL ", branches));
    sql.append(") participating_satellites");
    return sql.toString();
  }

  public static String buildEarliestHubLoadSql(
      DatabaseMeta databaseMeta, IVariables variables, DvHub hub, String loadDateField) {
    if (databaseMeta == null || hub == null) {
      return "SELECT " + nullTimestampLiteral(databaseMeta) + " AS earliest_load";
    }
    String tableName = !Utils.isEmpty(hub.getTableName()) ? hub.getTableName() : hub.getName();
    String quotedTable = databaseMeta.getQuotedSchemaTableCombination(variables, null, tableName);
    String quotedLoadDate = databaseMeta.quoteField(loadDateField);
    return "SELECT MIN(" + quotedLoadDate + ") AS earliest_load FROM " + quotedTable;
  }

  public static String buildDynamicSnapshotSpineCte(
      DatabaseMeta databaseMeta,
      String cteName,
      String snapshotColumnAlias,
      String boundsCteName,
      BvPitSnapshotAnchor anchor) {
    return switch (resolveDialect(databaseMeta)) {
      case SQL_SERVER ->
          buildSqlServerDynamicSnapshotSpineCte(
              cteName, snapshotColumnAlias, boundsCteName, anchor);
      case MYSQL ->
          buildMysqlDynamicSnapshotSpineCte(cteName, snapshotColumnAlias, boundsCteName, anchor);
      case SINGLESTORE ->
          buildSinglestoreDynamicSnapshotSpineCte(
              cteName, snapshotColumnAlias, boundsCteName, anchor);
      case SNOWFLAKE ->
          buildSnowflakeDynamicSnapshotSpineCte(
              cteName, snapshotColumnAlias, boundsCteName, anchor);
      case POSTGRES ->
          buildPostgresDynamicSnapshotSpineCte(cteName, snapshotColumnAlias, boundsCteName, anchor);
    };
  }

  public static String buildPostgresDynamicSnapshotSpineCte(
      String cteName,
      String snapshotColumnAlias,
      String boundsCteName,
      BvPitSnapshotAnchor anchor) {
    String snapshotExpression = postgresSnapshotExpression(anchor);
    return cteName
        + " AS ("
        + "SELECT "
        + snapshotExpression
        + " AS "
        + snapshotColumnAlias
        + " FROM "
        + boundsCteName
        + " b "
        + "CROSS JOIN LATERAL generate_series(b.start_date, b.end_date, INTERVAL '1 day') AS spine_day "
        + "WHERE b.start_date IS NOT NULL AND b.end_date IS NOT NULL AND b.start_date <= b.end_date"
        + ")";
  }

  public static String buildSqlServerDynamicSnapshotSpineCte(
      String cteName,
      String snapshotColumnAlias,
      String boundsCteName,
      BvPitSnapshotAnchor anchor) {
    String snapshotExpression = sqlServerSnapshotExpression(anchor);
    return cteName
        + " AS ("
        + "SELECT "
        + snapshotExpression
        + " AS "
        + snapshotColumnAlias
        + " FROM "
        + boundsCteName
        + " b "
        + "CROSS APPLY ("
        + "SELECT DATEADD(day, n.number, b.start_date) AS spine_day FROM ("
        + "SELECT TOP (CASE WHEN b.start_date IS NULL OR b.end_date IS NULL OR b.start_date > b.end_date "
        + "THEN 0 ELSE DATEDIFF(day, b.start_date, b.end_date) + 1 END) "
        + "ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) - 1 AS number FROM sys.all_objects"
        + ") n"
        + ") spine "
        + "WHERE b.start_date IS NOT NULL AND b.end_date IS NOT NULL AND b.start_date <= b.end_date"
        + ")";
  }

  /**
   * MySQL 8 recursive date spine. Nested {@code WITH RECURSIVE} keeps the outer pipeline CTE list
   * unchanged.
   */
  public static String buildMysqlDynamicSnapshotSpineCte(
      String cteName,
      String snapshotColumnAlias,
      String boundsCteName,
      BvPitSnapshotAnchor anchor) {
    String snapshotExpression = mysqlSnapshotExpression(anchor);
    return cteName
        + " AS ("
        + "WITH RECURSIVE days AS ("
        + "SELECT b.start_date AS spine_day FROM "
        + boundsCteName
        + " b "
        + "WHERE b.start_date IS NOT NULL AND b.end_date IS NOT NULL AND b.start_date <= b.end_date "
        + "UNION ALL "
        + "SELECT DATE_ADD(d.spine_day, INTERVAL 1 DAY) FROM days d "
        + "CROSS JOIN "
        + boundsCteName
        + " b "
        + "WHERE d.spine_day < b.end_date"
        + ") "
        + "SELECT "
        + snapshotExpression
        + " AS "
        + snapshotColumnAlias
        + " FROM days"
        + ")";
  }

  /**
   * SingleStore date spine without recursive CTEs.
   *
   * <p>SingleStore recursive CTEs require a base case over a sharded table and must materialize;
   * nesting {@code WITH RECURSIVE} inside a non-recursive CTE (reading only from other CTEs such as
   * {@code bounds}) fails with "recursive CTE select cannot be materialized". SingleStore has
   * strong non-recursive CTE support, so this returns peer CTEs ({@code digits}, {@code
   * day_offsets}, then the spine) that expand 0..9999 day offsets via digit cross-joins.
   *
   * <p>Callers append this fragment after {@code bounds} in the outer {@code WITH} list (it may
   * introduce multiple comma-separated CTEs).
   */
  public static String buildSinglestoreDynamicSnapshotSpineCte(
      String cteName,
      String snapshotColumnAlias,
      String boundsCteName,
      BvPitSnapshotAnchor anchor) {
    // mysqlSnapshotExpression references alias spine_day
    String snapshotExpression = mysqlSnapshotExpression(anchor);
    return "digits AS ("
        + "SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 "
        + "UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9"
        + "), "
        + "day_offsets AS ("
        + "SELECT (a.d + 10 * b.d + 100 * c.d + 1000 * e.d) AS n "
        + "FROM digits a CROSS JOIN digits b CROSS JOIN digits c CROSS JOIN digits e"
        + "), "
        + cteName
        + " AS ("
        + "SELECT "
        + snapshotExpression
        + " AS "
        + snapshotColumnAlias
        + " FROM ("
        + "SELECT DATE_ADD(b.start_date, INTERVAL o.n DAY) AS spine_day "
        + "FROM "
        + boundsCteName
        + " b "
        + "CROSS JOIN day_offsets o "
        + "WHERE b.start_date IS NOT NULL AND b.end_date IS NOT NULL AND b.start_date <= b.end_date "
        + "AND o.n <= DATEDIFF(b.end_date, b.start_date) "
        + "AND o.n <= "
        + SINGLESTORE_SPINE_MAX_DAY_OFFSET
        + ") spine_days"
        + ")";
  }

  /**
   * Snowflake date spine using {@code GENERATOR} + {@code DATEADD}. Does not use Postgres {@code
   * generate_series}. Caps the span at {@link #SINGLESTORE_SPINE_MAX_DAY_OFFSET} days because
   * {@code GENERATOR(ROWCOUNT)} is a constant.
   */
  public static String buildSnowflakeDynamicSnapshotSpineCte(
      String cteName,
      String snapshotColumnAlias,
      String boundsCteName,
      BvPitSnapshotAnchor anchor) {
    String snapshotExpression = snowflakeSnapshotExpression(anchor);
    return cteName
        + " AS ("
        + "SELECT "
        + snapshotExpression
        + " AS "
        + snapshotColumnAlias
        + " FROM ("
        + "SELECT DATEADD(day, g.n, b.start_date) AS spine_day "
        + "FROM "
        + boundsCteName
        + " b "
        + "CROSS JOIN (SELECT SEQ4() - 1 AS n FROM TABLE(GENERATOR(ROWCOUNT => "
        + SINGLESTORE_SPINE_MAX_DAY_OFFSET
        + "))) g "
        + "WHERE b.start_date IS NOT NULL AND b.end_date IS NOT NULL AND b.start_date <= b.end_date "
        + "AND g.n <= DATEDIFF('day', b.start_date, b.end_date)"
        + ") spine"
        + ")";
  }

  public static String buildPostgresSnapshotSpineCte(
      String cteName, String snapshotColumnAlias, SpineBounds bounds, BvPitSnapshotAnchor anchor) {
    if (bounds == null || !bounds.isValid()) {
      return cteName
          + " AS (SELECT CAST(NULL AS timestamp) AS "
          + snapshotColumnAlias
          + " WHERE 1 = 0)";
    }
    String startLiteral = "DATE '" + bounds.startDate().format(SQL_DATE) + "'";
    String endLiteral = "DATE '" + bounds.endDate().format(SQL_DATE) + "'";
    String snapshotExpression = postgresSnapshotExpression(anchor);

    return cteName
        + " AS ("
        + "SELECT "
        + snapshotExpression
        + " AS "
        + snapshotColumnAlias
        + " FROM generate_series("
        + startLiteral
        + ", "
        + endLiteral
        + ", INTERVAL '1 day') AS spine_day"
        + ")";
  }

  /**
   * Incremental snapshot filter using a positional JDBC parameter for the watermark. Bound at
   * runtime from a preceding Constant transform — dialect-neutral and safe across separate DV/BV
   * databases.
   *
   * @param snapshotColumnRef outer SQL column/expression compared to the watermark
   */
  public static String buildIncrementalSnapshotFilterSql(String snapshotColumnRef) {
    return snapshotColumnRef + " > ?";
  }

  /**
   * BV-only query used at pipeline generation to resolve the incremental snapshot watermark.
   * Returns {@code MAX(snapshot_date)} only; null results fall back to the default sentinel in Java
   * so the SQL stays free of dialect-specific timestamp literals.
   */
  public static String buildIncrementalSnapshotWatermarkSql(
      DatabaseMeta bvDatabaseMeta,
      IVariables variables,
      String pitTableName,
      String snapshotDateField) {
    String quotedTable =
        bvDatabaseMeta.getQuotedSchemaTableCombination(variables, null, pitTableName);
    String quotedSnapshotField = bvDatabaseMeta.quoteField(snapshotDateField);
    return "SELECT MAX("
        + quotedSnapshotField
        + ") AS incremental_snapshot_watermark FROM "
        + quotedTable;
  }

  static LocalDateTime anchorSnapshot(LocalDate day, BvPitSnapshotAnchor anchor) {
    if (anchor == BvPitSnapshotAnchor.START_OF_PERIOD) {
      return day.atStartOfDay();
    }
    return LocalDateTime.of(day, END_OF_DAY);
  }

  static String postgresSnapshotExpression(BvPitSnapshotAnchor anchor) {
    if (anchor == BvPitSnapshotAnchor.START_OF_PERIOD) {
      return "spine_day::timestamp";
    }
    return "(spine_day::timestamp + INTERVAL '23 hours 59 minutes 59 seconds')";
  }

  static String sqlServerSnapshotExpression(BvPitSnapshotAnchor anchor) {
    if (anchor == BvPitSnapshotAnchor.START_OF_PERIOD) {
      return "CAST(spine.spine_day AS datetime2)";
    }
    // 23:59:59 on the calendar day
    return "DATEADD(second, 86399, CAST(spine.spine_day AS datetime2))";
  }

  static String mysqlSnapshotExpression(BvPitSnapshotAnchor anchor) {
    if (anchor == BvPitSnapshotAnchor.START_OF_PERIOD) {
      return "TIMESTAMP(spine_day)";
    }
    return "TIMESTAMP(spine_day) + INTERVAL 23 HOUR + INTERVAL 59 MINUTE + INTERVAL 59 SECOND";
  }

  static String snowflakeSnapshotExpression(BvPitSnapshotAnchor anchor) {
    if (anchor == BvPitSnapshotAnchor.START_OF_PERIOD) {
      return "CAST(spine.spine_day AS TIMESTAMP_NTZ)";
    }
    return "DATEADD(second, 86399, CAST(spine.spine_day AS TIMESTAMP_NTZ))";
  }

  private static LocalDate resolveEndDate(
      BvPitSnapshotSchedule schedule, IVariables variables, LocalDate referenceDate)
      throws HopException {
    BvPitRangeEnd rangeEnd =
        schedule.getRangeEnd() != null ? schedule.getRangeEnd() : BvPitRangeEnd.NOW_MINUS_HORIZON;
    return switch (rangeEnd) {
      case NOW -> referenceDate;
      case NOW_MINUS_HORIZON -> referenceDate.minusDays(Math.max(0, schedule.getHorizonDays()));
      case FIXED_DATE ->
          parseFixedDate(resolve(variables, schedule.getRangeEndFixed()), "range end");
    };
  }

  private static LocalDate resolveStartDate(
      BvPitSnapshotSchedule schedule,
      IVariables variables,
      LocalDate earliestParticipatingSatelliteLoad,
      LocalDate earliestHubLoad)
      throws HopException {
    BvPitRangeStart rangeStart =
        schedule.getRangeStart() != null
            ? schedule.getRangeStart()
            : BvPitRangeStart.EARLIEST_PARTICIPATING_SATELLITE_LOAD;
    return switch (rangeStart) {
      case FIXED_DATE ->
          parseFixedDate(resolve(variables, schedule.getRangeStartFixed()), "range start");
      case EARLIEST_PARTICIPATING_SATELLITE_LOAD -> earliestParticipatingSatelliteLoad;
      case EARLIEST_HUB_LOAD -> earliestHubLoad;
    };
  }

  private static LocalDate parseFixedDate(String value, String label) throws HopException {
    if (Utils.isEmpty(value)) {
      throw new HopException("PIT snapshot " + label + " fixed date is required");
    }
    String trimmed = value.trim();
    try {
      if (trimmed.length() >= 10) {
        return LocalDate.parse(trimmed.substring(0, 10), SQL_DATE);
      }
      return LocalDate.parse(trimmed, SQL_DATE);
    } catch (DateTimeParseException e) {
      throw new HopException(
          "PIT snapshot " + label + " fixed date '" + value + "' is not a valid date", e);
    }
  }

  private static String resolve(IVariables variables, String value) {
    if (variables != null) {
      return variables.resolve(value);
    }
    return value;
  }
}
