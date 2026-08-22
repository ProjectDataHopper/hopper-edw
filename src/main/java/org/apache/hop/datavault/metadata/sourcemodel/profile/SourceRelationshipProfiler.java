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
package org.apache.hop.datavault.metadata.sourcemodel.profile;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationshipMultiplicity;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Profiles a {@link SourceRelationship} to suggest child/parent multiplicities using size-gated SQL
 * (never defaulting to full outer join on large tables).
 */
public final class SourceRelationshipProfiler {

  private static final Class<?> PKG = SourceRelationshipProfiler.class;

  private SourceRelationshipProfiler() {}

  public static SourceRelationshipProfileStrategy recommendStrategy(
      long childRows, long parentRows, SourceRelationshipProfileOptions options) {
    SourceRelationshipProfileOptions opts =
        options != null ? options : SourceRelationshipProfileOptions.defaults();
    long max = Math.max(Math.max(childRows, 0), Math.max(parentRows, 0));
    if (max > opts.getSampleMaxRows()) {
      return SourceRelationshipProfileStrategy.STATS_ONLY;
    }
    if (max > opts.getSmallMaxRows()) {
      return SourceRelationshipProfileStrategy.SAMPLED_KEY;
    }
    return SourceRelationshipProfileStrategy.EXACT_KEY;
  }

  public static String recommendStrategyMessage(
      long childRows, long parentRows, SourceRelationshipProfileStrategy recommended) {
    return BaseMessages.getString(
        PKG,
        "SourceRelationshipProfiler.Recommend."
            + (recommended != null ? recommended.getCode() : "EXACT_KEY"),
        Long.toString(childRows),
        Long.toString(parentRows));
  }

