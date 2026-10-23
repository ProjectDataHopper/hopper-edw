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
package org.hopper.edw.datavault.virtualization.dimensional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalFieldMasking;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmBridge;
import org.hopper.edw.datavault.metadata.dimensional.DmBridgeDimensionRef;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionLoadStrategySupport;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionLoadStrategySupport.DmDimensionLoadStrategy;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionOutriggerRef;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDegenerateDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.DmFactRangeDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmLayoutSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.DmRangeDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmScdUpdatePolicy;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeySupport;
import org.hopper.edw.datavault.metadata.dimensional.DmTableBase;
import org.hopper.edw.datavault.metadata.dimensional.DmTargetDatabaseSupport;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.datavault.virtualization.jdbc.HopSourceModelJdbcResultSet;

/**
 * JDBC schema of a dimensional model: modeled columns only, plus star foreign keys. Load-date and
 * load-cycle columns stay off the schema.
 */
public final class DimensionalJdbcCatalog {

  private final String schemaName;
  private final DatabaseMeta targetDatabase;
  private final String physicalSchema;
  private final Map<String, DimensionalJdbcTable> tables;
  private final List<DimensionalImportedKey> importedKeys;

  private DimensionalJdbcCatalog(
      String schemaName,
      DatabaseMeta targetDatabase,
      String physicalSchema,
      Map<String, DimensionalJdbcTable> tables,
      List<DimensionalImportedKey> importedKeys) {
    this.schemaName = schemaName;
    this.targetDatabase = targetDatabase;
    this.physicalSchema = physicalSchema;
    this.tables = tables;
    this.importedKeys = importedKeys;
  }

  public static DimensionalJdbcCatalog build(
      DimensionalModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String schemaName) {
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    DatabaseMeta target = null;
    try {
      target = DmTargetDatabaseSupport.loadTargetDatabase(metadataProvider, config);
    } catch (Exception ignored) {
      target = null;
    }
    String physicalSchema = null;
    if (target != null && !Utils.isEmpty(target.getPreferredSchemaName())) {
      physicalSchema = resolve(target.getPreferredSchemaName(), variables);
    }
    Map<String, DimensionalJdbcTable> tables = new LinkedHashMap<>();
    if (model != null) {
      for (IDmTable table : model.getTables()) {
        DimensionalJdbcTable jdbc =
            toTable(table, model, config, variables, metadataProvider, physicalSchema);
        if (jdbc != null && !Utils.isEmpty(jdbc.logicalName())) {
          tables.put(jdbc.logicalName(), jdbc);
        }
      }
    }
    List<DimensionalImportedKey> keys = new ArrayList<>();
    if (model != null) {
      for (IDmTable table : model.getTables()) {
        collectKeys(keys, table, tables);
      }
    }
    return new DimensionalJdbcCatalog(
        schemaName, target, physicalSchema, tables, List.copyOf(keys));
  }

  public String schemaName() {
    return schemaName;
  }

  public DatabaseMeta targetDatabase() {
    return targetDatabase;
  }

  public String physicalSchema() {
    return physicalSchema;
  }

  public List<DimensionalJdbcTable> tables() {
    return List.copyOf(tables.values());
  }

  public DimensionalJdbcTable table(String logicalName) {
    if (Utils.isEmpty(logicalName)) {
      return null;
    }
    DimensionalJdbcTable direct = tables.get(logicalName);
    if (direct != null) {
      return direct;
    }
    for (DimensionalJdbcTable table : tables.values()) {
      if (table.logicalName().equalsIgnoreCase(logicalName)) {
        return table;
      }
    }
    return null;
  }

  public List<DimensionalImportedKey> importedKeys(String foreignTable, String parentTable) {
    List<DimensionalImportedKey> matched = new ArrayList<>();
    for (DimensionalImportedKey key : importedKeys) {
      if (!matches(foreignTable, key.fkTable())) {
        continue;
      }
      if (!matches(parentTable, key.pkTable())) {
        continue;
      }
      matched.add(key);
    }
    return matched;
  }

