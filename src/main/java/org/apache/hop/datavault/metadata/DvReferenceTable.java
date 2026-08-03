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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.workflow.WorkflowMeta;

/**
 * Data Vault reference (code / catalog) table: natural keys and attributes with DV load metadata,
 * without hub hash keys or satellite hashdiff semantics.
 *
 * <p>Distinct from {@link DvLinkedTable}, which is a cross-model pointer or hub alias.
 */
@Getter
@Setter
public class DvReferenceTable extends DvTableBase
    implements IDvTable, IGuiPosition, IBaseMeta, IHasName {

  private static final Class<?> PKG = DvReferenceTable.class;

  public static final String DEFAULT_PIPELINE_NAME_PREFIX = "ref-";

  private static final Point LOCATION_SOURCE = new Point(160, 160);
  private static final Point LOCATION_LOAD = new Point(160, 320);
  public static final int SPACING_WIDTH = 160;

  /**
   * Natural key columns that identify a reference row. Reuses {@link BusinessKey} (including
   * per-source field mapping); UI labels this list as "Natural keys".
   */
  @HopMetadataProperty private List<BusinessKey> naturalKeys = new ArrayList<>();

  /** Descriptive attributes stored on the reference table. */
  @HopMetadataProperty private List<SatelliteAttribute> attributes = new ArrayList<>();

  /**
   * Record source(s) that feed this table (stored by {@link DataVaultSource} name), same pattern as
   * hubs.
   */
  @HopMetadataProperty(key = "recordSource", groupKey = "recordSources")
  private List<String> recordSources = new ArrayList<>();

  @HopMetadataProperty(storeWithCode = true)
  private DvReferenceLoadMode loadMode = DvReferenceLoadMode.FULL_REPLACE;

  public DvReferenceTable() {
    super();
    this.tableType = DvTableType.REFERENCE;
  }

  public DvReferenceTable(String name) {
    super(name);
    this.tableType = DvTableType.REFERENCE;
  }

  public void setNaturalKeys(List<BusinessKey> naturalKeys) {
    if (!java.util.Objects.equals(this.naturalKeys, naturalKeys)) {
      setChanged();
    }
    this.naturalKeys = naturalKeys != null ? naturalKeys : new ArrayList<>();
  }

  public void setAttributes(List<SatelliteAttribute> attributes) {
    if (!java.util.Objects.equals(this.attributes, attributes)) {
      setChanged();
    }
    this.attributes = attributes != null ? attributes : new ArrayList<>();
  }

  public void setRecordSources(List<String> recordSources) {
    if (!java.util.Objects.equals(this.recordSources, recordSources)) {
      setChanged();
    }
    this.recordSources = recordSources != null ? recordSources : new ArrayList<>();
  }

  public void setLoadMode(DvReferenceLoadMode loadMode) {
    DvReferenceLoadMode next = loadMode != null ? loadMode : DvReferenceLoadMode.FULL_REPLACE;
    if (this.loadMode != next) {
      setChanged();
    }
    this.loadMode = next;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DvModelCheckOptions options,
      DataVaultModel model) {
    super.check(remarks, metadataProvider, variables, options, model);

    if (!DvIntegrationSupport.relaxesSourceValidation(this)) {
      if (recordSources == null || recordSources.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(PKG, "DvTableBase.CheckResult.NoRecordSource"),
                this));
      } else {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_OK,
                BaseMessages.getString(
                    PKG,
                    "DvTableBase.CheckResult.HasRecordSources",
                    String.join(", ", recordSources)),
                this));
      }
    }

    if (Utils.isEmpty(naturalKeys)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvReferenceTable.CheckResult.NoNaturalKeys"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvReferenceTable.CheckResult.HasNaturalKeys", naturalKeys.size()),
              this));
      checkNaturalKeyNames(remarks);
    }

    checkAttributeNames(remarks);
    checkNameCollisionsWithStandardColumns(remarks, variables, model);
    checkLoadMode(remarks, metadataProvider, variables, model);
  }

  private void checkLoadMode(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model) {
    DvReferenceLoadMode mode = loadMode != null ? loadMode : DvReferenceLoadMode.FULL_REPLACE;
    if (mode == DvReferenceLoadMode.MERGE) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG, "DvReferenceTable.CheckResult.LoadModeNotImplemented", mode.getCode()),
              this));
      return;
    }
    if (mode != DvReferenceLoadMode.DELETE_INSERT
        || metadataProvider == null
        || model == null
        || recordSources == null
        || recordSources.isEmpty()) {
      return;
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    String targetDb = config != null ? config.getTargetDatabase() : null;
    for (String sourceRef : recordSources) {
      if (Utils.isEmpty(sourceRef)) {
        continue;
      }
      try {
        DataVaultSource source =
            DvSourceCatalogService.resolveSource(
                variables != null ? variables.resolve(sourceRef) : sourceRef,
                model,
                variables,
                metadataProvider);
        if (source == null) {
          continue;
        }
        if (!DvReferenceDeleteInsertSupport.isSameDatabaseAsTarget(
            source, targetDb, variables, metadataProvider)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_WARNING,
                  BaseMessages.getString(
                      PKG,
                      "DvReferenceTable.CheckResult.DeleteInsertFallbackFullReplace",
                      source.getName(),
                      DvReferenceDeleteInsertSupport.describeFallbackReason(
                          source, metadataProvider)),
                  this));
        }
      } catch (HopException e) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "DvReferenceTable.CheckResult.DeleteInsertSourceCheckFailed",
                    sourceRef,
                    e.getMessage()),
                this));
      }
    }
  }

  /**
   * Natural keys that apply to the given record source. When {@link
   * BusinessKey#getRecordSourceName()} is empty the key applies to every source.
   */
  public List<BusinessKey> getNaturalKeysForSource(String sourceName, IVariables variables) {
    List<BusinessKey> result = new ArrayList<>();
    if (naturalKeys == null) {
      return result;
    }
    String resolvedSource =
        variables != null && sourceName != null ? variables.resolve(sourceName) : sourceName;
    for (BusinessKey key : naturalKeys) {
      if (key == null) {
        continue;
      }
      if (Utils.isEmpty(key.getRecordSourceName())) {
        result.add(key);
        continue;
      }
      String keySource =
          variables != null
              ? variables.resolve(key.getRecordSourceName())
              : key.getRecordSourceName();
      if (resolvedSource != null && resolvedSource.equalsIgnoreCase(keySource)) {
        result.add(key);
      }
    }
    return result;
  }

  private void checkNaturalKeyNames(List<ICheckResult> remarks) {
    Set<String> seen = new HashSet<>();
    for (BusinessKey key : naturalKeys) {
      if (key == null || Utils.isEmpty(key.getName())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(PKG, "DvReferenceTable.CheckResult.NaturalKeyNoName"),
                this));
        continue;
      }
      String normalized = key.getName().toLowerCase(Locale.ROOT);
      if (!seen.add(normalized)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DvReferenceTable.CheckResult.DuplicateNaturalKey", key.getName()),
                this));
      }
    }
  }

  private void checkAttributeNames(List<ICheckResult> remarks) {
    if (Utils.isEmpty(attributes)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(PKG, "DvReferenceTable.CheckResult.NoAttributes"),
              this));
      return;
    }
    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_OK,
            BaseMessages.getString(
                PKG, "DvReferenceTable.CheckResult.HasAttributes", attributes.size()),
            this));

    Set<String> naturalKeyNames = new HashSet<>();
    if (naturalKeys != null) {
      for (BusinessKey key : naturalKeys) {
        if (key != null && !Utils.isEmpty(key.getName())) {
          naturalKeyNames.add(key.getName().toLowerCase(Locale.ROOT));
        }
      }
    }

    Set<String> seenAttrs = new HashSet<>();
    for (SatelliteAttribute attr : attributes) {
      if (attr == null || Utils.isEmpty(attr.getName())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(PKG, "DvReferenceTable.CheckResult.AttributeNoName"),
                this));
        continue;
      }
      String normalized = attr.getName().toLowerCase(Locale.ROOT);
      if (!seenAttrs.add(normalized)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "DvReferenceTable.CheckResult.DuplicateAttribute", attr.getName()),
                this));
      }
      if (naturalKeyNames.contains(normalized)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DvReferenceTable.CheckResult.AttributeCollidesWithNaturalKey",
                    attr.getName()),
                this));
      }
    }
  }

  private void checkNameCollisionsWithStandardColumns(
      List<ICheckResult> remarks, IVariables variables, DataVaultModel model) {
    if (model == null) {
      return;
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    if (config == null) {
      return;
    }
    Set<String> reserved = new HashSet<>();
    addResolvedName(reserved, config.getLoadDateField(), variables);
    addResolvedName(reserved, config.getRecordSourceField(), variables);
    if (reserved.isEmpty()) {
      return;
    }

    if (naturalKeys != null) {
      for (BusinessKey key : naturalKeys) {
        if (key != null
            && !Utils.isEmpty(key.getName())
            && reserved.contains(key.getName().toLowerCase(Locale.ROOT))) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DvReferenceTable.CheckResult.NaturalKeyCollidesWithStandard",
                      key.getName()),
                  this));
        }
      }
    }
    if (attributes != null) {
      for (SatelliteAttribute attr : attributes) {
        if (attr != null
            && !Utils.isEmpty(attr.getName())
            && reserved.contains(attr.getName().toLowerCase(Locale.ROOT))) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DvReferenceTable.CheckResult.AttributeCollidesWithStandard",
                      attr.getName()),
                  this));
        }
      }
    }
  }

  private static void addResolvedName(Set<String> names, String raw, IVariables variables) {
    if (Utils.isEmpty(raw)) {
      return;
    }
    String resolved = variables != null ? variables.resolve(raw) : raw;
    if (!Utils.isEmpty(resolved)) {
      names.add(resolved.toLowerCase(Locale.ROOT));
    }
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DataVaultModel model)
      throws HopException {
    if (metadataProvider == null || model == null) {
      return null;
    }
    IRowMeta rowMeta = new RowMeta();
    try {
      DataVaultConfiguration config = model.getConfigurationOrDefault();

      if (naturalKeys != null) {
        for (BusinessKey key : naturalKeys) {
          if (key == null || Utils.isEmpty(key.getName())) {
            continue;
          }
          String colName = variables.resolve(key.getName());
          int type = ValueMetaFactory.getIdForValueMeta(variables.resolve(key.getDataType()));
          int length = Const.toInt(variables.resolve(key.getLength()), -1);
          int precision = Const.toInt(variables.resolve(key.getPrecision()), -1);
          IValueMeta meta = ValueMetaFactory.createValueMeta(colName, type, length, precision);
          if (meta == null || meta.getType() == IValueMeta.TYPE_NONE) {
            throw new HopException(
                "Please specify a valid data type for natural key "
                    + key.getName()
                    + " in reference table "
                    + getName());
          }
          rowMeta.addValueMeta(meta);
        }
      }

      if (attributes != null) {
        for (SatelliteAttribute attr : attributes) {
          if (attr == null || Utils.isEmpty(attr.getName())) {
            continue;
          }
          String colName = variables.resolve(attr.getName());
          int type = ValueMetaFactory.getIdForValueMeta(variables.resolve(attr.getDataType()));
          int length = Const.toInt(variables.resolve(attr.getLength()), -1);
          int precision = Const.toInt(variables.resolve(attr.getPrecision()), -1);
          IValueMeta meta = ValueMetaFactory.createValueMeta(colName, type, length, precision);
          if (meta == null || meta.getType() == IValueMeta.TYPE_NONE) {
            // Default descriptive attributes to String when type is blank.
            meta = new ValueMetaString(colName);
            if (length > 0) {
              meta.setLength(length);
            }
          }
          rowMeta.addValueMeta(meta);
        }
      }

      String loadDateField = config != null ? config.getLoadDateField() : null;
      if (Utils.isEmpty(loadDateField)) {
        loadDateField = "LOAD_DATE";
      }
      loadDateField = variables.resolve(loadDateField);
      rowMeta.addValueMeta(new ValueMetaTimestamp(loadDateField));

      String rsFieldName = config != null ? config.getRecordSourceField() : null;
      if (Utils.isEmpty(rsFieldName)) {
        rsFieldName = "RECORD_SOURCE";
      }
      rsFieldName = variables.resolve(rsFieldName);
      String lengthString =
          config != null && !Utils.isEmpty(config.getRecordSourceFieldLength())
              ? config.getRecordSourceFieldLength()
              : "100";
      lengthString = variables.resolve(lengthString);
      int rsLength = Const.toInt(lengthString, 100);
      IValueMeta rsMeta = new ValueMetaString(rsFieldName);
      rsMeta.setLength(rsLength);
      rowMeta.addValueMeta(rsMeta);

      return rowMeta;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          "Error building target table layout for reference table " + getName(), e);
    }
  }

  @Override
  protected String[] resolveShardKeyColumns(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      IRowMeta targetFields) {
    if (naturalKeys == null || naturalKeys.isEmpty()) {
      return super.resolveShardKeyColumns(metadataProvider, variables, model, targetFields);
    }
    List<String> cols = new ArrayList<>();
    for (BusinessKey key : naturalKeys) {
      if (key != null && !Utils.isEmpty(key.getName())) {
        cols.add(variables != null ? variables.resolve(key.getName()) : key.getName());
      }
    }
    return cols.toArray(new String[0]);
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      String recordSourceGroup)
      throws HopException {
    List<PipelineMeta> result = new ArrayList<>();
    try {
      if (metadataProvider == null || model == null) {
        return result;
      }
      if (DvIntegrationSupport.isExternalRead(this)) {
        return result;
      }
      if (DvIntegrationSupport.isCustomPipelines(this)) {
        return DvIntegrationSupport.loadCustomUpdatePipelines(this, metadataProvider, variables);
      }

      DvReferenceLoadMode mode = loadMode != null ? loadMode : DvReferenceLoadMode.FULL_REPLACE;
      if (mode == DvReferenceLoadMode.MERGE) {
        throw new HopException(
            "Load mode MERGE is not implemented yet for reference table "
                + getName()
                + ". Use FULL_REPLACE or DELETE_INSERT.");
      }

      List<DataVaultSource> sources =
          loadRecordSources(model, variables, metadataProvider, recordSourceGroup);
      if (sources.isEmpty()) {
        if (!Utils.isEmpty(recordSourceGroup)) {
          return result;
        }
        throw new HopException(
            "Please specify one or more record sources in reference table " + getName());
      }

      DataVaultConfiguration config = model.getConfigurationOrDefault();
      DatabaseMeta targetDatabaseMeta =
          DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, config);
      if (targetDatabaseMeta == null) {
        throw new HopException(
            "Target database is not configured for reference table " + getName());
      }
      String targetDbName = targetDatabaseMeta.getName();
      String targetTableName = !Utils.isEmpty(getTableName()) ? getTableName() : getName();
      targetTableName = variables.resolve(targetTableName);

      IRowMeta targetLayout = getTargetTableLayout(metadataProvider, variables, model);
      if (targetLayout == null || targetLayout.isEmpty()) {
        throw new HopException("Unable to resolve target layout for reference table " + getName());
      }

      boolean preferDeleteInsert = mode == DvReferenceLoadMode.DELETE_INSERT;

      for (DataVaultSource src : sources) {
        IDvSource dvSource = src.getDvSource(metadataProvider);
        if (dvSource == null) {
          throw new HopException(
              "Unable to resolve technical source for record source " + src.getName());
        }

        boolean sameDbDeleteInsert =
            preferDeleteInsert
                && DvReferenceDeleteInsertSupport.isSameDatabaseAsTarget(
                    src, targetDbName, variables, metadataProvider);

        PipelineMeta pipelineMeta = new PipelineMeta();
        String baseName = DEFAULT_PIPELINE_NAME_PREFIX + getName();
        if (sources.size() > 1 && src.getName() != null) {
          pipelineMeta.setName(baseName + "_" + src.getName());
        } else {
          pipelineMeta.setName(baseName);
        }
        // Suffix helps operators distinguish insert-only legs orchestrated after SQL delete.
        if (sameDbDeleteInsert) {
          pipelineMeta.setName(pipelineMeta.getName() + "-insert");
        }

        GeneratedPipelineMetadataSupport.stampDvElementPipeline(
            pipelineMeta, model, "reference", getName(), targetTableName, src.getName());

        DvSourcePipelineBuilder builder =
            DvSourcePipelineBuilderFactory.forReference(
                variables,
                metadataProvider,
                model,
                pipelineMeta,
                src,
                dvSource,
                this,
                new Point(LOCATION_SOURCE.x, LOCATION_SOURCE.y));
        builder.build();
        TransformMeta sourceTransform = builder.getResultTransform();
        GeneratedPipelineMetadataSupport.stampSourceRead(sourceTransform, targetDbName);

        TransformMeta constantTransform =
            addConstantForLoadDate(config, variables, pipelineMeta, loadDate, sourceTransform);

        // DELETE_INSERT same-DB: insert only (delete runs in orchestrating workflow).
        // FULL_REPLACE or DELETE_INSERT fallback: truncate + insert.
        boolean truncateTable = !sameDbDeleteInsert;
        DvTargetLoadSupport.TargetLoadContext loadCtx =
            new DvTargetLoadSupport.TargetLoadContext(
                config,
                variables,
                targetDatabaseMeta,
                targetDbName,
                targetTableName,
                pipelineMeta.getName(),
                model.getName(),
                LOCATION_LOAD.x + 2 * SPACING_WIDTH,
                LOCATION_LOAD.y);
        DvTargetLoadSupport.TargetLoadResult writeResult =
            DvTargetLoadSupport.addTargetLoad(
                loadCtx, pipelineMeta, targetLayout, constantTransform, Set.of(), truncateTable);
        if (writeResult != null && writeResult.transformMeta != null) {
          GeneratedPipelineMetadataSupport.stampWriteTarget(
              writeResult.transformMeta, "reference", getName(), targetTableName, targetDbName);
        }

        result.add(pipelineMeta);
      }
      DvGeneratedPipelineSupport.applyLayout(result);
      return result;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          "Error generating update pipeline(s) for reference table " + getName(), e);
    }
  }

  @Override
  public List<WorkflowMeta> generateUpdateWorkflows(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      String recordSourceGroup)
      throws HopException {
    List<PipelineMeta> pipelines =
        generateUpdatePipelines(metadataProvider, variables, model, loadDate, recordSourceGroup);
    if (pipelines == null || pipelines.isEmpty()) {
      return List.of();
    }

    DvReferenceLoadMode mode = loadMode != null ? loadMode : DvReferenceLoadMode.FULL_REPLACE;
    if (mode == DvReferenceLoadMode.DELETE_INSERT) {
      List<WorkflowMeta> deleteInsertWorkflows =
          buildDeleteInsertWorkflows(
              metadataProvider, variables, model, recordSourceGroup, pipelines);
      if (!deleteInsertWorkflows.isEmpty()) {
        return deleteInsertWorkflows;
      }
      // All sources fell back to FULL_REPLACE — multi-source serial if needed.
    }

    if (pipelines.size() <= 1) {
      return List.of();
    }
    String workflowName =
        DvMultiSourceUpdateWorkflowSupport.defaultWorkflowName(this, DEFAULT_PIPELINE_NAME_PREFIX);
    return DvMultiSourceUpdateWorkflowSupport.buildSerialWorkflowsIfMultiSource(
        workflowName, pipelines);
  }

  /**
   * For each same-DB DELETE_INSERT source, pairs delete SQL with the matching {@code *-insert}
   * pipeline and orchestrates SQL → insert.
   */
  private List<WorkflowMeta> buildDeleteInsertWorkflows(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      String recordSourceGroup,
      List<PipelineMeta> pipelines)
      throws HopException {
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    DatabaseMeta targetDatabaseMeta =
        DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, config);
    if (targetDatabaseMeta == null) {
      return List.of();
    }
    String targetDbName = targetDatabaseMeta.getName();
    String targetTableName = !Utils.isEmpty(getTableName()) ? getTableName() : getName();
    targetTableName = variables.resolve(targetTableName);

    List<DataVaultSource> sources =
        loadRecordSources(model, variables, metadataProvider, recordSourceGroup);
    List<DvReferenceDeleteInsertSupport.DeleteInsertStep> steps = new ArrayList<>();

    for (DataVaultSource src : sources) {
      if (!DvReferenceDeleteInsertSupport.isSameDatabaseAsTarget(
          src, targetDbName, variables, metadataProvider)) {
        continue;
      }
      IDvSource dvSource = src.getDvSource(metadataProvider);
      if (!(dvSource
          instanceof org.apache.hop.datavault.metadata.database.DvDatabaseSource dbSource)) {
        continue;
      }
      List<BusinessKey> keys = getNaturalKeysForSource(variables.resolve(src.getName()), variables);
      String deleteSql =
          DvReferenceDeleteInsertSupport.buildDeleteByNaturalKeysSql(
              targetDatabaseMeta, targetTableName, dbSource, keys, variables);

      PipelineMeta insertPipeline = findInsertPipelineForSource(pipelines, src, sources.size() > 1);
      if (insertPipeline == null) {
        throw new HopException(
            "Unable to locate insert pipeline for DELETE_INSERT source " + src.getName());
      }
      steps.add(new DvReferenceDeleteInsertSupport.DeleteInsertStep(deleteSql, insertPipeline));
    }

    if (steps.isEmpty()) {
      return List.of();
    }

    String workflowName = DEFAULT_PIPELINE_NAME_PREFIX + getName() + "-delete-insert";
    WorkflowMeta workflow =
        DvReferenceDeleteInsertSupport.buildDeleteInsertWorkflow(
            workflowName, targetDbName, steps, null);
    return List.of(workflow);
  }

  private PipelineMeta findInsertPipelineForSource(
      List<PipelineMeta> pipelines, DataVaultSource source, boolean multiSource) {
    if (pipelines == null || source == null) {
      return null;
    }
    String base = DEFAULT_PIPELINE_NAME_PREFIX + getName();
    String expected;
    if (multiSource && source.getName() != null) {
      expected = base + "_" + source.getName() + "-insert";
    } else {
      expected = base + "-insert";
    }
    for (PipelineMeta pipeline : pipelines) {
      if (pipeline != null && expected.equals(pipeline.getName())) {
        return pipeline;
      }
    }
    // Fallback: single insert pipeline name match ends with -insert
    for (PipelineMeta pipeline : pipelines) {
      if (pipeline != null
          && pipeline.getName() != null
          && pipeline.getName().endsWith("-insert")
          && pipeline.getName().startsWith(base)) {
        return pipeline;
      }
    }
    return null;
  }

  private List<DataVaultSource> loadRecordSources(
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String recordSourceGroup)
      throws HopException {
    List<DataVaultSource> sources = new ArrayList<>();
    if (recordSources == null) {
      return sources;
    }
    for (String recordSource : recordSources) {
      if (Utils.isEmpty(recordSource)) {
        continue;
      }
      DataVaultSource source =
          DvSourceCatalogService.resolveSource(
              variables.resolve(recordSource), model, variables, metadataProvider);
      if (source != null && source.matchesRecordSourceGroup(recordSourceGroup, variables)) {
        sources.add(source);
      }
    }
    return sources;
  }

  private TransformMeta addConstantForLoadDate(
      DataVaultConfiguration config,
      IVariables variables,
      PipelineMeta pipelineMeta,
      Date loadDate,
      TransformMeta predecessor)
      throws HopException {
    String loadDateField = "LOAD_DATE";
    if (config != null && !Utils.isEmpty(config.getLoadDateField())) {
      loadDateField = config.getLoadDateField();
    }
    if (variables != null) {
      loadDateField = variables.resolve(loadDateField);
    }
    if (loadDate == null) {
      throw new HopException("Please provide a load date when updating a data vault.");
    }

    ValueMetaDate valueMeta = new ValueMetaDate("ld");
    valueMeta.setConversionMask(ValueMetaBase.DEFAULT_DATE_FORMAT_MASK);
    String string = valueMeta.getString(loadDate);

    ConstantMeta constantMeta = new ConstantMeta();
    ConstantField cf = new ConstantField(loadDateField, "Date", string);
    cf.setFieldFormat(valueMeta.getConversionMask());
    constantMeta.getFields().add(cf);

    TransformMeta tm = new TransformMeta("Constant", "add_" + loadDateField, constantMeta);
    tm.setLocation(LOCATION_LOAD.x + SPACING_WIDTH, LOCATION_LOAD.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }
}
