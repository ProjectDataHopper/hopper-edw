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
package org.apache.hop.quality.profile;

import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.quality.model.DataQualityFinding;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.DataQualityRuleType;
import org.apache.hop.quality.model.QualitySeverity;

/**
 * SELECT/WITH-only SQL assertion runner for {@link DataQualityRuleType#SQL_ASSERTION}.
 *
 * <p>Not a sandbox — DB grants and trusted metadata authors are the real boundary. Security
 * controls are best-effort allowlist/denylist. MySQL executable comments ({@code /}{@code
 * *!}…{@code *}{@code /}) are rejected outright. Residual risk remains for non-executable
 * comment-split keywords, string-literal edge cases, and side-effecting functions ({@code
 * pg_sleep}, etc.).
 *
 * <p>All expect modes request at most one JDBC row ({@code setQueryLimit(1)}); authors should still
 * include server-side {@code LIMIT} when practical — the limit only caps client/driver buffering.
 *
 * <p>Parse/security rejection, timeout, and SQL errors throw {@link HopException} (infra).
 * Expectation failures return findings.
 *
 * <p>Multi-statement rejection is a literal semicolon scan (a single trailing {@code ;} is
 * allowed). Semicolons inside string or dollar-quoted literals are false-rejected under this hard
 * policy.
 */
public final class SqlAssertionRunner {

  private static final LoggingObject LOGGING_OBJECT =
      new LoggingObject("hop-quality-SqlAssertionRunner");

  public static final String EXPECT_ZERO_ROWS = "ZERO_ROWS";
  public static final String EXPECT_ONE_ROW_TRUE = "ONE_ROW_TRUE";
  public static final String EXPECT_SCALAR_EQ = "SCALAR_EQ";

  /** Placeholder expanded to the subject's quoted schema.table after variable resolution. */
  public static final String SCHEMA_TABLE_PLACEHOLDER = "${schemaTable}";

  public static final int DEFAULT_QUERY_TIMEOUT_SECONDS =
      DatabaseProfileCollector.DEFAULT_QUERY_TIMEOUT_SECONDS;

  /** JDBC max-rows cap for all expect modes (each only needs one row). */
  public static final int QUERY_ROW_LIMIT = 1;

  private static final Pattern ALLOWLIST_START = Pattern.compile("(?is)^\\s*(with|select)\\b");

  private static final Pattern DENYLIST_TOKENS =
      Pattern.compile(
          "(?is)\\b("
              + "insert|update|delete|merge|drop|alter|truncate|create|grant|revoke|"
              + "call|execute|exec|copy|do|replace|attach|detach|vacuum|analyze|"
              + "security|into|outfile|dumpfile"
              + ")\\b");

  /**
   * MySQL versioned/executable comments ({@code /}{@code *!}…{@code *}{@code /}) execute content
   * and must never be stripped before denylist checks.
   */
  private static final Pattern MYSQL_EXECUTABLE_COMMENT_OPEN = Pattern.compile("/\\*!");