  private static boolean matches(String pattern, String name) {
    if (Utils.isEmpty(pattern) || "%".equals(pattern) || "*".equals(pattern)) {
      return true;
    }
    return name != null && name.equalsIgnoreCase(pattern);
  }

  private static DimensionalJdbcTable toTable(
      IDmTable table,
      DimensionalModel model,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String physicalSchema) {
    if (table instanceof DmRangeDimension || !(table instanceof DmTableBase base)) {
      return null;
    }
    if (Utils.isEmpty(base.getName())) {
      return null;
    }
    if (table instanceof DmDimension dimension) {
      return dimensionTable(dimension, base, config, variables, physicalSchema);
    }
    if (table instanceof DmDimensionAlias alias) {
      DmDimension target =
          DmDimensionResolutionSupport.resolveAliasTarget(
              model, alias, variables, metadataProvider);
      if (target == null) {
        return null;
      }
      DimensionalJdbcTable resolved =
          dimensionTable(target, target, config, variables, physicalSchema);
      if (resolved == null) {
        return null;
      }
      return new DimensionalJdbcTable(
          base.getName(),
          physicalSchema,
          resolved.physicalTable(),
          remarks(base, "Dimension alias"),
          resolved.columns());
    }
    if (table instanceof DmJunkDimension junk) {
      return junkTable(junk, base, config, variables, physicalSchema);
    }
    if (table instanceof DmBridge bridge) {
      return bridgeTable(bridge, base, variables, physicalSchema);
    }
    if (table instanceof IDmFactLikeTable fact) {
      return factTable(fact, base, variables, physicalSchema);
    }
    return null;
  }

