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
package org.apache.hop.datavault.metadata.businessvault;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.jinja.BvSqlJinjaRenderResult;
import org.apache.hop.datavault.jinja.BvSqlJinjaSupport;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Model-check rules for SQL-sourced Business Vault tables ({@link BvBusinessTable}): templates,
 * sources, refs, cycles, and light SQL hygiene.
 */
public final class BvSqlValidationSupport {

  private static final Class<?> PKG = BvSqlValidationSupport.class;

  /** Detects residual Jinja-like braces after known macros are removed. */
  private static final Pattern RESIDUAL_TEMPLATE =
      Pattern.compile("\\{\\{|\\}\\}", Pattern.CASE_INSENSITIVE);

  private BvSqlValidationSupport() {}

  /**
   * Validates one business table. Does not report model-wide SQL ref cycles — use {@link
   * #validateModelSqlGraph(List, BusinessVaultModel)}.
   */
  public static void validate(
      List<ICheckResult> remarks,
      BvBusinessTable table,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || table == null) {
      return;
    }

    if (Utils.isEmpty(table.getSqlQuery()) || Utils.isEmpty(table.getSqlQuery().trim())) {
      remarks.add(error(table, "BvBusinessTable.CheckResult.MissingSql", tableLabel(table)));
      return;
    }

    if (table.getMaterialization() == null) {
      remarks.add(
          warning(
              table,
              "BvBusinessTable.CheckResult.MissingMaterializationDefaulting",
              tableLabel(table)));
    }

    if (table.getReferenceStyleOrDefault() != BvSqlReferenceStyle.DBT) {
      remarks.add(
          error(table, "BvBusinessTable.CheckResult.UnsupportedReferenceStyle", tableLabel(table)));
    }

    validateDeclaredSources(remarks, table);