  private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
  private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n\\r]*");

  private SqlAssertionRunner() {}

  /**
   * Evaluate a SQL_ASSERTION rule against the subject's physical table database.
   *
   * @return empty list when expectation holds; one finding when it fails
   * @throws HopException on security rejection, missing connection, timeout, or SQL errors
   */
  public static List<DataQualityFinding> evaluate(
      DataQualityRule rule,
      String subjectKey,
      String databaseMetaName,
      String schemaName,
      String tableName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (rule == null || !rule.isEnabled() || rule.getType() != DataQualityRuleType.SQL_ASSERTION) {
      return List.of();
    }
    if (Utils.isEmpty(databaseMetaName) || Utils.isEmpty(tableName)) {
      throw new HopException(
          "SQL_ASSERTION requires a database physical table for subject " + subjectKey);
    }

    String rawSql = rule.parameter(DataQualityRule.PARAM_SQL);
    if (Utils.isEmpty(rawSql)) {
      throw new HopException(
          "SQL_ASSERTION rule '" + ruleLabel(rule) + "' is missing parameter sql");
    }

    DatabaseMeta databaseMeta = loadDatabaseMeta(databaseMetaName, metadataProvider);
    String quotedSchemaTable =
        databaseMeta.getQuotedSchemaTableCombination(
            variables, Const.NVL(schemaName, ""), tableName);

    String prepared = prepareSql(rawSql, variables, quotedSchemaTable);
    validateSelectOnly(prepared, rule);

    String expect;
    try {
      expect = normalizeExpect(rule.parameter(DataQualityRule.PARAM_EXPECT, EXPECT_ZERO_ROWS));
    } catch (IllegalArgumentException e) {
      throw new HopException("SQL_ASSERTION rule '" + ruleLabel(rule) + "': " + e.getMessage(), e);
    }
    String expectValue = rule.parameter(DataQualityRule.PARAM_EXPECT_VALUE);
    if (EXPECT_SCALAR_EQ.equals(expect) && expectValue == null) {
      throw new HopException(
          "SQL_ASSERTION rule '"
              + ruleLabel(rule)
              + "' expect=SCALAR_EQ requires parameter expectValue");
    }

    int timeoutSeconds = parseTimeout(rule.parameter(DataQualityRule.PARAM_QUERY_TIMEOUT));

    try (Database db = new Database(LOGGING_OBJECT, variables, databaseMeta)) {
      db.connect();
      try {
        db.setStatementQueryTimeoutSeconds(timeoutSeconds);
      } catch (Exception ignored) {
        // driver may not support statement timeouts
      }
      // Cap client/driver buffering: all expect modes only need the first row.
      // Server-side work may still run without an author LIMIT in the SQL.
      try {
        db.setQueryLimit(QUERY_ROW_LIMIT);
      } catch (Exception ignored) {
        // best-effort
      }

      ResultSet rs = db.openQuery(prepared);
      if (rs == null) {
        throw new HopException(
            "SQL_ASSERTION rule '" + ruleLabel(rule) + "' returned no result set");
      }
      try {
        return evaluateResult(rule, subjectKey, expect, expectValue, rs);
      } finally {
        db.closeQuery(rs);
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          "SQL_ASSERTION rule '"
              + ruleLabel(rule)
              + "' failed: "
              + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
          e);
    }
  }

  /**
   * Variable-resolve SQL, expand {@code ${schemaTable}}, trim, strip a single trailing terminator.
   * Does not apply security checks (call {@link #validateSelectOnly} after).
   *
   * <p>Multi-statement check is a literal {@code ;} scan (not string/literal-aware). Semicolons
   * inside string literals are rejected as multi-statement under this hard policy.
   */
  public static String prepareSql(String rawSql, IVariables variables, String quotedSchemaTable)
      throws HopException {
    if (rawSql == null) {
      throw new HopException("SQL_ASSERTION sql is empty");
    }
    String resolved = variables != null ? variables.resolve(rawSql) : rawSql;
    if (quotedSchemaTable != null) {
      resolved = resolved.replace(SCHEMA_TABLE_PLACEHOLDER, quotedSchemaTable);
    }
    String trimmed = resolved.trim();
    if (trimmed.isEmpty()) {
      throw new HopException("SQL_ASSERTION sql is empty after resolve");
    }
    // Reject multi-statement: only a single trailing terminator is allowed (literal scan)
    if (trimmed.endsWith(";")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
    }
    if (trimmed.indexOf(';') >= 0) {
      throw new HopException(
          "SQL_ASSERTION rejects multi-statement SQL (semicolon not solely trailing;"
              + " semicolons inside string literals are also rejected)");
    }
    if (trimmed.isEmpty()) {
      throw new HopException("SQL_ASSERTION sql is empty after resolve");
    }
    return trimmed;
  }

  /**
   * Best-effort SELECT/WITH allowlist and token denylist. Throws on rejection (infra).
   *
   * <p>MySQL executable comments are rejected before any strip. Denylist is applied to both the raw
   * SQL and the comment-stripped form so strip cannot hide tokens. Residual risk remains for
   * non-executable comment-split keywords and side-effecting functions.
   */
  public static void validateSelectOnly(String preparedSql, DataQualityRule rule)
      throws HopException {
    if (Utils.isEmpty(preparedSql)) {
      throw new HopException("SQL_ASSERTION sql is empty");
    }
    rejectMysqlExecutableComments(preparedSql, rule);

    // Denylist on raw form first (before any strip can hide tokens)
    rejectDenylistedTokens(preparedSql, rule);

    String forCheck = stripNonExecutableSqlComments(preparedSql);
    if (Utils.isEmpty(forCheck)) {
      throw new HopException("SQL_ASSERTION sql is empty after comment strip");
    }
    if (!ALLOWLIST_START.matcher(forCheck).find()) {
      throw new HopException(
          "SQL_ASSERTION rule '"
              + ruleLabel(rule)
              + "' must start with SELECT or WITH (got non-SELECT SQL)");
    }
    // Denylist again on stripped form (comment-split keywords may rejoin after strip)
    rejectDenylistedTokens(forCheck, rule);
  }

  /**
   * Reject MySQL versioned/executable comments that the engine would execute while a naive stripper
   * would hide denylisted tokens from validation.
   */
  public static void rejectMysqlExecutableComments(String sql, DataQualityRule rule)
      throws HopException {
    if (sql == null) {
      return;
    }
    if (MYSQL_EXECUTABLE_COMMENT_OPEN.matcher(sql).find()) {
      throw new HopException(
          "SQL_ASSERTION rule '" + ruleLabel(rule) + "' rejects MySQL executable comments (/*!)");
    }
  }

  private static void rejectDenylistedTokens(String sql, DataQualityRule rule) throws HopException {
    if (sql == null) {
      return;
    }
    Matcher deny = DENYLIST_TOKENS.matcher(sql);
    if (deny.find()) {
      throw new HopException(
          "SQL_ASSERTION rule '"
              + ruleLabel(rule)
              + "' contains denylisted token: "
              + deny.group(1));
    }
  }

  /**
   * Best-effort strip of ordinary {@code --} line and {@code /}{@code *}…{@code *}{@code /} block
   * comments before allow/deny checks. Does not strip MySQL executable comments (those are rejected
   * earlier).
   */
  public static String stripNonExecutableSqlComments(String sql) {
    if (sql == null) {
      return "";
    }
    // Remove ordinary block and line comments for allow/deny scanning (best-effort).
    // MySQL /*! … */ is rejected before this path is used.
    String withoutBlocks = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
    String withoutLines = LINE_COMMENT.matcher(withoutBlocks).replaceAll(" ");
    return withoutLines.trim();
  }

  /**
   * @deprecated use {@link #stripNonExecutableSqlComments(String)}; kept for test compatibility
   */
  @Deprecated
  public static String stripLeadingSqlComments(String sql) {
    return stripNonExecutableSqlComments(sql);
  }

  public static String normalizeExpect(String raw) {
    if (Utils.isEmpty(raw)) {
      return EXPECT_ZERO_ROWS;
    }
    String upper = raw.trim().toUpperCase(Locale.ROOT);
    return switch (upper) {
      case EXPECT_ZERO_ROWS, EXPECT_ONE_ROW_TRUE, EXPECT_SCALAR_EQ -> upper;
      default ->
          throw new IllegalArgumentException(
              "Unknown SQL_ASSERTION expect mode: "
                  + raw
                  + " (expected ZERO_ROWS, ONE_ROW_TRUE, or SCALAR_EQ)");
    };
  }

  public static int parseTimeout(String raw) {
    if (Utils.isEmpty(raw)) {
      return DEFAULT_QUERY_TIMEOUT_SECONDS;
    }
    try {
      int value = Integer.parseInt(raw.trim());
      return Math.max(0, value);
    } catch (NumberFormatException e) {
      return DEFAULT_QUERY_TIMEOUT_SECONDS;
    }
  }

  /** Evaluate expectation against an open result set (fetches at most one row). */
  public static List<DataQualityFinding> evaluateResult(
      DataQualityRule rule, String subjectKey, String expect, String expectValue, ResultSet rs)
      throws Exception {
    boolean hasRow = rs.next();
    Object firstCol = hasRow ? rs.getObject(1) : null;
    return evaluateExpectation(rule, subjectKey, expect, expectValue, hasRow, firstCol);
  }

  /** Pure expectation evaluation (no JDBC). Public for unit tests. */
  public static List<DataQualityFinding> evaluateExpectation(
      DataQualityRule rule,
      String subjectKey,
      String expect,
      String expectValue,
      boolean hasRow,
      Object firstCol)
      throws HopException {
    return switch (expect) {
      case EXPECT_ZERO_ROWS -> {
        if (!hasRow) {
          yield List.of();
        }
        yield List.of(
            finding(
                rule,
                subjectKey,
                "SQL assertion expected zero rows but received at least one",
                "row present: " + stringify(firstCol),
                "ZERO_ROWS",
                metric("expect", EXPECT_ZERO_ROWS)));
      }
      case EXPECT_ONE_ROW_TRUE -> {
        if (!hasRow) {
          yield List.of(
              finding(
                  rule,
                  subjectKey,
                  "SQL assertion expected one row evaluating to true but got zero rows",
                  "0 rows",
                  "ONE_ROW_TRUE",
                  metric("expect", EXPECT_ONE_ROW_TRUE)));
        }
        if (isTruthy(firstCol)) {
          yield List.of();
        }
        yield List.of(
            finding(
                rule,
                subjectKey,
                "SQL assertion expected first column to be true",
                stringify(firstCol),
                "true",
                metric("expect", EXPECT_ONE_ROW_TRUE)));
      }
      case EXPECT_SCALAR_EQ -> {
        if (!hasRow) {
          yield List.of(
              finding(
                  rule,
                  subjectKey,
                  "SQL assertion expected scalar equality but got zero rows",
                  "0 rows",
                  "SCALAR_EQ " + expectValue,
                  metrics("expect", EXPECT_SCALAR_EQ, "expectValue", expectValue)));
        }
        if (scalarEquals(firstCol, expectValue)) {
          yield List.of();
        }
        yield List.of(
            finding(
                rule,
                subjectKey,
                "SQL assertion scalar value does not match expectValue",
                stringify(firstCol),
                expectValue,
                metrics("expect", EXPECT_SCALAR_EQ, "expectValue", expectValue)));
      }
      default -> throw new HopException("Unknown SQL_ASSERTION expect mode: " + expect);
    };
  }

  public static boolean isTruthy(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof Number n) {
      return n.doubleValue() != 0.0d;
    }
    String s = String.valueOf(value).trim();
    if (s.isEmpty()) {
      return false;
    }
    return "true".equalsIgnoreCase(s)
        || "t".equalsIgnoreCase(s)
        || "yes".equalsIgnoreCase(s)
        || "y".equalsIgnoreCase(s)
        || "1".equals(s);
  }

  /**
   * Strict string equality: {@code String.valueOf(actual)} vs {@code expectValue}. Numeric JDBC
   * types are not normalized ({@code 42.0} / {@code BigDecimal("42.00")} do not match {@code
   * "42"}). Authors should cast/format in SQL when needed.
   */
  public static boolean scalarEquals(Object actual, String expectValue) {
    if (expectValue == null) {
      return actual == null;
    }
    if (actual == null) {
      return false;
    }
    return expectValue.equals(String.valueOf(actual));
  }

  private static DataQualityFinding finding(
      DataQualityRule rule,
      String subjectKey,
      String message,
      String actual,
      String expected,
      Map<String, String> metrics) {
    QualitySeverity severity =
        rule.getSeverity() != null ? rule.getSeverity() : QualitySeverity.BLOCKING;
    return DataQualityFinding.builder()
        .subjectKey(subjectKey)
        .ruleId(rule.getId())
        .ruleName(rule.getName())
        .type(rule.getType())
        .severity(severity)
        .fieldName(rule.getFieldName())
        .message(message)
        .actualSummary(actual)
        .expectedSummary(expected)
        .metrics(metrics != null ? metrics : Map.of())
        .build();
  }

  private static Map<String, String> metric(String k, String v) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(k, v);
    return map;
  }

  private static Map<String, String> metrics(String k1, String v1, String k2, String v2) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }

  private static String stringify(Object value) {
    return value == null ? "null" : String.valueOf(value);
  }

  private static String ruleLabel(DataQualityRule rule) {
    if (rule == null) {
      return "?";
    }
    if (!Utils.isEmpty(rule.getName())) {
      return rule.getName();
    }
    if (!Utils.isEmpty(rule.getId())) {
      return rule.getId();
    }
    return "SQL_ASSERTION";
  }

  private static DatabaseMeta loadDatabaseMeta(
      String databaseMetaName, IHopMetadataProvider metadataProvider) throws HopException {
    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(databaseMetaName);
    } catch (Exception e) {
      throw new HopException("Error loading database connection '" + databaseMetaName + "'", e);
    }
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + databaseMetaName + "' was not found");
    }
    return databaseMeta;
  }
}