  private static DimensionalJdbcTable dimensionTable(
      DmDimension dimension,
      DmTableBase base,
      DimensionalConfiguration config,
      IVariables variables,
      String physicalSchema) {
    dimension.normalizeLegacyScdType();
    List<DimensionalJdbcColumn> columns = new ArrayList<>();
    boolean surrogate = DmSurrogateKeySupport.usesSurrogateColumn(dimension);
    String surrogateField =
        DmSurrogateKeySupport.resolveSurrogateKeyField(dimension, config, variables);
    if (surrogate && !Utils.isEmpty(surrogateField)) {
      add(columns, surrogateField, IValueMeta.TYPE_INTEGER, true, null);
    }
    for (DmNaturalKeyField key : dimension.getNaturalKeysOrEmpty()) {
      if (key == null || Utils.isEmpty(key.getFieldName())) {
        continue;
      }
      add(
          columns,
          resolve(key.getFieldName(), variables),
          IValueMeta.TYPE_STRING,
          !surrogate,
          DimensionalFieldMasking.patternOrNull(key.getMaskingPatternName()));
    }
    DmDimensionLoadStrategy strategy = DmDimensionLoadStrategySupport.resolveLoadStrategy(dimension);
    if (strategy == DmDimensionLoadStrategy.PURE_TYPE2
        || strategy == DmDimensionLoadStrategy.DIMENSION_LOOKUP) {
      add(columns, config.resolveDateFromField(variables), IValueMeta.TYPE_TIMESTAMP, false, null);
      add(columns, config.resolveDateToField(variables), IValueMeta.TYPE_TIMESTAMP, false, null);
    }
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      if (attribute == null) {
        continue;
      }
      String field = DmDimensionLoadStrategySupport.resolveTargetFieldName(attribute, variables);
      String pattern = DimensionalFieldMasking.patternOrNull(attribute.getMaskingPatternName());
      add(columns, field, IValueMeta.TYPE_STRING, false, pattern);
      if (attribute.getScdUpdatePolicy() == DmScdUpdatePolicy.TYPE3_PREVIOUS) {
        add(
            columns,
            DmLayoutSupport.resolvePreviousFieldName(attribute, variables),
            IValueMeta.TYPE_STRING,
            false,
            pattern);
      }
    }
    for (DmDimensionOutriggerRef outrigger : dimension.getOutriggersOrEmpty()) {
      if (outrigger == null) {
        continue;
      }
      add(
          columns,
          resolve(outrigger.getForeignKeyColumn(), variables),
          IValueMeta.TYPE_INTEGER,
          false,
          null);
    }
    return new DimensionalJdbcTable(
        base.getName(),
        physicalSchema,
        physicalName(base, variables),
        remarks(base, "Dimension"),
        List.copyOf(columns));
  }

  private static DimensionalJdbcTable junkTable(
      DmJunkDimension junk,
      DmTableBase base,
      DimensionalConfiguration config,
      IVariables variables,
      String physicalSchema) {
    List<DimensionalJdbcColumn> columns = new ArrayList<>();
    String surrogate = DmSurrogateKeySupport.resolveJunkSurrogateKeyField(junk, config, variables);
    add(columns, surrogate, IValueMeta.TYPE_INTEGER, true, null);
    for (DmNaturalKeyField key : junk.getKeyFieldsOrEmpty()) {
      if (key == null) {
        continue;
      }
      add(
          columns,
          resolve(key.getFieldName(), variables),
          IValueMeta.TYPE_STRING,
          false,
          DimensionalFieldMasking.patternOrNull(key.getMaskingPatternName()));
    }
    return new DimensionalJdbcTable(
        base.getName(),
        physicalSchema,
        physicalName(base, variables),
        remarks(base, "Junk dimension"),
        List.copyOf(columns));
  }

  private static DimensionalJdbcTable bridgeTable(
      DmBridge bridge, DmTableBase base, IVariables variables, String physicalSchema) {
    List<DimensionalJdbcColumn> columns = new ArrayList<>();
    for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
      if (ref == null) {
        continue;
      }
      add(
          columns,
          resolve(ref.getForeignKeyColumn(), variables),
          IValueMeta.TYPE_INTEGER,
          false,
          null);
    }
    add(columns, resolve(bridge.getWeightField(), variables), IValueMeta.TYPE_NUMBER, false, null);
    return new DimensionalJdbcTable(
        base.getName(),
        physicalSchema,
        physicalName(base, variables),
        remarks(base, "Bridge"),
        List.copyOf(columns));
  }

  private static DimensionalJdbcTable factTable(
      IDmFactLikeTable fact, DmTableBase base, IVariables variables, String physicalSchema) {
    List<DimensionalJdbcColumn> columns = new ArrayList<>();
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role == null) {
        continue;
      }
      add(
          columns,
          resolve(role.getForeignKeyColumn(), variables),
          IValueMeta.TYPE_INTEGER,
          false,
          null);
    }
    for (DmFactJunkDimensionRole role : fact.getJunkDimensionRolesOrEmpty()) {
      if (role == null) {
        continue;
      }
      add(
          columns,
          resolve(role.getForeignKeyColumn(), variables),
          IValueMeta.TYPE_INTEGER,
          false,
          null);
    }
    for (DmFactRangeDimensionRole role : fact.getRangeDimensionRolesOrEmpty()) {
      if (role == null) {
        continue;
      }
      add(
          columns,
          resolve(role.getTargetFieldName(), variables),
          IValueMeta.TYPE_STRING,
          false,
          null);
    }
    for (DmFactDegenerateDimension degenerate : fact.getDegenerateDimensionsOrEmpty()) {
      if (degenerate == null) {
        continue;
      }
      add(
          columns,
          resolve(degenerate.getFieldName(), variables),
          IValueMeta.TYPE_STRING,
          false,
          DimensionalFieldMasking.patternOrNull(degenerate.getMaskingPatternName()));
    }
    for (DmFactMeasure measure : fact.getMeasuresOrEmpty()) {
      if (measure == null) {
        continue;
      }
      add(
          columns,
          resolve(measure.getFieldName(), variables),
          IValueMeta.TYPE_BIGNUMBER,
          false,
          DimensionalFieldMasking.patternOrNull(measure.getMaskingPatternName()));
    }
    return new DimensionalJdbcTable(
        base.getName(),
        physicalSchema,
        physicalName(base, variables),
        remarks(base, "Fact"),
        List.copyOf(columns));
  }

  private static void collectKeys(
      List<DimensionalImportedKey> keys, IDmTable table, Map<String, DimensionalJdbcTable> tables) {
    if (table instanceof IDmFactLikeTable fact) {
      String factName = table.getName();
      for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
        if (role == null) {
          continue;
        }
        addKey(
            keys,
            tables,
            factName,
            role.getForeignKeyColumn(),
            role.getDimensionTableName(),
            "fk_" + factName + "_" + role.getForeignKeyColumn());
      }
      for (DmFactJunkDimensionRole role : fact.getJunkDimensionRolesOrEmpty()) {
        if (role == null) {
          continue;
        }
        addKey(
            keys,
            tables,
            factName,
            role.getForeignKeyColumn(),
            role.getJunkDimensionTableName(),
            "fk_" + factName + "_" + role.getForeignKeyColumn());
      }
    }
    if (table instanceof DmBridge bridge) {
      for (DmBridgeDimensionRef ref : bridge.getDimensionRefsOrEmpty()) {
        if (ref == null) {
          continue;
        }
        addKey(
            keys,
            tables,
            bridge.getName(),
            ref.getForeignKeyColumn(),
            ref.getDimensionTableName(),
            "fk_" + bridge.getName() + "_" + ref.getForeignKeyColumn());
      }
    }
    if (table instanceof DmDimension dimension) {
      for (DmDimensionOutriggerRef ref : dimension.getOutriggersOrEmpty()) {
        if (ref == null) {
          continue;
        }
        addKey(
            keys,
            tables,
            dimension.getName(),
            ref.getForeignKeyColumn(),
            ref.getDimensionTableName(),
            "fk_" + dimension.getName() + "_" + ref.getForeignKeyColumn());
      }
    }
  }

  private static void addKey(
      List<DimensionalImportedKey> keys,
      Map<String, DimensionalJdbcTable> tables,
      String fkTable,
      String fkColumn,
      String pkTable,
      String fkName) {
    if (Utils.isEmpty(fkTable) || Utils.isEmpty(fkColumn) || Utils.isEmpty(pkTable)) {
      return;
    }
    DimensionalJdbcTable parent = find(tables, pkTable);
    if (parent == null) {
      return;
    }
    String pkColumn = null;
    for (DimensionalJdbcColumn column : parent.columns()) {
      if (column.primaryKey()) {
        pkColumn = column.name();
        break;
      }
    }
    if (Utils.isEmpty(pkColumn)) {
      return;
    }
    keys.add(new DimensionalImportedKey(fkTable, fkColumn, parent.logicalName(), pkColumn, 1, fkName));
  }

  private static DimensionalJdbcTable find(Map<String, DimensionalJdbcTable> tables, String name) {
    DimensionalJdbcTable direct = tables.get(name);
    if (direct != null) {
      return direct;
    }
    for (DimensionalJdbcTable table : tables.values()) {
      if (table.logicalName().equalsIgnoreCase(name)) {
        return table;
      }
    }
    return null;
  }

  private static void add(
      List<DimensionalJdbcColumn> columns,
      String name,
      int hopType,
      boolean primaryKey,
      String maskingPattern) {
    if (Utils.isEmpty(name)) {
      return;
    }
    for (DimensionalJdbcColumn existing : columns) {
      if (existing.name().equalsIgnoreCase(name)) {
        return;
      }
    }
    int type = hopType > 0 ? hopType : IValueMeta.TYPE_STRING;
    columns.add(
        new DimensionalJdbcColumn(
            name,
            type,
            HopSourceModelJdbcResultSet.hopToSqlType(type),
            primaryKey,
            maskingPattern));
  }

  private static String physicalName(DmTableBase base, IVariables variables) {
    if (!Utils.isEmpty(base.getTableName())) {
      return resolve(base.getTableName(), variables);
    }
    return resolve(base.getName(), variables);
  }

  private static String remarks(DmTableBase base, String fallback) {
    return !Utils.isEmpty(base.getDescription()) ? base.getDescription() : fallback;
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    String resolved = variables != null ? variables.resolve(value) : value;
    return resolved != null ? resolved.trim() : null;
  }

  /** JDBC type name for a Hop type, used by the servlet column payload. */
  public static String typeName(int hopType) {
    return HopSourceModelJdbcResultSet.hopToTypeName(
        hopType > 0 ? hopType : IValueMeta.TYPE_STRING);
  }
}
