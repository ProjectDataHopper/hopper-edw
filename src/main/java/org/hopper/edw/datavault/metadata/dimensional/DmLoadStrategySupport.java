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
package org.hopper.edw.datavault.metadata.dimensional;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;

/** Resolves source-load baselines and the values published to SQL and pipelines. */
public final class DmLoadStrategySupport {

  public static final String MODE_RELOAD = "RELOAD";
  public static final String MODE_INITIAL = "INITIAL";
  public static final String MODE_INCREMENTAL = "INCREMENTAL";
  public static final String TIMESTAMP_MASK = "yyyy-MM-dd HH:mm:ss.SSS";
  public static final String PARAMETER_TRANSFORM = "param_dm_load";

  private static final Class<?> PKG = DmLoadStrategySupport.class;
  private static final ThreadLocal<Snapshot> ACTIVE = new ThreadLocal<>();

  private DmLoadStrategySupport() {}

  /** Values for one table's generated pipeline. */
  public record Snapshot(String mode, String loadFrom, String loadTo) {}

  public static DmLoadStrategy normalize(DmLoadStrategy strategy) {
    if (strategy == null || strategy.isDefaultEquivalent()) {
      return null;
    }
    return strategy;
  }

  public static String summary(DmLoadStrategy strategy) {
    DmLoadStrategy effective = strategy != null ? strategy : new DmLoadStrategy();
    DmLoadStrategyType type = effective.resolveStrategy();
    if (type.limitsExtract() && !Utils.isEmpty(effective.getBaselineColumn())) {
      return BaseMessages.getString(
          PKG, "DmLoadStrategySupport.Summary.WithBaseline", type.getDescription(), effective.getBaselineColumn());
    }
    return type.getDescription();
  }

  public static boolean publishVariables(DmTableBase table) {
    if (table == null) {
      return false;
    }
    DmSourceConfiguration source = table.getSourceOrDefault();
    if (source.isPipelineSource()) {
      return true;
    }
    DmLoadStrategy strategy = table.getLoadStrategy();
    DmLoadValueBinding binding =
        strategy == null ? DmLoadValueBinding.VARIABLES : strategy.resolveValueBinding();
    return binding.includesVariables();
  }

  public static boolean usesJdbcParameters(DmTableBase table) {
    if (table == null || !table.getSourceOrDefault().isSqlSource()) {
      return false;
    }
    DmLoadStrategy strategy = table.getLoadStrategy();
    if (strategy == null) {
      return false;
    }
    return strategy.resolveValueBinding().includesJdbcParameters();
  }

  public static Snapshot activeSnapshot() {
    return ACTIVE.get();
  }

  public static <T> T withSnapshot(Snapshot snapshot, Generation<T> generation) throws HopException {
    Snapshot previous = ACTIVE.get();
    ACTIVE.set(snapshot);
    try {
      return generation.run();
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(e.getMessage(), e);
    } finally {
      if (previous == null) {
        ACTIVE.remove();
      } else {
        ACTIVE.set(previous);
      }
    }
  }

  @FunctionalInterface
  public interface Generation<T> {
    T run() throws Exception;
  }

  public static void applyVariables(IVariables variables, DmLoadStrategy strategy, Snapshot snapshot) {
    if (variables == null || snapshot == null) {
      return;
    }
    DmLoadStrategy effective = strategy != null ? strategy : new DmLoadStrategy();
    variables.setVariable(effective.resolveLoadModeVariable(), snapshot.mode());
    variables.setVariable(effective.resolveLoadFromVariable(), snapshot.loadFrom());
    variables.setVariable(effective.resolveLoadToVariable(), snapshot.loadTo());
  }

  public static void addPipelineParameters(
      PipelineMeta pipelineMeta, DmLoadStrategy strategy, Snapshot snapshot) throws HopException {
    if (pipelineMeta == null || snapshot == null) {
      return;
    }
    DmLoadStrategy effective = strategy != null ? strategy : new DmLoadStrategy();
    defineParameter(
        pipelineMeta,
        effective.resolveLoadModeVariable(),
        snapshot.mode(),
        BaseMessages.getString(PKG, "DmLoadStrategySupport.Parameter.Mode"));
    defineParameter(
        pipelineMeta,
        effective.resolveLoadFromVariable(),
        snapshot.loadFrom(),
        BaseMessages.getString(PKG, "DmLoadStrategySupport.Parameter.From"));
    defineParameter(
        pipelineMeta,
        effective.resolveLoadToVariable(),
        snapshot.loadTo(),
        BaseMessages.getString(PKG, "DmLoadStrategySupport.Parameter.To"));
  }

