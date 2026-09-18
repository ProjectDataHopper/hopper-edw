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
package org.hopper.edw.datavault.busmatrix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmBusinessProcessRef;
import org.hopper.edw.datavault.metadata.dimensional.DmConformedDimensionRef;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionLoadStrategySupport;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionResolutionSupport;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.DmFactRangeDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField;
import org.hopper.edw.datavault.metadata.dimensional.DmRangeDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceConfiguration;
import org.hopper.edw.datavault.metadata.dimensional.DmTableBase;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.datavault.resourcedefinition.ResourceDefinitionGroupResolver;

/** Builds a {@link BusMatrix} from dimensional models on a resource definition group. */
public final class BusMatrixBuilder {

  private BusMatrixBuilder() {}

  public static BusMatrix build(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (group == null) {
      throw new HopException("Resource definition group is required");
    }
    List<DimensionalModel> models = new ArrayList<>();
    List<String> loadWarnings = new ArrayList<>();
    for (String modelFile : group.getDimensionalModelFiles()) {
      if (Utils.isEmpty(modelFile)) {
        continue;
      }
      try {
        models.add(
            ResourceDefinitionGroupResolver.loadDimensionalModel(
                modelFile, variables, metadataProvider));
      } catch (Exception e) {
        loadWarnings.add(
            modelFile + ": " + Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
      }
    }
    BusMatrix matrix = build(group.getName(), models, variables, metadataProvider);
    if (loadWarnings.isEmpty()) {
      return matrix;
    }
    List<String> warnings = new ArrayList<>(loadWarnings);
    warnings.addAll(matrix.getWarnings());
    return new BusMatrix(matrix.getGroupName(), matrix.getRows(), matrix.getColumns(), warnings);
  }

  public static BusMatrix build(
      String groupName,
      List<DimensionalModel> models,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    List<String> warnings = new ArrayList<>();
    Map<String, ColumnAcc> columns = new LinkedHashMap<>();
    List<RowAcc> rows = new ArrayList<>();

    List<DimensionalModel> safeModels = models != null ? models : List.of();
    for (DimensionalModel model : safeModels) {
      if (model == null) {
        continue;
      }
      collectPhysicalColumns(model, variables, columns);
    }

    for (DimensionalModel model : safeModels) {
      if (model == null) {
        continue;
      }
      for (IDmTable table : model.getTables()) {
        if (!(table instanceof IDmFactLikeTable fact)) {
          continue;
        }
        RowAcc row = newRow(model, fact, variables);
        addRoles(row, model, fact, variables, metadataProvider, columns, warnings);
        rows.add(row);
      }
    }

    rows.sort(
        Comparator.comparing((RowAcc r) -> r.business, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(r -> r.level1, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(r -> r.level2, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(r -> r.level3, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(r -> r.factName, String.CASE_INSENSITIVE_ORDER));

    List<BusMatrixColumn> columnList = new ArrayList<>();
    for (ColumnAcc acc : columns.values()) {
      columnList.add(acc.toColumn());
    }
    columnList.sort(Comparator.comparing(BusMatrixColumn::label, String.CASE_INSENSITIVE_ORDER));

    List<BusMatrixRow> rowList = new ArrayList<>();
    for (RowAcc acc : rows) {
      rowList.add(acc.toRow());
    }
    return new BusMatrix(Const.NVL(groupName, ""), rowList, columnList, warnings);
  }

  private static void collectPhysicalColumns(
      DimensionalModel model, IVariables variables, Map<String, ColumnAcc> columns) {
    for (IDmTable table : model.getTables()) {
      if (table instanceof DmDimension
          || table instanceof DmJunkDimension
          || table instanceof DmRangeDimension) {
        ColumnAcc acc = columnFor(table, model, variables);
        columns.putIfAbsent(acc.key, acc);
        applyConformedLabel(model, table, acc);
      }
    }
  }

  private static void addRoles(
      RowAcc row,
      DimensionalModel model,
      IDmFactLikeTable fact,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Map<String, ColumnAcc> columns,
      List<String> warnings) {
    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role == null) {
        continue;
      }
      mark(
          row,
          resolveColumn(
              model, role.getDimensionTableName(), variables, metadataProvider, columns, warnings),
          Const.NVL(role.getDimensionTableName(), "dimension"));
    }
    for (DmFactJunkDimensionRole role : fact.getJunkDimensionRolesOrEmpty()) {
      if (role == null) {
        continue;
      }
      mark(
          row,
          resolveColumn(
              model,
              role.getJunkDimensionTableName(),
              variables,
              metadataProvider,
              columns,
              warnings),
          Const.NVL(role.getJunkDimensionTableName(), "junk"));
    }
    for (DmFactRangeDimensionRole role : fact.getRangeDimensionRolesOrEmpty()) {
      if (role == null) {
        continue;
      }
      mark(
          row,
          resolveColumn(
              model,
              role.getRangeDimensionTableName(),
              variables,
              metadataProvider,
              columns,
              warnings),
          Const.NVL(role.getRangeDimensionTableName(), "range"));
    }
  }

  private static void mark(RowAcc row, ColumnAcc column, String roleName) {
    if (column == null) {
      return;
    }
    row.cells.computeIfAbsent(column.key, k -> new ArrayList<>()).add(roleName);
  }

  private static ColumnAcc resolveColumn(
      DimensionalModel model,
      String canvasName,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Map<String, ColumnAcc> columns,
      List<String> warnings) {
    if (Utils.isEmpty(canvasName)) {
      return null;
    }
    String resolvedName = resolve(canvasName, variables);
    IDmTable local = model.findTable(resolvedName);
    if (local instanceof DmDimensionAlias alias) {
      DmDimension target =
          DmDimensionResolutionSupport.resolveAliasTarget(
              model, alias, variables, metadataProvider);
      if (target != null) {
        ColumnAcc acc = columnFor(target, model, variables);
        ColumnAcc existing = columns.putIfAbsent(acc.key, acc);
        return existing != null ? existing : acc;
      }
      String warning =
          "Unresolved dimension alias '"
              + Const.NVL(alias.getName(), resolvedName)
              + "' on model "
              + Const.NVL(model.getName(), model.getFilename());
      if (!warnings.contains(warning)) {
        warnings.add(warning);
      }
      ColumnAcc fallback = fallbackColumn(resolvedName, model);
      columns.putIfAbsent(fallback.key, fallback);
      return columns.get(fallback.key);
    }
    if (local instanceof DmDimension
        || local instanceof DmJunkDimension
        || local instanceof DmRangeDimension) {
      ColumnAcc acc = columnFor(local, model, variables);
      applyConformedLabel(model, local, acc);
      ColumnAcc existing = columns.putIfAbsent(acc.key, acc);
      return existing != null ? existing : acc;
    }
    DmDimension resolved =
        DmDimensionResolutionSupport.resolveDimension(
            model, resolvedName, variables, metadataProvider);
    if (resolved != null) {
      ColumnAcc acc = columnFor(resolved, model, variables);
      ColumnAcc existing = columns.putIfAbsent(acc.key, acc);
      return existing != null ? existing : acc;
    }
    ColumnAcc fallback = fallbackColumn(resolvedName, model);
    columns.putIfAbsent(fallback.key, fallback);
    return columns.get(fallback.key);
  }

  private static ColumnAcc columnFor(IDmTable table, DimensionalModel model, IVariables variables) {
    String physical = resolve(Const.NVL(table.getTableName(), table.getName()), variables);
    String name = Const.NVL(table.getName(), physical);
    String type = table.getTableType() != null ? table.getTableType().name() : "";
    String key = physical.toLowerCase(Locale.ROOT);
    List<String> naturalKeys = List.of();
    String scdNature = "";
    if (table instanceof DmDimension dim) {
      naturalKeys =
          dim.getNaturalKeysOrEmpty().stream()
              .map(DmNaturalKeyField::getFieldName)
              .filter(f -> !Utils.isEmpty(f))
              .toList();
      var scd = DmDimensionLoadStrategySupport.resolveDerivedScdType(dim);
      scdNature = scd != null && "TYPE2".equalsIgnoreCase(scd.name()) ? "SCD Type 2" : "SCD Type 1";
    } else if (table instanceof DmJunkDimension) {
      scdNature = "Junk Dimension";
    } else if (table instanceof DmRangeDimension) {
      scdNature = "Range Dimension";
    }
    String sourceType = "";
    String sourceDetail = "";
    if (table instanceof DmTableBase tableBase) {
      DmSourceConfiguration src = tableBase.getSourceOrDefault();
      sourceType = src.resolveSourceType().name();
      sourceDetail = resolveSourceDetail(src, variables);
    }
    return new ColumnAcc(
        key,
        name,
        physical,
        type,
        Const.NVL(model.getFilename(), ""),
        name,
        naturalKeys,
        scdNature,
        sourceType,
        sourceDetail);
  }

  private static ColumnAcc fallbackColumn(String name, DimensionalModel model) {
    String key = Const.NVL(name, "").toLowerCase(Locale.ROOT);
    return new ColumnAcc(
        key,
        name,
        name,
        "UNRESOLVED",
        Const.NVL(model.getFilename(), ""),
        name,
        List.of(),
        "",
        "",
        "");
  }

  private static void applyConformedLabel(DimensionalModel model, IDmTable table, ColumnAcc acc) {
    if (model == null || table == null || acc == null) {
      return;
    }
    String physical = Const.NVL(table.getTableName(), table.getName());
    for (DmConformedDimensionRef ref : model.getConformedDimensionsOrEmpty()) {
      if (ref == null) {
        continue;
      }
      if (physical.equals(ref.getDimensionTableName()) && !Utils.isEmpty(ref.getLogicalName())) {
        acc.label = ref.getLogicalName();
        return;
      }
    }
  }

  private static RowAcc newRow(
      DimensionalModel model, IDmFactLikeTable fact, IVariables variables) {
    DmBusinessProcessRef process = fact.getBusinessProcessOrEmpty();
    RowAcc row = new RowAcc();
    row.factName = Const.NVL(fact.getName(), "");
    row.physicalTableName = Const.NVL(fact.getTableName(), row.factName);
    row.grain = Const.NVL(fact.getGrain(), "");
    row.tableType = fact.getTableType() != null ? fact.getTableType().name() : "";
    row.modelFilename = Const.NVL(model.getFilename(), "");
    row.modelName = Const.NVL(model.getName(), "");
    row.business = resolve(process.getBusiness(), variables);
    row.level1 = resolve(process.getLevel1(), variables);
    row.level2 = resolve(process.getLevel2(), variables);
    row.level3 = resolve(process.getLevel3(), variables);

    List<String> measures = new ArrayList<>();
    for (DmFactMeasure m : fact.getMeasuresOrEmpty()) {
      if (m != null && !Utils.isEmpty(m.getFieldName())) {
        String desc = m.getFieldName();
        if (!m.isAdditive()) {
          desc += " (non-additive)";
        }
        measures.add(desc);
      }
    }
    row.measures = measures;
    if (fact instanceof DmTableBase tableBase) {
      DmSourceConfiguration src = tableBase.getSourceOrDefault();
      row.sourceType = src.resolveSourceType().name();
      row.sourceDetail = resolveSourceDetail(src, variables);
    }
    return row;
  }

  private static String resolveSourceDetail(DmSourceConfiguration src, IVariables variables) {
    if (src == null) {
      return "";
    }
    if (src.isRecordDefinitionSource()) {
      return Const.NVL(src.getSourceRecordNamespace(), "")
          + "/"
          + Const.NVL(src.getSourceRecordName(), "");
    }
    if (src.isPipelineSource()) {
      return resolve(src.getSourcePipelineFile(), variables);
    }
    if (src.isSqlSource()) {
      return resolve(src.getSourceConnection(), variables);
    }
    return "";
  }

  private static String resolve(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return "";
    }
    return variables != null ? Const.NVL(variables.resolve(value), value) : value;
  }

  private static final class ColumnAcc {
    final String key;
    String label;
    final String physicalTableName;
    final String tableType;
    final String modelFilename;
    final String dimensionName;
    final List<String> naturalKeys;
    final String scdNature;
    final String sourceType;
    final String sourceDetail;

    ColumnAcc(
        String key,
        String label,
        String physicalTableName,
        String tableType,
        String modelFilename,
        String dimensionName,
        List<String> naturalKeys,
        String scdNature,
        String sourceType,
        String sourceDetail) {
      this.key = key;
      this.label = label;
      this.physicalTableName = physicalTableName;
      this.tableType = tableType;
      this.modelFilename = modelFilename;
      this.dimensionName = dimensionName;
      this.naturalKeys = naturalKeys != null ? List.copyOf(naturalKeys) : List.of();
      this.scdNature = Const.NVL(scdNature, "");
      this.sourceType = Const.NVL(sourceType, "");
      this.sourceDetail = Const.NVL(sourceDetail, "");
    }

    BusMatrixColumn toColumn() {
      return new BusMatrixColumn(
          key,
          label,
          physicalTableName,
          tableType,
          modelFilename,
          dimensionName,
          naturalKeys,
          scdNature,
          sourceType,
          sourceDetail);
    }
  }

  private static final class RowAcc {
    String factName;
    String physicalTableName;
    String grain;
    String tableType;
    String modelFilename;
    String modelName;
    String business = "";
    String level1 = "";
    String level2 = "";
    String level3 = "";
    List<String> measures = List.of();
    String sourceType = "";
    String sourceDetail = "";
    final Map<String, List<String>> cells = new LinkedHashMap<>();

    BusMatrixRow toRow() {
      Map<String, BusMatrixCell> mapped = new LinkedHashMap<>();
      for (Map.Entry<String, List<String>> entry : cells.entrySet()) {
        mapped.put(entry.getKey(), new BusMatrixCell(entry.getValue()));
      }
      return new BusMatrixRow(
          factName,
          physicalTableName,
          grain,
          tableType,
          modelFilename,
          modelName,
          business,
          level1,
          level2,
          level3,
          measures,
          sourceType,
          sourceDetail,
          mapped);
    }
  }
}
