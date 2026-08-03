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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * Chooses how to order the <em>target</em> stream of a link CDC merge on the link hash key so that
 * order matches Hop SortRows (Java code-point / {@link String#compareTo} for STRING/HEX; byte order
 * for BINARY).
 *
 * <p>Linguistic database collations (PostgreSQL locale/ICU, SQL Server CI, MySQL {@code *_ci}, …)
 * can disagree with Hop on STRING (decimal-dash) hash keys — for example {@code 0-10-…} vs {@code
 * 0-100-…}. That desynchronizes MergeRows and can re-insert existing keys under a primary key.
 *
 * <p>Strategy:
 *
 * <ul>
 *   <li><b>BINARY</b> hash keys: plain SQL {@code ORDER BY} (no collation).
 *   <li><b>STRING / HEX</b>: SQL {@code ORDER BY … COLLATE &lt;hop-compatible binary&gt;} when the
 *       engine is known and we are certain (static trust and/or live probe); otherwise Hop Sort
 *       Rows on the target leg.
 * </ul>
 *
 * <p>No user configuration: correctness is the plugin's responsibility. Source-side SortRows after
 * {@code DvHashKey} is always required (hash is computed in Hop).
 */
public final class DvHashKeyOrderStrategySupport {

  private static final ILoggingObject LOGGING_OBJECT =
      new SimpleLoggingObject("DvHashKeyOrderStrategy", LoggingObjectType.GENERAL, null);

  /**
   * Known reverse pair under UCA / locale collations vs Java: Java places TEN before HUNDRED
   * because {@code '-'} (45) &lt; {@code '0'} (48) after the shared prefix {@code 0-10}.
   */
  public static final String PROBE_TEN = "0-10-223-4-100-150-80-194-196-90-5-75-165-24-39-120";

  public static final String PROBE_HUNDRED =
      "0-100-114-143-52-153-54-130-206-138-191-46-249-199-180-57";

  /** How the target leg should be ordered for MergeRows. */
  public enum TargetHashOrderMode {
    /** Append SQL ORDER BY (optional COLLATE); do not add target SortRows. */
    SQL_ORDER_BY,
    /** No SQL ORDER BY; add Hop SortRows after Table Input. */
    HOP_SORT_ROWS
  }

  /**
   * Resolved plan for one link target read.
   *
   * @param mode ordering strategy
   * @param orderBySqlSuffix full suffix starting with {@code ORDER BY}, or null for Hop sort
   * @param rationale human-readable reason (logs / tests)
   * @param wrapDistinctSubquery when true, wrap {@code SELECT DISTINCT …} in a subquery before
   *     applying ORDER BY (SQL Server DISTINCT + expression ORDER BY rules)
   */
  public record TargetHashOrderPlan(
      TargetHashOrderMode mode,
      String orderBySqlSuffix,
      String rationale,
      boolean wrapDistinctSubquery) {

    public boolean useSqlOrderBy() {
      return mode == TargetHashOrderMode.SQL_ORDER_BY;
    }

    public boolean useHopSortRows() {
      return mode == TargetHashOrderMode.HOP_SORT_ROWS;
    }
  }

  /** Session cache: one probe/resolve per connection identity during pipeline generation. */
  public static final class Session {
    private final Map<String, TargetHashOrderPlan> cache = new ConcurrentHashMap<>();

    public TargetHashOrderPlan get(String key) {
      return cache.get(key);
    }

    public void put(String key, TargetHashOrderPlan plan) {
      if (key != null && plan != null) {
        cache.put(key, plan);
      }
    }
  }

  private DvHashKeyOrderStrategySupport() {}

  /**
   * Resolve target-stream ordering for a link hash key column.
   *
   * @param databaseMeta target database (may be null → Hop sort)
   * @param config model configuration (hash key data type)
   * @param variables Hop variables
   * @param quotedHashExpression already-quoted LHK column for ORDER BY
   * @param session optional cache for this generation batch
   * @param allowLiveProbe when true and a connection works, verify candidates with the dash pair
   */
  public static TargetHashOrderPlan resolve(
      DatabaseMeta databaseMeta,
      DataVaultConfiguration config,
      IVariables variables,
      String quotedHashExpression,
      Session session,
      boolean allowLiveProbe) {
    if (Utils.isEmpty(quotedHashExpression)) {
      return hopSort("empty hash expression");
    }
    HashKeyDataType hashType =
        config != null ? config.resolveHashKeyDataType() : HashKeyDataType.HEX;

    if (hashType == HashKeyDataType.BINARY) {
      return sqlOrderBy(
          " ORDER BY " + quotedHashExpression,
          "BINARY hash key: plain SQL ORDER BY (byte order)",
          needsDistinctOrderBySubquery(databaseMeta));
    }

    if (databaseMeta == null || Utils.isEmpty(databaseMeta.getPluginId())) {
      return hopSort("no target database meta; STRING/HEX hash requires Hop SortRows");
    }

    String cacheKey = cacheKey(databaseMeta, hashType);
    if (session != null) {
      TargetHashOrderPlan cached = session.get(cacheKey);
      if (cached != null) {
        // Re-bind ORDER BY expression to this column (cache stores template with placeholder).
        return rebindQuotedExpression(cached, quotedHashExpression);
      }
    }

    TargetHashOrderPlan plan =
        resolveStringHashOrder(databaseMeta, variables, quotedHashExpression, allowLiveProbe);
    if (session != null) {
      session.put(cacheKey, plan);
    }
    return plan;
  }

  /** Convenience: resolve with live probe enabled and no session cache. */
  public static TargetHashOrderPlan resolve(
      DatabaseMeta databaseMeta,
      DataVaultConfiguration config,
      IVariables variables,
      String quotedHashExpression) {
    return resolve(databaseMeta, config, variables, quotedHashExpression, null, true);
  }

  /**
   * Apply a resolved plan to a {@code SELECT DISTINCT … FROM …} target SQL body (no trailing ORDER
   * BY). Returns the final SQL string for Table Input.
   */
  public static String applyToDistinctSelect(String distinctSelectSql, TargetHashOrderPlan plan) {
    if (Utils.isEmpty(distinctSelectSql) || plan == null || !plan.useSqlOrderBy()) {
      return distinctSelectSql;
    }
    String orderBy = plan.orderBySqlSuffix();
    if (Utils.isEmpty(orderBy)) {
      return distinctSelectSql;
    }
    if (plan.wrapDistinctSubquery()) {
      return "SELECT * FROM (" + distinctSelectSql + ") hop_lhk_ord" + orderBy;
    }
    return distinctSelectSql + orderBy;
  }

  /**
   * Candidate hop-compatible collations for a database plugin, in preference order. Empty when the
   * engine is not in the registry (always Hop SortRows for STRING/HEX).
   */
  public static List<String> candidateCollations(DatabaseMeta databaseMeta) {
    if (databaseMeta == null || Utils.isEmpty(databaseMeta.getPluginId())) {
      return List.of();
    }
    String pluginId = databaseMeta.getPluginId().trim().toUpperCase(Locale.ROOT);
    return switch (pluginId) {
      case DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID -> List.of("C", "POSIX");
      case DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID ->
          List.of("Latin1_General_100_BIN2", "Latin1_General_100_BIN2_UTF8", "Latin1_General_BIN2");
      case DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID,
              "MARIADB" ->
          List.of("utf8mb4_bin", "utf8mb4_0900_bin", "binary");
      default -> List.of();
    };
  }

  /**
   * True when the first candidate for this engine is statically trusted to match Hop code-point
   * order for ASCII hash content (unit-tested invariant + engine collation definition).
   */
  public static boolean hasStaticTrust(DatabaseMeta databaseMeta) {
    if (databaseMeta == null || Utils.isEmpty(databaseMeta.getPluginId())) {
      return false;
    }
    String pluginId = databaseMeta.getPluginId().trim().toUpperCase(Locale.ROOT);
    return switch (pluginId) {
      case DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID,
              "MARIADB" ->
          true;
      default -> false;
    };
  }

  /**
   * True when this engine needs {@code SELECT * FROM (SELECT DISTINCT …) t ORDER BY col} even for a
   * bare column (no COLLATE). SQL Server is the known case; PostgreSQL accepts bare-column ORDER BY
   * with DISTINCT when the column is in the select list.
   */
  public static boolean needsDistinctOrderBySubquery(DatabaseMeta databaseMeta) {
    return DvSqlOrderBySupport.isSqlServer(databaseMeta);
  }

  /**
   * True when ORDER BY uses COLLATE (or any expression that is not identical to a select-list
   * column). PostgreSQL, SQL Server, and MySQL all reject {@code SELECT DISTINCT col … ORDER BY col
   * COLLATE x} because the COLLATE expression is not in the select list.
   */
  public static boolean needsDistinctOrderBySubqueryForCollate() {
    return true;
  }

  /** Java / Hop order: TEN before HUNDRED. */
  public static boolean javaPlacesTenBeforeHundred() {
    return PROBE_TEN.compareTo(PROBE_HUNDRED) < 0;
  }

  /** Outcome of a live collation probe against the target database. */
  enum ProbeOutcome {
    /** Connected and a candidate produced Hop order. */
    MATCHED,
    /** Connected but no candidate matched Hop order (or all SQL failed). */
    CONNECTED_NO_MATCH,
    /** Could not connect or probe is not applicable. */
    UNAVAILABLE
  }

  record ProbeResult(ProbeOutcome outcome, String collation) {
    static ProbeResult matched(String collation) {
      return new ProbeResult(ProbeOutcome.MATCHED, collation);
    }

    static ProbeResult connectedNoMatch() {
      return new ProbeResult(ProbeOutcome.CONNECTED_NO_MATCH, null);
    }

    static ProbeResult unavailable() {
      return new ProbeResult(ProbeOutcome.UNAVAILABLE, null);
    }
  }

  static TargetHashOrderPlan resolveStringHashOrder(
      DatabaseMeta databaseMeta,
      IVariables variables,
      String quotedHashExpression,
      boolean allowLiveProbe) {
    List<String> candidates = candidateCollations(databaseMeta);
    if (candidates.isEmpty()) {
      return hopSort(
          "engine "
              + Const.NVL(databaseMeta.getPluginId(), "?")
              + " has no hop-compatible hash collation registry entry");
    }

    if (allowLiveProbe) {
      ProbeResult probe = probeCollations(databaseMeta, variables, candidates);
      if (probe.outcome() == ProbeOutcome.MATCHED && !Utils.isEmpty(probe.collation())) {
        return sqlOrderByWithCollation(
            databaseMeta,
            quotedHashExpression,
            probe.collation(),
            "live probe confirmed hop order with COLLATE " + probe.collation());
      }
      // Connected but no candidate matched: do not fall back to static trust.
      if (probe.outcome() == ProbeOutcome.CONNECTED_NO_MATCH) {
        return hopSort("live probe did not confirm any hop-compatible collation; using SortRows");
      }
      // UNAVAILABLE: fall through to static trust
    }

    // Offline or probe unavailable: use static trust for known engines only.
    if (hasStaticTrust(databaseMeta)) {
      String trusted = candidates.get(0);
      return sqlOrderByWithCollation(
          databaseMeta,
          quotedHashExpression,
          trusted,
          "static trust: hop-compatible COLLATE "
              + trusted
              + " for "
              + databaseMeta.getPluginId()
              + " (offline / no live probe)");
    }

    return hopSort("no static trust and no live probe for " + databaseMeta.getPluginId());
  }

  /** Builds ORDER BY fragment with optional COLLATE for a string hash expression. */
  static String orderBySuffix(
      DatabaseMeta databaseMeta, String quotedExpression, String collation) {
    if (Utils.isEmpty(collation)) {
      return " ORDER BY " + quotedExpression;
    }
    return " ORDER BY "
        + quotedExpression
        + " COLLATE "
        + DvSqlOrderBySupport.formatCollationIdentifier(databaseMeta, collation);
  }

  static ProbeResult probeCollations(
      DatabaseMeta databaseMeta, IVariables variables, List<String> candidates) {
    if (databaseMeta == null || candidates == null || candidates.isEmpty()) {
      return ProbeResult.unavailable();
    }
    try (Database db = new Database(LOGGING_OBJECT, variables, databaseMeta)) {
      db.connect();
      for (String collation : candidates) {
        if (Utils.isEmpty(collation)) {
          continue;
        }
        String sql = buildProbeOrderSql(databaseMeta, collation);
        if (Utils.isEmpty(sql)) {
          continue;
        }
        try {
          List<Object[]> rows = db.getRows(sql, 2);
          if (probeRowsMatchHopOrder(rows)) {
            return ProbeResult.matched(collation);
          }
        } catch (Exception candidateFailure) {
          // try next candidate
        }
      }
      return ProbeResult.connectedNoMatch();
    } catch (Exception e) {
      return ProbeResult.unavailable();
    }
  }

  /**
   * Engine-specific two-row ORDER BY probe. Returns null when syntax is not implemented for the
   * plugin.
   */
  static String buildProbeOrderSql(DatabaseMeta databaseMeta, String collation) {
    if (databaseMeta == null || Utils.isEmpty(collation)) {
      return null;
    }
    String pluginId =
        databaseMeta.getPluginId() != null
            ? databaseMeta.getPluginId().trim().toUpperCase(Locale.ROOT)
            : "";
    String collId = DvSqlOrderBySupport.formatCollationIdentifier(databaseMeta, collation);
    String ten = escapeSqlLiteral(PROBE_TEN);
    String hundred = escapeSqlLiteral(PROBE_HUNDRED);

    return switch (pluginId) {
      case DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID ->
          "SELECT v FROM (VALUES ('"
              + ten
              + "'), ('"
              + hundred
              + "')) AS t(v) ORDER BY v COLLATE "
              + collId;
      case DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID ->
          "SELECT v FROM (VALUES (N'"
              + ten
              + "'), (N'"
              + hundred
              + "')) AS t(v) ORDER BY v COLLATE "
              + collId;
      case DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID,
              DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID,
              "MARIADB" ->
          "SELECT v FROM (SELECT '"
              + ten
              + "' AS v UNION ALL SELECT '"
              + hundred
              + "') t ORDER BY v COLLATE "
              + collId;
      case DvBulkLoadPluginSupport.ORACLE_DB_PLUGIN_ID ->
          // NLSSORT binary path — collation argument unused; BINARY sort.
          "SELECT v FROM (SELECT '"
              + ten
              + "' AS v FROM dual UNION ALL SELECT '"
              + hundred
              + "' FROM dual) t ORDER BY NLSSORT(v, 'NLS_SORT=BINARY')";
      default -> null;
    };
  }

  static boolean probeRowsMatchHopOrder(List<Object[]> rows) {
    if (rows == null || rows.size() < 2) {
      return false;
    }
    String first = rowString(rows.get(0));
    String second = rowString(rows.get(1));
    if (first == null || second == null) {
      return false;
    }
    // Hop order: TEN then HUNDRED
    return PROBE_TEN.equals(first) && PROBE_HUNDRED.equals(second);
  }

  private static String rowString(Object[] row) {
    if (row == null || row.length == 0 || row[0] == null) {
      return null;
    }
    return row[0].toString();
  }

  static String escapeSqlLiteral(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("'", "''");
  }

  private static TargetHashOrderPlan sqlOrderByWithCollation(
      DatabaseMeta databaseMeta, String quotedHashExpression, String collation, String rationale) {
    // COLLATE makes the ORDER BY expression differ from the bare select-list column; wrap DISTINCT
    // on every engine (PostgreSQL: "ORDER BY expressions must appear in select list").
    return sqlOrderBy(
        orderBySuffix(databaseMeta, quotedHashExpression, collation),
        rationale,
        needsDistinctOrderBySubqueryForCollate());
  }

  private static TargetHashOrderPlan sqlOrderBy(
      String orderBySqlSuffix, String rationale, boolean wrapDistinctSubquery) {
    return new TargetHashOrderPlan(
        TargetHashOrderMode.SQL_ORDER_BY, orderBySqlSuffix, rationale, wrapDistinctSubquery);
  }

  /** Safe default when no plan is available: Hop SortRows, no SQL ORDER BY. */
  public static TargetHashOrderPlan hopSortFallback() {
    return hopSort("fallback: Hop SortRows");
  }

  private static TargetHashOrderPlan hopSort(String rationale) {
    return new TargetHashOrderPlan(TargetHashOrderMode.HOP_SORT_ROWS, null, rationale, false);
  }

  private static String cacheKey(DatabaseMeta databaseMeta, HashKeyDataType hashType) {
    String name = Const.NVL(databaseMeta.getName(), "");
    String plugin = Const.NVL(databaseMeta.getPluginId(), "");
    return name + "|" + plugin + "|" + (hashType != null ? hashType.name() : "?");
  }

  /**
   * Cached plans store an ORDER BY built for a specific column expression. When reusing for another
   * link on the same connection, rebuild the suffix with the new quoted expression while keeping
   * mode/rationale/wrap flag.
   */
  private static TargetHashOrderPlan rebindQuotedExpression(
      TargetHashOrderPlan cached, String quotedHashExpression) {
    if (cached == null || !cached.useSqlOrderBy() || Utils.isEmpty(cached.orderBySqlSuffix())) {
      return cached;
    }
    // Extract COLLATE clause if present: " ORDER BY <anything> COLLATE <id>" or plain ORDER BY
    String suffix = cached.orderBySqlSuffix().trim();
    int collateIdx = suffix.toUpperCase(Locale.ROOT).indexOf(" COLLATE ");
    if (collateIdx < 0) {
      // plain ORDER BY (BINARY)
      return new TargetHashOrderPlan(
          cached.mode(),
          " ORDER BY " + quotedHashExpression,
          cached.rationale(),
          cached.wrapDistinctSubquery());
    }
    String collatePart = suffix.substring(collateIdx); // " COLLATE …"
    return new TargetHashOrderPlan(
        cached.mode(),
        " ORDER BY " + quotedHashExpression + collatePart,
        cached.rationale(),
        cached.wrapDistinctSubquery());
  }

  /** Package-visible for tests: list of registry engines that use static trust offline. */
  static List<String> staticTrustPluginIds() {
    List<String> ids = new ArrayList<>();
    ids.add(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);
    ids.add(DvBulkLoadPluginSupport.MSSQL_DB_PLUGIN_ID);
    ids.add(DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID);
    ids.add(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID);
    ids.add(DvBulkLoadPluginSupport.SINGLESTORE_DB_PLUGIN_ID);
    ids.add("MARIADB");
    return ids;
  }
}