    List<BvSqlRef> refs;
    if (BvSqlJinjaSupport.needsJinjaRender(table.getSqlQuery())) {
      try {
        BvSqlJinjaRenderResult rendered =
            BvSqlJinjaSupport.render(table, bvModel, dvModel, metadataProvider, variables, null);
        refs = new java.util.ArrayList<>(rendered.refs());
        table.setSqlRefs(refs);
        BvSqlRefResolver.syncDerivativesFromResolvedRefs(table, refs);
        validateRefs(remarks, table, refs);
        validateJinjaSourcesUsed(remarks, table, rendered.sourceUsages());
        validateSelfReferences(remarks, table, refs);
        validateBareTableIdentifiers(remarks, table, bvModel, dvModel, rendered.renderedSql());
      } catch (HopException e) {
        remarks.add(
            error(
                table,
                "BvBusinessTable.CheckResult.JinjaRenderFailed",
                tableLabel(table),
                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
      }
    } else {
      validateMalformedTemplates(remarks, table);
      refs = BvSqlRefResolver.syncRefsFromSql(table, bvModel, dvModel, variables, metadataProvider);
      validateRefs(remarks, table, refs);
      validateSourcesUsedInSql(remarks, table);
      validateSelfReferences(remarks, table, refs);
      validateBareTableIdentifiers(remarks, table, bvModel, dvModel);
    }
    validateTargetDatabase(remarks, table, bvModel, metadataProvider);
  }

  /**
   * Model-level SQL dependency graph checks (cycle detection once per model). Expects per-table
   * checks to have already synced {@code sqlRefs}.
   */
  public static void validateModelSqlGraph(List<ICheckResult> remarks, BusinessVaultModel bvModel) {
    if (remarks == null || bvModel == null) {
      return;
    }
    String cycle = BvSqlDependencySupport.findCycleDescription(bvModel.getTables());
    if (cycle != null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvBusinessTable.CheckResult.SqlRefCycleModel", cycle),
              null));
    }
  }

  static void validateMalformedTemplates(List<ICheckResult> remarks, BvBusinessTable table) {
    String sql = table.getSqlQuery();
    if (Utils.isEmpty(sql)) {
      return;
    }
    List<BvSqlTemplateParser.MacroOccurrence> macros = BvSqlTemplateParser.parse(sql);
    StringBuilder stripped = new StringBuilder(sql);
    for (int i = macros.size() - 1; i >= 0; i--) {
      BvSqlTemplateParser.MacroOccurrence macro = macros.get(i);
      for (int p = macro.start(); p < macro.end(); p++) {
        stripped.setCharAt(p, ' ');
      }
    }
    Matcher residual = RESIDUAL_TEMPLATE.matcher(stripped);
    if (residual.find()) {
      remarks.add(error(table, "BvBusinessTable.CheckResult.MalformedTemplate", tableLabel(table)));
    }
  }

  static void validateDeclaredSources(List<ICheckResult> remarks, BvBusinessTable table) {
    Set<String> seen = new HashSet<>();
    for (BvSqlSource source : table.getSources()) {
      if (source == null) {
        continue;
      }
      if (Utils.isEmpty(source.getSourceName())) {
        if (!Utils.isEmpty(source.getTableName())) {
          remarks.add(
              error(
                  table,
                  "BvBusinessTable.CheckResult.SourceMissingName",
                  tableLabel(table),
                  source.getTableName()));
        }
        continue;
      }
      if (Utils.isEmpty(source.getTableName())) {
        remarks.add(
            error(
                table,
                "BvBusinessTable.CheckResult.IncompleteSource",
                tableLabel(table),
                source.getSourceName()));
      }
      String key =
          source.getSourceName().trim().toLowerCase(Locale.ROOT)
              + "\0"
              + (source.getTableName() != null
                  ? source.getTableName().trim().toLowerCase(Locale.ROOT)
                  : "");
      if (!seen.add(key)) {
        remarks.add(
            warning(
                table,
                "BvBusinessTable.CheckResult.DuplicateSource",
                tableLabel(table),
                source.getSourceName(),
                source.getTableName()));
      }
    }
  }

  static void validateRefs(List<ICheckResult> remarks, BvBusinessTable table, List<BvSqlRef> refs) {
    for (String label : BvSqlRefResolver.listUnresolvedRefLabels(refs)) {
      remarks.add(
          error(table, "BvBusinessTable.CheckResult.UnresolvedRef", tableLabel(table), label));
    }
  }

  static void validateSourcesUsedInSql(List<ICheckResult> remarks, BvBusinessTable table) {
    List<BvSqlTemplateParser.MacroOccurrence> macros =
        BvSqlTemplateParser.parse(table.getSqlQuery());
    Set<String> usedSources = new HashSet<>();
    for (BvSqlTemplateParser.MacroOccurrence macro : macros) {
      if (macro.kind() != BvSqlTemplateParser.MacroKind.SOURCE) {
        continue;
      }
      String key =
          macro.sourceName().trim().toLowerCase(Locale.ROOT)
              + "\0"
              + macro.sourceTableName().trim().toLowerCase(Locale.ROOT);
      usedSources.add(key);
      BvSqlSource declared =
          BvSqlRefResolver.findSource(table, macro.sourceName(), macro.sourceTableName());
      if (declared == null) {
        remarks.add(
            error(
                table,
                "BvBusinessTable.CheckResult.MissingSourceDeclaration",
                tableLabel(table),
                macro.sourceName(),
                macro.sourceTableName()));
      }
    }

    for (BvSqlSource source : table.getSources()) {
      if (source == null || Utils.isEmpty(source.getSourceName())) {
        continue;
      }
      String key =
          source.getSourceName().trim().toLowerCase(Locale.ROOT)
              + "\0"
              + (source.getTableName() != null
                  ? source.getTableName().trim().toLowerCase(Locale.ROOT)
                  : "");
      if (!usedSources.contains(key)) {
        remarks.add(
            warning(
                table,
                "BvBusinessTable.CheckResult.UnusedSource",
                tableLabel(table),
                source.getSourceName(),
                source.getTableName()));
      }
    }
  }

  static void validateSelfReferences(
      List<ICheckResult> remarks, BvBusinessTable table, List<BvSqlRef> refs) {
    if (refs == null) {
      return;
    }
    for (BvSqlRef ref : refs) {
      if (ref == null || Utils.isEmpty(ref.getObjectName())) {
        continue;
      }
      if (isSelf(table, ref.getObjectName())) {
        remarks.add(
            error(
                table,
                "BvBusinessTable.CheckResult.SelfReference",
                tableLabel(table),
                ref.getObjectName()));
      }
    }
  }

  /**
   * Warns when SQL appears to name a known BV/DV table without using {@code ref()} / {@code
   * source()} (optional hygiene from the design plan).
   */
  static void validateJinjaSourcesUsed(
      List<ICheckResult> remarks, BvBusinessTable table, List<BvSqlSource> used) {
    Set<String> usedSources = new HashSet<>();
    if (used != null) {
      for (BvSqlSource usage : used) {
        if (usage == null || Utils.isEmpty(usage.getSourceName())) {
          continue;
        }
        String key =
            usage.getSourceName().trim().toLowerCase(Locale.ROOT)
                + "\0"
                + (usage.getTableName() != null
                    ? usage.getTableName().trim().toLowerCase(Locale.ROOT)
                    : "");
        usedSources.add(key);
        if (BvSqlRefResolver.findSource(table, usage.getSourceName(), usage.getTableName())
            == null) {
          remarks.add(
              error(
                  table,
                  "BvBusinessTable.CheckResult.MissingSourceDeclaration",
                  tableLabel(table),
                  usage.getSourceName(),
                  usage.getTableName()));
        }
      }
    }
    for (BvSqlSource source : table.getSources()) {
      if (source == null || Utils.isEmpty(source.getSourceName())) {
        continue;
      }
      String key =
          source.getSourceName().trim().toLowerCase(Locale.ROOT)
              + "\0"
              + (source.getTableName() != null
                  ? source.getTableName().trim().toLowerCase(Locale.ROOT)
                  : "");
      if (!usedSources.contains(key)) {
        remarks.add(
            warning(
                table,
                "BvBusinessTable.CheckResult.UnusedSource",
                tableLabel(table),
                source.getSourceName(),
                source.getTableName()));
      }
    }
  }

  static void validateBareTableIdentifiers(
      List<ICheckResult> remarks,
      BvBusinessTable table,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      String alreadyStrippedSql) {
    String stripped = alreadyStrippedSql;
    if (Utils.isEmpty(stripped)) {
      return;
    }
    warnBareIdentifiers(remarks, table, bvModel, dvModel, stripped);
  }

  static void validateBareTableIdentifiers(
      List<ICheckResult> remarks,
      BvBusinessTable table,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel) {
    String sql = table.getSqlQuery();
    if (Utils.isEmpty(sql)) {
      return;
    }
    warnBareIdentifiers(remarks, table, bvModel, dvModel, stripMacros(sql));
  }

  private static void warnBareIdentifiers(
      List<ICheckResult> remarks,
      BvBusinessTable table,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      String stripped) {
    Set<String> reported = new HashSet<>();

    if (bvModel != null) {
      for (IBvTable other : bvModel.getTables()) {
        if (other == null || other == table) {
          continue;
        }
        warnBareIfPresent(remarks, table, stripped, other.getName(), reported);
        warnBareIfPresent(remarks, table, stripped, other.getTableName(), reported);
      }
    }
    if (dvModel != null) {
      for (IDvTable dvTable : dvModel.getTables()) {
        if (dvTable == null) {
          continue;
        }
        warnBareIfPresent(remarks, table, stripped, dvTable.getName(), reported);
        warnBareIfPresent(remarks, table, stripped, dvTable.getTableName(), reported);
      }
    }
  }

  static void validateTargetDatabase(
      List<ICheckResult> remarks,
      BvBusinessTable table,
      BusinessVaultModel bvModel,
      IHopMetadataProvider metadataProvider) {
    if (bvModel == null) {
      return;
    }
    BusinessVaultConfiguration config = bvModel.getConfigurationOrDefault();
    if (Utils.isEmpty(config.getTargetDatabase())) {
      remarks.add(
          error(table, "BvBusinessTable.CheckResult.MissingBvTargetDatabase", tableLabel(table)));
      return;
    }
    if (metadataProvider == null) {
      return;
    }
    try {
      DatabaseMeta db = BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, config);
      if (db == null) {
        remarks.add(
            error(
                table,
                "BvBusinessTable.CheckResult.BvTargetDatabaseNotFound",
                tableLabel(table),
                config.getTargetDatabase()));
      }
    } catch (HopException e) {
      remarks.add(
          error(
              table,
              "BvBusinessTable.CheckResult.BvTargetDatabaseNotFound",
              tableLabel(table),
              config.getTargetDatabase()));
    }
  }

  private static void warnBareIfPresent(
      List<ICheckResult> remarks,
      BvBusinessTable table,
      String strippedSql,
      String candidate,
      Set<String> reported) {
    if (Utils.isEmpty(candidate) || Utils.isEmpty(strippedSql)) {
      return;
    }
    String key = candidate.toLowerCase(Locale.ROOT);
    if (!reported.add(key)) {
      return;
    }
    Pattern word =
        Pattern.compile("\\b" + Pattern.quote(candidate) + "\\b", Pattern.CASE_INSENSITIVE);
    if (word.matcher(strippedSql).find()) {
      remarks.add(
          warning(
              table,
              "BvBusinessTable.CheckResult.BareTableIdentifier",
              tableLabel(table),
              candidate));
    }
  }

  private static String stripMacros(String sql) {
    List<BvSqlTemplateParser.MacroOccurrence> macros = BvSqlTemplateParser.parse(sql);
    StringBuilder stripped = new StringBuilder(sql);
    for (int i = macros.size() - 1; i >= 0; i--) {
      BvSqlTemplateParser.MacroOccurrence macro = macros.get(i);
      for (int p = macro.start(); p < macro.end(); p++) {
        stripped.setCharAt(p, ' ');
      }
    }
    return stripped.toString();
  }

  private static boolean isSelf(BvBusinessTable table, String objectName) {
    if (table == null || Utils.isEmpty(objectName)) {
      return false;
    }
    return objectName.equalsIgnoreCase(table.getName())
        || objectName.equalsIgnoreCase(table.getTableName());
  }

  private static String tableLabel(BvBusinessTable table) {
    return table != null && !Utils.isEmpty(table.getName()) ? table.getName() : "?";
  }

  private static CheckResult error(BvBusinessTable table, String key, Object... args) {
    return new CheckResult(
        ICheckResult.TYPE_RESULT_ERROR, BaseMessages.getString(PKG, key, args), table);
  }

  private static CheckResult warning(BvBusinessTable table, String key, Object... args) {
    return new CheckResult(
        ICheckResult.TYPE_RESULT_WARNING, BaseMessages.getString(PKG, key, args), table);
  }
}