  public static SourceRelationshipProfileResult profile(
      SourceModel model,
      SourceRelationship relationship,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SourceRelationshipProfileOptions options)
      throws HopException {
    SourceRelationshipProfileOptions opts =
        options != null ? options : SourceRelationshipProfileOptions.defaults();
    if (model == null || relationship == null || !relationship.isValid()) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceRelationshipProfiler.Error.InvalidRelationship"));
    }
    SourceTable child = model.findTable(relationship.getChildTableName());
    SourceTable parent = model.findTable(relationship.getParentTableName());
    if (child == null || parent == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceRelationshipProfiler.Error.MissingTables"));
    }
    if (child.resolvePhysicalType() != DvSourceType.DATABASE
        || parent.resolvePhysicalType() != DvSourceType.DATABASE) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceRelationshipProfiler.Error.NotDatabase"));
    }
    if (Utils.isEmpty(child.getDatabaseName())
        || !child.getDatabaseName().equals(parent.getDatabaseName())) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceRelationshipProfiler.Error.DifferentConnections"));
    }

    DatabaseMeta databaseMeta =
        metadataProvider
            .getSerializer(DatabaseMeta.class)
            .load(variables.resolve(child.getDatabaseName()));
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SourceRelationshipProfiler.Error.ConnectionNotFound", child.getDatabaseName()));
    }

    SourceRelationshipProfileResult result = new SourceRelationshipProfileResult();
    SimpleLoggingObject logging =
        new SimpleLoggingObject("SourceRelationshipProfiler", LoggingObjectType.GENERAL, null);

    try (Database db = new Database(logging, variables, databaseMeta)) {
      db.connect();
      if (opts.getQueryTimeoutSeconds() > 0) {
        try {
          db.setQueryLimit(0);
        } catch (Exception ignored) {
          // optional
        }
      }

      long childRows = estimateRowCount(db, databaseMeta, variables, child, result, true);
      long parentRows = estimateRowCount(db, databaseMeta, variables, parent, result, false);
      result.setChildRowEstimate(childRows);
      result.setParentRowEstimate(parentRows);

      SourceRelationshipProfileStrategy strategy = opts.getStrategy();
      if (strategy == null) {
        strategy = recommendStrategy(childRows, parentRows, opts);
      }
      // Safety: never FOJ large tables.
      if (strategy == SourceRelationshipProfileStrategy.FULL_OUTER
          && Math.max(childRows, parentRows) > opts.getFullOuterMaxRows()) {
        result.addMessage(
            BaseMessages.getString(
                PKG,
                "SourceRelationshipProfiler.Warn.FullOuterTooLarge",
                Long.toString(opts.getFullOuterMaxRows())));
        strategy = recommendStrategy(childRows, parentRows, opts);
      }
      if (strategy == SourceRelationshipProfileStrategy.EXACT_KEY
          && Math.max(childRows, parentRows) > opts.getSmallMaxRows()) {
        result.addMessage(
            BaseMessages.getString(PKG, "SourceRelationshipProfiler.Warn.DowngradeToSample"));
        strategy = SourceRelationshipProfileStrategy.SAMPLED_KEY;
      }
      if (strategy == SourceRelationshipProfileStrategy.SAMPLED_KEY
          && Math.max(childRows, parentRows) > opts.getSampleMaxRows()) {
        result.addMessage(
            BaseMessages.getString(PKG, "SourceRelationshipProfiler.Warn.DowngradeToStats"));
        strategy = SourceRelationshipProfileStrategy.STATS_ONLY;
      }
      result.setStrategyUsed(strategy);
      result.addMessage(recommendStrategyMessage(childRows, parentRows, strategy));

      switch (strategy) {
        case STATS_ONLY -> applyStatsOnly(result, child, parent);
        case FULL_OUTER ->
            applyExactKeyAnalytics(
                db, databaseMeta, variables, relationship, child, parent, result, false, opts);
        case SAMPLED_KEY ->
            applyExactKeyAnalytics(
                db, databaseMeta, variables, relationship, child, parent, result, true, opts);
        case EXACT_KEY ->
            applyExactKeyAnalytics(
                db, databaseMeta, variables, relationship, child, parent, result, false, opts);
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceRelationshipProfiler.Error.ProfileFailed"), e);
    }
    return result;
  }

  private static void applyStatsOnly(
      SourceRelationshipProfileResult result, SourceTable child, SourceTable parent) {
    boolean parentPk = !parent.primaryKeyColumns().isEmpty();
    boolean childFkNonUnique = true; // FK is typically non-unique
    SourceRelationshipMultiplicity.MultiplicityPair pair =
        SourceRelationshipMultiplicityInference.fromStatsOnly(childFkNonUnique, parentPk);
    result.setChildMultiplicity(pair.child());
    result.setParentMultiplicity(pair.parent());
    result.setConfidence(SourceRelationshipProfileResult.Confidence.LOW);
    result.addMessage(BaseMessages.getString(PKG, "SourceRelationshipProfiler.StatsOnly.Note"));
  }

  private static void applyExactKeyAnalytics(
      Database db,
      DatabaseMeta databaseMeta,
      IVariables variables,
      SourceRelationship relationship,
      SourceTable child,
      SourceTable parent,
      SourceRelationshipProfileResult result,
      boolean sampled,
      SourceRelationshipProfileOptions opts)
      throws Exception {
    String childFrom = qualifiedTable(databaseMeta, variables, child);
    String parentFrom = qualifiedTable(databaseMeta, variables, parent);
    List<String> childKeys = relationship.getChildColumns();
    List<String> parentKeys = relationship.getParentColumns();

    String childKeyExpr =
        childKeys.stream().map(databaseMeta::quoteField).collect(Collectors.joining(", "));
    String parentKeyExpr =
        parentKeys.stream().map(databaseMeta::quoteField).collect(Collectors.joining(", "));
    String onClause = buildOnClause(databaseMeta, childKeys, parentKeys, "c", "p");

    String childSource = childFrom;
    String parentSource = parentFrom;
    if (sampled) {
      childSource = sampleSubquery(databaseMeta, childFrom, opts.getSampleSize());
      parentSource = sampleSubquery(databaseMeta, parentFrom, opts.getSampleSize());
      result.setConfidence(SourceRelationshipProfileResult.Confidence.MEDIUM);
      result.addMessage(
          BaseMessages.getString(
              PKG,
              "SourceRelationshipProfiler.Sampled.Note",
              Integer.toString(opts.getSampleSize())));
    } else {
      result.setConfidence(SourceRelationshipProfileResult.Confidence.HIGH);
    }

    // Null child keys.
    String nullSql =
        "SELECT COUNT(*) FROM "
            + childSource
            + " c WHERE "
            + childKeys.stream()
                .map(k -> "c." + databaseMeta.quoteField(k) + " IS NULL")
                .collect(Collectors.joining(" OR "));
    long nullCount = queryLong(db, nullSql);
    result.setChildNullKeyCount(nullCount);
    boolean childNulls = nullCount > 0;

    // Max children per parent key (among non-null children that match).
    String maxSql =
        "SELECT COALESCE(MAX(cnt), 0) FROM (SELECT COUNT(*) AS cnt FROM "
            + childSource
            + " c WHERE "
            + childKeys.stream()
                .map(k -> "c." + databaseMeta.quoteField(k) + " IS NOT NULL")
                .collect(Collectors.joining(" AND "))
            + " GROUP BY "
            + childKeys.stream()
                .map(k -> "c." + databaseMeta.quoteField(k))
                .collect(Collectors.joining(", "))
            + ") x";
    long maxChildren = queryLong(db, maxSql);
    result.setMaxChildrenPerParent(maxChildren);

    // Orphans: child rows with non-null keys and no parent.
    String orphanSql =
        "SELECT COUNT(*) FROM "
            + childSource
            + " c WHERE "
            + childKeys.stream()
                .map(k -> "c." + databaseMeta.quoteField(k) + " IS NOT NULL")
                .collect(Collectors.joining(" AND "))
            + " AND NOT EXISTS (SELECT 1 FROM "
            + parentSource
            + " p WHERE "
            + onClause
            + ")";
    long orphans = queryLong(db, orphanSql);
    result.setChildOrphanCount(orphans);

    // Parents without children.
    String lonelyParentSql =
        "SELECT COUNT(*) FROM "
            + parentSource
            + " p WHERE NOT EXISTS (SELECT 1 FROM "
            + childSource
            + " c WHERE "
            + onClause
            + ")";
    long lonelyParents = queryLong(db, lonelyParentSql);
    result.setParentWithoutChildren(lonelyParents);

    // Parent key uniqueness.
    String parentDupSql =
        "SELECT COUNT(*) FROM (SELECT 1 FROM "
            + parentSource
            + " p GROUP BY "
            + parentKeys.stream()
                .map(k -> "p." + databaseMeta.quoteField(k))
                .collect(Collectors.joining(", "))
            + " HAVING COUNT(*) > 1) d";
    long parentDups = queryLong(db, parentDupSql);
    boolean parentUnique = parentDups == 0;

    SourceRelationshipMultiplicity.MultiplicityPair pair =
        SourceRelationshipMultiplicityInference.fromMetrics(
            childNulls, maxChildren, lonelyParents > 0, parentUnique);
    result.setChildMultiplicity(pair.child());
    result.setParentMultiplicity(pair.parent());
  }

  private static String buildOnClause(
      DatabaseMeta databaseMeta,
      List<String> childKeys,
      List<String> parentKeys,
      String c,
      String p) {
    StringBuilder on = new StringBuilder();
    for (int i = 0; i < childKeys.size(); i++) {
      if (i > 0) {
        on.append(" AND ");
      }
      on.append(c)
          .append('.')
          .append(databaseMeta.quoteField(childKeys.get(i)))
          .append(" = ")
          .append(p)
          .append('.')
          .append(databaseMeta.quoteField(parentKeys.get(i)));
    }
    return on.toString();
  }

  private static String sampleSubquery(
      DatabaseMeta databaseMeta, String qualifiedTable, int sampleSize) {
    int n = Math.max(1, sampleSize);
    String plugin =
        databaseMeta.getPluginId() != null ? databaseMeta.getPluginId().toUpperCase() : "";
    if (plugin.contains("POSTGRES")) {
      return "(SELECT * FROM " + qualifiedTable + " TABLESAMPLE SYSTEM (5) LIMIT " + n + ")";
    }
    if (plugin.contains("MSSQL") || plugin.contains("SQLSERVER")) {
      return "(SELECT TOP (" + n + ") * FROM " + qualifiedTable + ")";
    }
    // MySQL / generic
    return "(SELECT * FROM " + qualifiedTable + " LIMIT " + n + ")";
  }

  private static long estimateRowCount(
      Database db,
      DatabaseMeta databaseMeta,
      IVariables variables,
      SourceTable table,
      SourceRelationshipProfileResult result,
      boolean child)
      throws Exception {
    Long estimated = tryDialectEstimate(db, databaseMeta, variables, table);
    if (estimated != null && estimated >= 0) {
      if (child) {
        result.setChildRowCountExact(false);
      } else {
        result.setParentRowCountExact(false);
      }
      return estimated;
    }
    long exact =
        queryLong(db, "SELECT COUNT(*) FROM " + qualifiedTable(databaseMeta, variables, table));
    if (child) {
      result.setChildRowCountExact(true);
    } else {
      result.setParentRowCountExact(true);
    }
    return exact;
  }

  private static Long tryDialectEstimate(
      Database db, DatabaseMeta databaseMeta, IVariables variables, SourceTable table) {
    try {
      String plugin =
          databaseMeta.getPluginId() != null ? databaseMeta.getPluginId().toUpperCase() : "";
      String schema = variables.resolve(ConstNvl(table.getSchemaName()));
      String name = variables.resolve(table.getTableName());
      if (Utils.isEmpty(name)) {
        name = table.getName();
      }
      if (plugin.contains("POSTGRES")) {
        String sql =
            "SELECT COALESCE(c.reltuples, -1)::bigint FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE c.relname = '"
                + escapeLiteral(name)
                + "'"
                + (Utils.isEmpty(schema) ? "" : " AND n.nspname = '" + escapeLiteral(schema) + "'");
        long v = queryLong(db, sql);
        return v >= 0 ? v : null;
      }
      if (plugin.contains("MYSQL") || plugin.contains("SINGLESTORE")) {
        String sql =
            "SELECT table_rows FROM information_schema.tables WHERE table_name = '"
                + escapeLiteral(name)
                + "'"
                + (Utils.isEmpty(schema)
                    ? ""
                    : " AND table_schema = '" + escapeLiteral(schema) + "'");
        long v = queryLong(db, sql);
        return v >= 0 ? v : null;
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  private static String qualifiedTable(
      DatabaseMeta databaseMeta, IVariables variables, SourceTable table) {
    String schema = table.getSchemaName();
    String name = table.getTableName();
    if (Utils.isEmpty(name)) {
      name = table.getName();
    }
    return databaseMeta.getQuotedSchemaTableCombination(variables, schema, name);
  }

  private static long queryLong(Database db, String sql) throws Exception {
    List<Object[]> rows = db.getRows(sql, 1);
    if (rows == null || rows.isEmpty() || rows.get(0) == null || rows.get(0).length == 0) {
      return 0;
    }
    Object v = rows.get(0)[0];
    if (v == null) {
      return 0;
    }
    if (v instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(v.toString());
  }

  private static String escapeLiteral(String value) {
    return value == null ? "" : value.replace("'", "''");
  }

  private static String ConstNvl(String value) {
    return value == null ? "" : value;
  }
}