  /**
   * Reads the target high-water mark when the strategy needs it. A missing or empty target is an
   * initial load. A non-empty target whose baseline column is entirely null fails, so a first
   * incremental run cannot append a second full copy.
   */
  public static Snapshot resolveSnapshot(
      DmTableBase table, DatabaseMeta targetDatabase, IVariables variables, Date runTimestamp)
      throws HopException {
    DmLoadStrategy strategy = table != null ? table.getLoadStrategy() : null;
    DmLoadStrategy effective = strategy != null ? strategy : new DmLoadStrategy();
    DmLoadStrategyType type = effective.resolveStrategy();
    if (!type.isExecutable()) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DmLoadStrategySupport.Error.ReservedStrategy",
              table != null ? table.getName() : "",
              type.getDescription()));
    }
    String loadTo = formatTimestamp(runTimestamp != null ? runTimestamp : new Date());
    String sentinel = effective.resolveSentinel();
    if (type == DmLoadStrategyType.INITIAL_RELOAD) {
      return new Snapshot(MODE_RELOAD, sentinel, loadTo);
    }
    if (effective.resolveBaselineKind() == DmLoadBaselineKind.SENTINEL) {
      return new Snapshot(MODE_INCREMENTAL, sentinel, loadTo);
    }
    String column = resolveName(effective.getBaselineColumn(), variables);
    if (Utils.isEmpty(column)) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DmLoadStrategySupport.Error.MissingBaselineColumn",
              table != null ? table.getName() : ""));
    }
    if (targetDatabase == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DmLoadStrategySupport.Error.MissingTargetDatabase",
              table != null ? table.getName() : ""));
    }
    String tableName =
        resolveName(
            table != null && !Utils.isEmpty(table.getTableName())
                ? table.getTableName()
                : table != null ? table.getName() : null,
            variables);
    String schema =
        Utils.isEmpty(targetDatabase.getPreferredSchemaName())
            ? null
            : resolveName(targetDatabase.getPreferredSchemaName(), variables);
    SimpleLoggingObject loggingObject =
        new SimpleLoggingObject(
            DmLoadStrategySupport.class.getSimpleName(), LoggingObjectType.GENERAL, null);
    try (Database database = new Database(loggingObject, variables, targetDatabase)) {
      database.connect();
      if (!database.checkTableExists(schema, tableName)) {
        return new Snapshot(MODE_INITIAL, sentinel, loadTo);
      }
      String sql = baselineSql(targetDatabase, variables, schema, tableName, column);
      RowMetaAndData row = database.getOneRow(sql);
      if (row == null || row.getData() == null || row.getData().length < 2) {
        return new Snapshot(MODE_INITIAL, sentinel, loadTo);
      }
      long count = toLong(row.getData()[0]);
      Object max = row.getData()[1];
      if (count <= 0) {
        return new Snapshot(MODE_INITIAL, sentinel, loadTo);
      }
      if (max == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "DmLoadStrategySupport.Error.NullBaseline",
                table != null ? table.getName() : "",
                column));
      }
      return new Snapshot(MODE_INCREMENTAL, formatValue(max), loadTo);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DmLoadStrategySupport.Error.BaselineQueryFailed",
              table != null ? table.getName() : "",
              e.getMessage()),
          e);
    }
  }

  public static Snapshot previewSnapshot(
      DmTableBase table, DatabaseMeta targetDatabase, IVariables variables, Date runTimestamp) {
    try {
      return resolveSnapshot(table, targetDatabase, variables, runTimestamp);
    } catch (Exception e) {
      DmLoadStrategy strategy = table != null ? table.getLoadStrategy() : null;
      String sentinel =
          strategy != null ? strategy.resolveSentinel() : DmLoadStrategy.DEFAULT_SENTINEL;
      return new Snapshot(MODE_RELOAD, sentinel, formatTimestamp(runTimestamp));
    }
  }

  public static String baselineSql(
      DatabaseMeta databaseMeta, IVariables variables, String schema, String table, String column) {
    String quotedColumn = databaseMeta.quoteField(column);
    String quotedTable =
        databaseMeta.getQuotedSchemaTableCombination(variables, schema, table);
    return "SELECT COUNT(*), MAX(" + quotedColumn + ") FROM " + quotedTable;
  }

  /**
   * Preview SQL with Hop variables resolved and JDBC '{name}' tokens replaced by literals. The
   * database workbench runs the text as written; generation still binds '{name}' through Table
   * Input.
   */
  public static String preparePreviewSql(
      String sql, DmLoadStrategy strategy, Snapshot snapshot, IVariables variables) {
    if (sql == null) {
      return null;
    }
    IVariables space = variables;
    org.apache.hop.core.variables.Variables child = null;
    if (snapshot != null) {
      child = new org.apache.hop.core.variables.Variables();
      if (variables != null) {
        child.initializeFrom(variables);
      }
      applyVariables(child, strategy, snapshot);
      space = child;
    }
    String resolved = space != null ? space.resolve(sql) : sql;
    if (snapshot == null || strategy == null || !strategy.resolveValueBinding().includesJdbcParameters()) {
      return resolved;
    }
    resolved = replaceToken(resolved, strategy.resolveJdbcModeParameter(), quoteString(snapshot.mode()));
    resolved =
        replaceToken(
            resolved, strategy.resolveJdbcFromParameter(), timestampLiteral(snapshot.loadFrom()));
    resolved =
        replaceToken(resolved, strategy.resolveJdbcToParameter(), timestampLiteral(snapshot.loadTo()));
    return resolved;
  }

  public static String formatTimestamp(Date date) {
    if (date == null) {
      return DmLoadStrategy.DEFAULT_SENTINEL;
    }
    return new SimpleDateFormat(TIMESTAMP_MASK).format(date);
  }

  static String formatValue(Object value) {
    if (value instanceof Date date) {
      return formatTimestamp(date);
    }
    String text = String.valueOf(value).trim();
    if (Utils.isEmpty(text) || "null".equalsIgnoreCase(text)) {
      return DmLoadStrategy.DEFAULT_SENTINEL;
    }
    // Hop's default timestamp mask is yyyy/MM/dd HH:mm:ss.SSSSSSSSS. RowGenerator parses with
    // Timestamp.valueOf, which requires dashes: yyyy-MM-dd HH:mm:ss[.fffffffff].
    return text.replace('/', '-');
  }

  static long toLong(Object value) {
    if (value == null) {
      return 0L;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  public static boolean sqlMentionsVariable(String sql, String variableName) {
    if (Utils.isEmpty(sql) || Utils.isEmpty(variableName)) {
      return false;
    }
    return sql.contains("${" + variableName + "}");
  }

  public static boolean sqlMentionsJdbcParameter(String sql, String parameterName) {
    if (Utils.isEmpty(sql) || Utils.isEmpty(parameterName)) {
      return false;
    }
    return sql.contains("{" + parameterName + "}");
  }

  private static void defineParameter(
      PipelineMeta pipelineMeta, String name, String defaultValue, String description)
      throws HopException {
    if (Utils.isEmpty(name)) {
      return;
    }
    String[] existingNames = pipelineMeta.listParameters();
    if (existingNames != null) {
      for (String existing : existingNames) {
        if (name.equals(existing)) {
          return;
        }
      }
    }
    pipelineMeta.addParameterDefinition(name, defaultValue != null ? defaultValue : "", description);
  }

  private static String resolveName(String name, IVariables variables) {
    if (name == null) {
      return null;
    }
    String resolved = variables != null ? variables.resolve(name) : name;
    return resolved != null ? resolved.trim() : null;
  }

  static String timestampLiteral(String value) {
    return "CAST(" + quoteString(value) + " AS TIMESTAMP)";
  }

  private static String quoteString(String value) {
    String text = value != null ? value : "";
    return "'" + text.replace("'", "''") + "'";
  }

  /** Replaces {@code {name}} outside quotes and comments. */
  static String replaceToken(String sql, String name, String replacement) {
    if (Utils.isEmpty(sql) || Utils.isEmpty(name) || replacement == null) {
      return sql;
    }
    String token = "{" + name + "}";
    StringBuilder out = new StringBuilder(sql.length() + replacement.length());
    int i = 0;
    while (i < sql.length()) {
      char c = sql.charAt(i);
      char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
      if (c == '-' && next == '-') {
        int end = indexOfNewline(sql, i + 2);
        if (end < 0) {
          out.append(sql.substring(i));
          break;
        }
        out.append(sql, i, end);
        i = end;
        continue;
      }
      if (c == '/' && next == '*') {
        int end = sql.indexOf("*/", i + 2);
        if (end < 0) {
          out.append(sql.substring(i));
          break;
        }
        out.append(sql, i, end + 2);
        i = end + 2;
        continue;
      }
      if (c == '\'' || c == '"') {
        int end = endOfQuoted(sql, i, c);
        out.append(sql, i, end);
        i = end;
        continue;
      }
      if (sql.startsWith(token, i)) {
        out.append(replacement);
        i += token.length();
        continue;
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }

  private static int indexOfNewline(String sql, int from) {
    int newline = sql.indexOf('\n', from);
    int carriage = sql.indexOf('\r', from);
    if (newline < 0) {
      return carriage;
    }
    if (carriage < 0) {
      return newline;
    }
    return Math.min(newline, carriage);
  }

  private static int endOfQuoted(String sql, int start, char quote) {
    int i = start + 1;
    while (i < sql.length()) {
      if (sql.charAt(i) == quote) {
        if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
          i += 2;
          continue;
        }
        return i + 1;
      }
      i++;
    }
    return sql.length();
  }
}
