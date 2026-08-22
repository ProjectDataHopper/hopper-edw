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
package org.apache.hop.datavault.metadata.dimensional.dbimport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmBridge;
import org.apache.hop.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAttribute;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionScdType;
import org.apache.hop.datavault.metadata.dimensional.DmFact;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmFactMeasure;
import org.apache.hop.datavault.metadata.dimensional.DmFactlessFact;
import org.apache.hop.datavault.metadata.dimensional.DmNaturalKeyField;
import org.apache.hop.datavault.metadata.dimensional.DmScdUpdatePolicy;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Imports existing database tables as draft dimensional model tables.
 *
 * <p>Column and table-name heuristics produce a starting point for human review — the same draft
 * contract as {@link org.apache.hop.datavault.metadata.dimensional.publish.DvToDimensionalPublish}.
 */
public final class DmDatabaseTableImportSupport {

  private static final Class<?> PKG = DmDatabaseTableImportSupport.class;

  private DmDatabaseTableImportSupport() {}

  public static DmDatabaseImportResult importTables(
      DimensionalModel model,
      DatabaseMeta databaseMeta,
      DmDatabaseImportOptions options,
      List<String> tableNames,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmDatabaseTableImportSupport.Error.NoModel"));
    }
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmDatabaseTableImportSupport.Error.NoDatabase"));
    }
    if (tableNames == null || tableNames.isEmpty()) {
      return DmDatabaseImportResult.empty();
    }

    DmDatabaseImportOptions resolvedOptions =
        options != null ? options : DmDatabaseImportOptions.defaults();
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    List<IDmTable> imported = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    ILoggingObject loggingObject =
        new SimpleLoggingObject("DmDatabaseTableImport", LoggingObjectType.GENERAL, null);
    String schemaName = variables != null ? variables.resolve(resolvedOptions.getSchemaName()) : "";

    try (Database database = new Database(loggingObject, variables, databaseMeta)) {
      database.connect();
      int index = 0;
      for (String rawTableName : tableNames) {
        String tableName = stripTableNameQuotes(rawTableName);
        if (Utils.isEmpty(tableName)) {
          continue;
        }
        try {
          IRowMeta rowMeta = database.getTableFieldsMeta(schemaName, tableName);
          if (rowMeta == null || rowMeta.isEmpty()) {
            errors.add(
                BaseMessages.getString(
                    PKG, "DmDatabaseTableImportSupport.Error.NoColumns", tableName));
            continue;
          }
          IDmTable table =
              buildTableFromRowMeta(
                  tableName, rowMeta, model, config, resolvedOptions, variables, index);
          if (model.findTable(table.getName()) != null) {
            warnings.add(
                BaseMessages.getString(
                    PKG,
                    "DmDatabaseTableImportSupport.Warning.DuplicateMetadataName",
                    table.getName(),
                    tableName));
            table.setName(uniqueMetadataName(model, table.getName()));
          }
          positionImportedTable(table, resolvedOptions, index);
          imported.add(table);
          index++;
        } catch (Exception e) {
          errors.add(
              BaseMessages.getString(
                  PKG,
                  "DmDatabaseTableImportSupport.Error.TableImportFailed",
                  tableName,
                  e.getMessage()));
        }
      }
    } catch (HopDatabaseException e) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmDatabaseTableImportSupport.Error.DatabaseConnection"), e);
    }

    if (!Utils.isEmpty(resolvedOptions.getDatabaseName())
        && Utils.isEmpty(config.getSourceDatabase())) {
      config.setSourceDatabase(resolvedOptions.getDatabaseName());
    }

    return new DmDatabaseImportResult(imported, warnings, errors);
  }

  public static IDmTable buildTableFromRowMeta(
      String tableName,
      IRowMeta rowMeta,
      DimensionalModel model,
      DimensionalConfiguration config,
      DmDatabaseImportOptions options,
      IVariables variables,
      int layoutIndex) {
    DmTableType tableType = inferTableType(tableName);
    return switch (tableType) {
      case DIMENSION -> buildDimension(tableName, rowMeta, config, options, variables);
      case FACT -> buildFact(tableName, rowMeta, model, config, variables);
      case BRIDGE -> buildBridge(tableName, rowMeta, model, variables);
      case FACTLESS_FACT -> buildFactlessFact(tableName, rowMeta, model, variables);
      default -> buildDimension(tableName, rowMeta, config, options, variables);
    };
  }

  public static DmTableType inferTableType(String tableName) {
    String normalized = normalizeTableName(tableName);
    if (normalized.startsWith("bridge_")) {
      return DmTableType.BRIDGE;
    }
    if (normalized.startsWith("f_factless_") || normalized.startsWith("factless_")) {
      return DmTableType.FACTLESS_FACT;
    }
    if (normalized.startsWith("f_") || normalized.startsWith("fact_")) {
      return DmTableType.FACT;
    }
    if (normalized.startsWith("d_") || normalized.startsWith("dim_")) {
      return DmTableType.DIMENSION;
    }
    return DmTableType.DIMENSION;
  }

  public static String inferMetadataName(String tableName, DmTableType tableType) {
    String normalized = normalizeTableName(tableName);
    return switch (tableType) {
      case DIMENSION ->
          normalized.startsWith("dim_") ? normalized : "dim_" + stripPrefix(normalized, "d_");
      case FACT ->
          normalized.startsWith("fact_") ? normalized : "fact_" + stripPrefix(normalized, "f_");
      case BRIDGE -> normalized.startsWith("bridge_") ? normalized : "bridge_" + normalized;
      case FACTLESS_FACT ->
          normalized.startsWith("factless_")
              ? normalized
              : "factless_" + stripPrefix(normalized, "f_factless_");
      default -> normalized;
    };
  }

  private static DmDimension buildDimension(
      String tableName,
      IRowMeta rowMeta,
      DimensionalConfiguration config,
      DmDatabaseImportOptions options,
      IVariables variables) {
    DmDimension dimension = new DmDimension();
    dimension.setName(inferMetadataName(tableName, DmTableType.DIMENSION));
    dimension.setTableName(tableName);
    dimension.setDescription(
        BaseMessages.getString(
            PKG, "DmDatabaseTableImportSupport.Dimension.Description", tableName));
    DmDimensionScdType defaultScdType =
        options != null && options.getDefaultDimensionScdType() != null
            ? options.getDefaultDimensionScdType()
            : DmDimensionScdType.TYPE1;
    DmScdUpdatePolicy attributePolicy =
        defaultScdType == DmDimensionScdType.TYPE2
            ? DmScdUpdatePolicy.TYPE2
            : DmScdUpdatePolicy.TYPE1;

    Set<String> standardColumns = standardWarehouseColumns(config, variables);
    List<String> sourceColumns = new ArrayList<>();
    String naturalKey = null;

    for (IValueMeta valueMeta : rowMeta.getValueMetaList()) {
      if (valueMeta == null || Utils.isEmpty(valueMeta.getName())) {
        continue;
      }
      String fieldName = valueMeta.getName();
      if (standardColumns.contains(fieldName.toLowerCase(Locale.ROOT))) {
        continue;
      }
      sourceColumns.add(fieldName);
      if (naturalKey == null && isNaturalKeyCandidate(fieldName, valueMeta)) {
        naturalKey = fieldName;
      }
    }

    if (!Utils.isEmpty(naturalKey)) {
      dimension.getNaturalKeys().add(new DmNaturalKeyField(naturalKey));
    }

    for (String fieldName : sourceColumns) {
      if (fieldName.equals(naturalKey)) {
        continue;
      }
      dimension.getAttributes().add(new DmDimensionAttribute(fieldName, attributePolicy));
    }

    dimension.getSourceOrDefault().setSourceSql(buildSelectSql(sourceColumns, tableName));
    return dimension;
  }

  private static DmFact buildFact(
      String tableName,
      IRowMeta rowMeta,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables) {
    DmFact fact = new DmFact();
    fact.setName(inferMetadataName(tableName, DmTableType.FACT));
    fact.setTableName(tableName);
    fact.setDescription(
        BaseMessages.getString(PKG, "DmDatabaseTableImportSupport.Fact.Description", tableName));

    Set<String> standardColumns = standardWarehouseColumns(config, variables);
    List<String> sourceColumns = new ArrayList<>();

    for (IValueMeta valueMeta : rowMeta.getValueMetaList()) {
      if (valueMeta == null || Utils.isEmpty(valueMeta.getName())) {
        continue;
      }
      String fieldName = valueMeta.getName();
      if (standardColumns.contains(fieldName.toLowerCase(Locale.ROOT))) {
        continue;
      }
      sourceColumns.add(fieldName);
      if (isForeignKeyColumn(fieldName, valueMeta)) {
        String dimensionName = resolveDimensionNameForForeignKey(fieldName, model);
        DmFactDimensionRole role = new DmFactDimensionRole(dimensionName, fieldName);
        role.setSourceFieldName(sourceFieldNameForForeignKey(fieldName));
        fact.getDimensionRoles().add(role);
      } else if (isMeasureColumn(valueMeta)) {
        fact.getMeasures().add(new DmFactMeasure(fieldName, true));
      }
    }

    fact.getSourceOrDefault().setSourceSql(buildSelectSql(sourceColumns, tableName));
    return fact;
  }

  private static DmFactlessFact buildFactlessFact(
      String tableName, IRowMeta rowMeta, DimensionalModel model, IVariables variables) {
    DmFactlessFact factless = new DmFactlessFact();
    factless.setName(inferMetadataName(tableName, DmTableType.FACTLESS_FACT));
    factless.setTableName(tableName);
    factless.setDescription(
        BaseMessages.getString(
            PKG, "DmDatabaseTableImportSupport.Factless.Description", tableName));

    List<String> sourceColumns = new ArrayList<>();
    for (IValueMeta valueMeta : rowMeta.getValueMetaList()) {
      if (valueMeta == null || Utils.isEmpty(valueMeta.getName())) {
        continue;
      }
      String fieldName = valueMeta.getName();
      sourceColumns.add(fieldName);
      if (isForeignKeyColumn(fieldName, valueMeta)) {
        String dimensionName = resolveDimensionNameForForeignKey(fieldName, model);
        DmFactDimensionRole role = new DmFactDimensionRole(dimensionName, fieldName);
        role.setSourceFieldName(sourceFieldNameForForeignKey(fieldName));
        factless.getDimensionRoles().add(role);
      }
    }

    factless.getSourceOrDefault().setSourceSql(buildSelectSql(sourceColumns, tableName));
    return factless;
  }

  private static DmBridge buildBridge(
      String tableName, IRowMeta rowMeta, DimensionalModel model, IVariables variables) {
    DmBridge bridge = new DmBridge();
    bridge.setName(inferMetadataName(tableName, DmTableType.BRIDGE));
    bridge.setTableName(tableName);
    bridge.setDescription(
        BaseMessages.getString(PKG, "DmDatabaseTableImportSupport.Bridge.Description", tableName));

    List<String> sourceColumns = new ArrayList<>();
    for (IValueMeta valueMeta : rowMeta.getValueMetaList()) {
      if (valueMeta == null || Utils.isEmpty(valueMeta.getName())) {
        continue;
      }
      String fieldName = valueMeta.getName();
      sourceColumns.add(fieldName);
      if (isForeignKeyColumn(fieldName, valueMeta)) {
        bridge
            .getDimensionRefs()
            .add(
                new DmBridgeDimensionRef(
                    resolveDimensionNameForForeignKey(fieldName, model), fieldName));
      } else if (isMeasureColumn(valueMeta) && Utils.isEmpty(bridge.getWeightField())) {
        bridge.setWeightField(fieldName);
      }
    }

    bridge.getSourceOrDefault().setSourceSql(buildSelectSql(sourceColumns, tableName));
    return bridge;
  }

  private static void positionImportedTable(
      IDmTable table, DmDatabaseImportOptions options, int index) {
    if (!(table instanceof DmTableBase tableBase) || options == null) {
      return;
    }
    int column = index % 3;
    int row = index / 3;
    tableBase.setLocation(
        new Point(
            options.getLayoutStartX() + column * options.getLayoutColumnWidth(),
            options.getLayoutStartY() + row * options.getLayoutRowHeight()));
  }

  private static String uniqueMetadataName(DimensionalModel model, String baseName) {
    String candidate = baseName;
    int suffix = 2;
    while (model.findTable(candidate) != null) {
      candidate = baseName + "_" + suffix++;
    }
    return candidate;
  }

  private static Set<String> standardWarehouseColumns(
      DimensionalConfiguration config, IVariables variables) {
    DimensionalConfiguration resolved = config != null ? config : new DimensionalConfiguration();
    Set<String> columns = new LinkedHashSet<>();
    addLower(columns, resolved.resolveDimKeyField(variables));
    addLower(columns, resolved.resolveVersionField(variables));
    addLower(columns, resolved.resolveDateFromField(variables));
    addLower(columns, resolved.resolveDateToField(variables));
    addLower(columns, resolved.resolveLoadDateField(variables));
    addLower(columns, resolved.resolveCurrentFlagField(variables));
    return columns;
  }

  private static void addLower(Set<String> columns, String value) {
    if (!Utils.isEmpty(value)) {
      columns.add(value.trim().toLowerCase(Locale.ROOT));
    }
  }

  private static boolean isNaturalKeyCandidate(String fieldName, IValueMeta valueMeta) {
    String normalized = fieldName.toLowerCase(Locale.ROOT);
    if (normalized.endsWith("_key")) {
      return false;
    }
    if (normalized.endsWith("_id") || normalized.endsWith("_code") || normalized.equals("id")) {
      return true;
    }
    return valueMeta.isString() || valueMeta.isInteger();
  }

  private static boolean isForeignKeyColumn(String fieldName, IValueMeta valueMeta) {
    return valueMeta.isInteger() && fieldName.toLowerCase(Locale.ROOT).endsWith("_key");
  }

  private static boolean isMeasureColumn(IValueMeta valueMeta) {
    return valueMeta.isNumeric();
  }

  private static String resolveDimensionNameForForeignKey(
      String foreignKeyColumn, DimensionalModel model) {
    String stem = foreignKeyColumn;
    if (stem.toLowerCase(Locale.ROOT).endsWith("_key")) {
      stem = stem.substring(0, stem.length() - 4);
    }
    String candidate = "dim_" + stem;
    if (model != null && model.findTable(candidate) != null) {
      return candidate;
    }
    String tableCandidate = "d_" + stem;
    if (model != null) {
      for (IDmTable table : model.getTables()) {
        if (table != null && tableCandidate.equalsIgnoreCase(table.getTableName())) {
          return table.getName();
        }
      }
    }
    return candidate;
  }

  private static String sourceFieldNameForForeignKey(String foreignKeyColumn) {
    String stem = foreignKeyColumn;
    if (stem.toLowerCase(Locale.ROOT).endsWith("_key")) {
      stem = stem.substring(0, stem.length() - 4);
    }
    return stem;
  }

  private static String buildSelectSql(List<String> columns, String tableName) {
    if (columns == null || columns.isEmpty()) {
      return BaseMessages.getString(
          PKG, "DmDatabaseTableImportSupport.DraftSourceSql.Empty", tableName);
    }
    return BaseMessages.getString(
        PKG, "DmDatabaseTableImportSupport.DraftSourceSql", String.join(", ", columns), tableName);
  }

  private static String normalizeTableName(String tableName) {
    if (Utils.isEmpty(tableName)) {
      return "";
    }
    String normalized = tableName.trim().toLowerCase(Locale.ROOT);
    int dotIndex = normalized.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex < normalized.length() - 1) {
      normalized = normalized.substring(dotIndex + 1);
    }
    return normalized;
  }

  private static String stripPrefix(String value, String prefix) {
    if (Utils.isEmpty(value) || Utils.isEmpty(prefix)) {
      return value;
    }
    return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
  }

  private static String stripTableNameQuotes(String tableName) {
    if (Utils.isEmpty(tableName)) {
      return tableName;
    }
    String trimmed = tableName.trim();
    if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
        || (trimmed.startsWith("`") && trimmed.endsWith("`"))
        || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }
}
