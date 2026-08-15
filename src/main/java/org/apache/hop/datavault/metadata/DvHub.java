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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Condition;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.ValueMetaAndData;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.transform.dvhashkey.DvHashKeyMeta;
import org.apache.hop.datavault.transform.dvhashkey.DvHashKeyMetaFactory;
import org.apache.hop.datavault.transform.mergerowsplus.MergeRowsPlusMeta;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.concatfields.ConcatField;
import org.apache.hop.pipeline.transforms.concatfields.ConcatFieldsMeta;
import org.apache.hop.pipeline.transforms.concatfields.ExtraFields;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.jspecify.annotations.NonNull;

/**
 * Data Vault 2.0 Hub metadata definition.
 *
 * <p>A Hub represents a core business concept (e.g. Customer, Product, Order). It contains the
 * business key(s) and the surrogate hash key derived from them.
 */
@GuiPlugin
@Getter
@Setter
public class DvHub extends DvTableBase implements IDvTable, IGuiPosition, IBaseMeta, IHasName {
  private static final Class<?> PKG = DvHub.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_HUB_DIALOG";

  private static final Point LOCATION_START_LINE_1 = new Point(160, 48);
  private static final Point LOCATION_START_LINE_2 = new Point(160, 160);
  private static final Point LOCATION_START_LINE_3 = new Point(160, 320);

  public static final int SPACING_WIDTH = 160;

  /**
   * The business keys that uniquely identify the hub instance. Composite keys are supported by
   * having multiple entries (order matters for hashing).
   */
  // List of business keys. The @GuiWidgetElement annotation is omitted because
  // GuiElementType does not (currently) include a LIST type. Nested POJO lists are
  // supported via @HopMetadataProperty and will be handled by the metadata editor / serialization.
  @HopMetadataProperty private List<BusinessKey> businessKeys = new ArrayList<>();

  /**
   * The name of the hash key (surrogate key) column for this hub in the target Data Vault tables.
   * Replaces the previous global "hash key suffix" setting in the DataVaultConfiguration.
   */
  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.TEXT,
      label = "i18n::DvHub.HashKeyFieldName.Label",
      toolTip = "i18n::DvHub.HashKeyFieldName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String hashKeyFieldName;

  /**
   * Optional per-hub name for the record source column (overrides the global one from
   * DataVaultConfiguration). Supports variables.
   */
  @GuiWidgetElement(
      order = "0510",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvHub.RecordSourceFieldName.Label",
      toolTip = "i18n::DvHub.RecordSourceFieldName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String recordSourceFieldName;

  /**
   * List of referenced {@link DataVaultSource} metadata elements (stored by name) that can feed
   * this table. A Hub can have multiple sources.
   */
  @HopMetadataProperty(key = "recordSource", groupKey = "recordSources")
  protected List<String> recordSources = new ArrayList<>();

  /**
   * When false, generated loads must not insert placeholder hub rows for this table (external or
   * curated identity). Null/absent (existing models) means allowed.
   */
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @HopMetadataProperty
  private Boolean allowInferredInsert;

  public DvHub() {
    super();
    this.tableType = DvTableType.HUB;
  }

  public DvHub(String name) {
    super(name);
    this.tableType = DvTableType.HUB;
  }

  /** Absent value (legacy models) means inferred inserts are allowed. */
  public boolean isAllowInferredInsert() {
    return allowInferredInsert == null || allowInferredInsert;
  }

  /**
   * Hop XML deserialization looks up this setter by the field type ({@link Boolean}), not {@code
   * boolean}.
   */
  public void setAllowInferredInsert(Boolean allowInferredInsert) {
    this.allowInferredInsert = allowInferredInsert;
  }

  public void setBusinessKeys(List<BusinessKey> businessKeys) {
    if (!java.util.Objects.equals(this.businessKeys, businessKeys)) {
      setChanged();
    }
    this.businessKeys = businessKeys;
  }

  public void setHashKeyFieldName(String hashKeyFieldName) {
    if (!java.util.Objects.equals(this.hashKeyFieldName, hashKeyFieldName)) {
      setChanged();
    }
    this.hashKeyFieldName = hashKeyFieldName;
  }

  public void setRecordSourceFieldName(String recordSourceFieldName) {
    if (!java.util.Objects.equals(this.recordSourceFieldName, recordSourceFieldName)) {
      setChanged();
    }
    this.recordSourceFieldName = recordSourceFieldName;
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

    if (Utils.isEmpty(businessKeys)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvHub.CheckResult.NoBusinessKeys"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvHub.CheckResult.HasBusinessKeys", businessKeys.size()),
              this));
      for (BusinessKey bk : businessKeys) {
        if (Utils.isEmpty(bk.getName())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(PKG, "DvHub.CheckResult.BusinessKeyNoName"),
                  this));
        }

        // Target DDL / catalog publish need a concrete Hop (or SQL) type on the vault BK column.
        String resolvedDataType =
            variables != null
                ? variables.resolve(Const.NVL(bk.getDataType(), ""))
                : bk.getDataType();
        if (Utils.isEmpty(resolvedDataType)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG, "DvHub.CheckResult.BusinessKeyNoDataType", Const.NVL(bk.getName(), "?")),
                  this));
        } else if (!DvDataTypeSupport.isKnownDataTypeLabel(resolvedDataType)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DvHub.CheckResult.BusinessKeyInvalidDataType",
                      Const.NVL(bk.getName(), "?"),
                      resolvedDataType),
                  this));
        }

        // Validate that source field(s) (sourceFieldNames / sourceFieldName or fallback to name)
        // of this business key exist in the DataVaultSource referenced by its recordSourceName.
        if (DvIntegrationSupport.relaxesSourceValidation(this)) {
          continue;
        }
        List<String> sourceParts = bk.resolveSourceParts();
        if (sourceParts.isEmpty() && !Utils.isEmpty(bk.getName())) {
          sourceParts = List.of(bk.getName());
        }
        if (!sourceParts.isEmpty()
            && !Utils.isEmpty(bk.getRecordSourceName())
            && metadataProvider != null) {
          try {
            String resolvedRecordSource =
                variables != null
                    ? variables.resolve(bk.getRecordSourceName())
                    : bk.getRecordSourceName();
            DataVaultSource recordSource =
                DvSourceCatalogService.resolveSource(
                    resolvedRecordSource, model, variables, metadataProvider);
            if (recordSource != null) {
              List<SourceField> fields = recordSource.getFields(metadataProvider);
              for (String sourceField : sourceParts) {
                boolean exists = false;
                String resolvedSourceField =
                    variables != null ? variables.resolve(sourceField) : sourceField;
                for (SourceField sf : fields) {
                  String sfName =
                      variables != null ? variables.resolve(sf.getName()) : sf.getName();
                  if (resolvedSourceField != null && resolvedSourceField.equals(sfName)) {
                    exists = true;
                    break;
                  }
                }
                if (!exists) {
                  remarks.add(
                      new CheckResult(
                          ICheckResult.TYPE_RESULT_ERROR,
                          "Source field '"
                              + sourceField
                              + "' of business key '"
                              + bk.getName()
                              + "' does not exist in record source '"
                              + bk.getRecordSourceName()
                              + "'",
                          this));
                }
              }
            }
            if (!Utils.isEmpty(bk.getRecordSourceName())
                && recordSources != null
                && !recordSources.isEmpty()) {
              String resolvedBkSource =
                  variables != null
                      ? variables.resolve(bk.getRecordSourceName())
                      : bk.getRecordSourceName();
              boolean listed =
                  recordSources.stream()
                      .filter(s -> !Utils.isEmpty(s))
                      .anyMatch(
                          s -> {
                            String resolvedHubSource = variables != null ? variables.resolve(s) : s;
                            return resolvedBkSource.equals(resolvedHubSource);
                          });
              if (!listed) {
                remarks.add(
                    new CheckResult(
                        ICheckResult.TYPE_RESULT_ERROR,
                        BaseMessages.getString(
                            PKG,
                            "DvHub.CheckResult.BusinessKeyRecordSourceNotListed",
                            bk.getName(),
                            bk.getRecordSourceName()),
                        this));
              }
            }
          } catch (HopException e) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    "Error loading record source '"
                        + bk.getRecordSourceName()
                        + "' for business key '"
                        + bk.getName()
                        + "': "
                        + e.getMessage(),
                    this));
          }
        }
      }
      for (String mismatch :
          DvBusinessKeyPartSupport.findCompositePartCountMismatches(this, variables)) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, mismatch, this));
      }
      DataVaultConfiguration hubConfig = model != null ? model.getConfigurationOrDefault() : null;
      if (hubConfig != null
          && hubConfig.isHashUsesComposedBusinessKey()
          && !Utils.isEmpty(hubConfig.getHashContentSuffix())
          && DvBusinessKeyPartSupport.hubHasCompositeBusinessKey(this)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(PKG, "DvHub.CheckResult.HashComposedWithSuffixWarning"),
                this));
      }
    }

    if (Utils.isEmpty(hashKeyFieldName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(PKG, "DvHub.CheckResult.NoHashKeyFieldName"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvHub.CheckResult.HasHashKeyFieldName", hashKeyFieldName),
              this));
    }

    if (Utils.isEmpty(recordSourceFieldName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvHub.CheckResult.NoRecordSourceFieldName"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvHub.CheckResult.HasRecordSourceFieldName", recordSourceFieldName),
              this));
    }

    if (!DvIntegrationSupport.relaxesSourceValidation(this)
        && metadataProvider != null
        && recordSources != null
        && options != null) {
      DvModelCheckOptions effectiveOptions =
          options != null ? options : DvModelCheckOptions.fastOnly();
      DvFieldMappingValidationSupport.validateHubRecordSourceFields(
          this, model, effectiveOptions, metadataProvider, variables, this, remarks);
      DataVaultConfiguration config = model.getConfigurationOrDefault();
      DatabaseMeta targetDatabaseMeta = null;
      try {
        if (config != null && !Utils.isEmpty(config.getTargetDatabase())) {
          targetDatabaseMeta =
              metadataProvider.getSerializer(DatabaseMeta.class).load(config.getTargetDatabase());
        }
      } catch (HopException e) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                "Error loading target database for type validation: " + e.getMessage(),
                this));
      }
      for (String recordSourceRef : recordSources) {
        if (Utils.isEmpty(recordSourceRef)) {
          continue;
        }
        try {
          String resolvedRef =
              variables != null ? variables.resolve(recordSourceRef) : recordSourceRef;
          DataVaultSource recordSource =
              DvSourceCatalogService.resolveSource(resolvedRef, model, variables, metadataProvider);
          if (recordSource != null) {
            DvFieldMappingValidationSupport.validateHubBusinessKeys(
                this,
                recordSource,
                config,
                targetDatabaseMeta,
                options,
                metadataProvider,
                variables,
                this,
                remarks);
          }
        } catch (HopException e) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  "Error loading record source '"
                      + recordSourceRef
                      + "' for type validation: "
                      + e.getMessage(),
                  this));
        }
      }
    }
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

      List<DataVaultSource> sources =
          loadRecordSources(model, variables, metadataProvider, recordSourceGroup);
      if (sources.isEmpty()) {
        if (!Utils.isEmpty(recordSourceGroup)) {
          return result;
        }
        throw new HopException("Please specify one or more record sources in Hub " + getName());
      }

      for (DataVaultSource src : sources) {
        HubUpdateContext ctx =
            HubUpdateContext.create(metadataProvider, variables, model, this, src);
        if (ctx == null) continue;

        PipelineMeta pipelineMeta = new PipelineMeta();
        String baseName = ctx.pipelineName;
        if (sources.size() > 1 && src.getName() != null) {
          pipelineMeta.setName(baseName + "_" + src.getName());
        } else {
          pipelineMeta.setName(baseName);
        }

        GeneratedPipelineMetadataSupport.stampDvHubPipeline(
            pipelineMeta, model, this, ctx.targetTableName, src.getName());

        // Source TableInput (from record source) — SQL ORDER BY business keys (auto COLLATE on
        // SQL Server when source/target collations or Unicode/ANSI types differ).
        TransformMeta sourceInputTransform = addSourceTableInput(ctx, src, pipelineMeta);
        GeneratedPipelineMetadataSupport.stampSourceRead(sourceInputTransform, ctx.targetDbName);

        // Composite hub BKs: compose source parts into the single vault BK column before merge so
        // CDC keys match the target table (parts remain on the stream for default hash-over-parts).
        TransformMeta sourceForMerge =
            addComposeCompositeBusinessKeys(ctx, pipelineMeta, sourceInputTransform);

        // Target TableInput (from target DB, for diff) — same ORDER BY keys + bridge COLLATE.
        TransformMeta targetInputTransform = addTargetTableInput(ctx, pipelineMeta);
        if (targetInputTransform != null) {
          GeneratedPipelineMetadataSupport.stampTargetRead(
              targetInputTransform, "hub", getName(), ctx.targetTableName, ctx.targetDbName);
        }

        // MergeRowsPlus (diff) on business keys. No SortRows: both legs are DB-sorted.
        TransformMeta mergeTransform =
            addMergeRows(ctx, pipelineMeta, sourceForMerge, targetInputTransform);
        if (mergeTransform != null) {
          GeneratedPipelineMetadataSupport.stampTransform(
              mergeTransform,
              new GeneratedPipelineMetadataSupport.TransformContext(
                  GeneratedPipelineMetadataConstants.ROLE_CDC_MERGE,
                  "hub",
                  getName(),
                  null,
                  ctx.targetTableName,
                  null,
                  null));
        }

        // Filter Rows: keep only "new" rows coming out of the diff (for insert into target hub)
        TransformMeta filterTransform = addFilterNewRows(pipelineMeta, mergeTransform);
        if (filterTransform != null) {
          GeneratedPipelineMetadataSupport.stampTransform(
              filterTransform,
              new GeneratedPipelineMetadataSupport.TransformContext(
                  GeneratedPipelineMetadataConstants.ROLE_FILTER,
                  "hub",
                  getName(),
                  null,
                  ctx.targetTableName,
                  null,
                  null));
        }

        // DvHashKey after the filter (only for new rows).
        TransformMeta checkSumTransform =
            addDvHashKey(ctx, pipelineMeta, filterTransform, LOCATION_START_LINE_3);
        if (checkSumTransform != null) {
          GeneratedPipelineMetadataSupport.stampTransform(
              checkSumTransform,
              new GeneratedPipelineMetadataSupport.TransformContext(
                  GeneratedPipelineMetadataConstants.ROLE_HASH_KEY,
                  "hub",
                  getName(),
                  null,
                  ctx.targetTableName,
                  null,
                  null));
        } else {
          checkSumTransform = filterTransform;
        }

        // Add Constant transform for the static load date (provided to the method)
        TransformMeta constantTransform =
            addConstantForLoadDate(ctx, pipelineMeta, loadDate, checkSumTransform);
        constantTransform = addConstantForLoadCycleId(ctx, pipelineMeta, constantTransform);

        // Write new rows to the target (all target fields except "flag")
        IRowMeta targetLayout = getTargetTableLayout(metadataProvider, variables, model);
        DvTargetLoadSupport.TargetLoadResult writeResult =
            DvTargetLoadSupport.addTargetLoad(
                buildTargetLoadContext(ctx, pipelineMeta.getName()),
                pipelineMeta,
                targetLayout,
                constantTransform,
                Set.of("flag"));
        if (writeResult != null && writeResult.transformMeta != null) {
          GeneratedPipelineMetadataSupport.stampWriteTarget(
              writeResult.transformMeta, "hub", getName(), ctx.targetTableName, ctx.targetDbName);
        }

        result.add(pipelineMeta);
      }
      DvGeneratedPipelineSupport.applyLayout(result);
      return result;
    } catch (Exception e) {
      throw new HopException("Error generating update pipeline(s) for Hub target " + getName(), e);
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
    if (pipelines == null || pipelines.size() <= 1) {
      return List.of();
    }
    DataVaultConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DataVaultConfiguration();
    String prefix =
        config != null && !Utils.isEmpty(config.getHubPipelineNamePrefix())
            ? config.getHubPipelineNamePrefix()
            : DataVaultConfiguration.DEFAULT_HUB_PIPELINE_NAME_PREFIX;
    String workflowName = DvMultiSourceUpdateWorkflowSupport.defaultWorkflowName(this, prefix);
    return DvMultiSourceUpdateWorkflowSupport.buildSerialWorkflowsIfMultiSource(
        workflowName, pipelines);
  }

  private List<DataVaultSource> loadRecordSources(
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String recordSourceGroup)
      throws HopException {
    List<DataVaultSource> sources = new ArrayList<>();

    for (String recordSource : recordSources) {
      DataVaultSource source =
          DvSourceCatalogService.resolveSource(
              variables.resolve(recordSource), model, variables, metadataProvider);
      if (source != null && source.matchesRecordSourceGroup(recordSourceGroup, variables)) {
        sources.add(source);
      }
    }
    return sources;
  }

  // --- Small methods, each adding one transform and referencing the shared context ---

  private TransformMeta addSourceTableInput(
      HubUpdateContext ctx, DataVaultSource dataVaultSource, PipelineMeta pipelineMeta)
      throws HopException {
    // Let's ask our source to provide the needed Hop transforms.
    //
    DvSourcePipelineBuilder builder =
        DvSourcePipelineBuilderFactory.forHub(
            ctx.variables,
            ctx.metadataProvider,
            ctx.model,
            pipelineMeta,
            dataVaultSource,
            ctx.dvSource,
            this,
            new Point(LOCATION_START_LINE_2.x, LOCATION_START_LINE_2.y));
    builder.build();
    builder.applyCatalogDataTypeMappings();

    return builder.getResultTransform();
  }

  /**
   * For each composite business key, add a ConcatFields step that builds the stored vault BK column
   * from ordered source parts (delimiter/trim/null from configuration via ConcatFields nullif +
   * separator). Part fields stay on the stream for hash-over-parts.
   */
  private TransformMeta addComposeCompositeBusinessKeys(
      HubUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor) {
    if (predecessor == null || !DvBusinessKeyPartSupport.hubHasCompositeBusinessKey(this)) {
      return predecessor;
    }

    String sourceName =
        ctx.dataVaultSource != null ? ctx.variables.resolve(ctx.dataVaultSource.getName()) : null;
    String delimiter =
        ctx.config != null && !Utils.isEmpty(ctx.config.getBusinessKeyDelimiter())
            ? ctx.variables.resolve(ctx.config.getBusinessKeyDelimiter())
            : "||";
    String nullPlaceholder =
        ctx.config != null
            ? ctx.variables.resolve(Const.NVL(ctx.config.getNullPlaceholder(), ""))
            : "";

    TransformMeta current = predecessor;
    int composeIndex = 0;
    for (DvBusinessKeyPartSupport.VaultBusinessKey vaultKey :
        DvBusinessKeyPartSupport.resolveVaultBusinessKeys(this)) {
      if (!vaultKey.composite()) {
        continue;
      }
      List<String> parts =
          DvBusinessKeyPartSupport.resolveSourcePartsForHubSource(
              this, vaultKey.vaultFieldName(), sourceName, ctx.variables);
      if (parts.isEmpty()) {
        continue;
      }

      ConcatFieldsMeta concatMeta = new ConcatFieldsMeta();
      concatMeta.setSeparator(delimiter);
      concatMeta.setSkipValueEmpty(false);
      List<ConcatField> fields = new ArrayList<>();
      for (String part : parts) {
        ConcatField field = new ConcatField();
        field.setName(part);
        if (!Utils.isEmpty(nullPlaceholder)) {
          field.setNullString(nullPlaceholder);
        }
        if (ctx.config == null || ctx.config.isTrimBusinessKeys()) {
          field.setTrimType("both");
        }
        fields.add(field);
      }
      concatMeta.setOutputFields(fields);

      ExtraFields extra = new ExtraFields();
      extra.setTargetFieldName(vaultKey.vaultFieldName());
      BusinessKey def = vaultKey.definition();
      int targetLength = def != null ? Const.toInt(ctx.variables.resolve(def.getLength()), -1) : -1;
      extra.setTargetFieldLength(targetLength);
      extra.setRemoveSelectedFields(false);
      concatMeta.setExtraFields(extra);

      String transformName = "compose_" + vaultKey.vaultFieldName();
      if (composeIndex > 0) {
        transformName = transformName + "_" + composeIndex;
      }
      TransformMeta tm = new TransformMeta("ConcatFields", transformName, concatMeta);
      tm.setLocation(
          LOCATION_START_LINE_2.x + SPACING_WIDTH * (1 + composeIndex), LOCATION_START_LINE_2.y);
      pipelineMeta.addTransform(tm);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(current, tm));
      current = tm;
      composeIndex++;
    }
    return current;
  }

  private TransformMeta addTargetTableInput(HubUpdateContext ctx, PipelineMeta pipelineMeta)
      throws HopException {
    if (ctx.targetDatabaseMeta == null) {
      return null;
    }

    TableInputMeta targetTableInputMeta = new TableInputMeta();
    targetTableInputMeta.setConnection(ctx.targetDbName);

    // Generate the SQL to read from the Hub target table.
    //
    StringBuilder sql = new StringBuilder("SELECT DISTINCT ");

    // First add all the business key(s)
    List<String> bkQuotedBkFields = new ArrayList<>();
    for (BusinessKey key : getDistinctBusinessKeys()) {
      String quotedBkField =
          ctx.targetDatabaseMeta.quoteField(ctx.variables.resolve(key.getName()));
      bkQuotedBkFields.add(quotedBkField);
    }
    sql.append(StringUtils.join(bkQuotedBkFields, ","));

    // The source indicator field is called recordSourceFieldName
    // If it's nog specified we look at the global configuration.
    //
    String rsFieldName = calculateRecordSourceFieldName(ctx);
    sql.append(", ")
        .append(DvSqlSupport.typedNullString(ctx.targetDatabaseMeta, ctx.variables, ctx.config))
        .append(" AS ")
        .append(ctx.targetDatabaseMeta.quoteField(rsFieldName));

    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.targetTableName));
    // SQL Server: ORDER BY col COLLATE x with SELECT DISTINCT is invalid unless the COLLATE
    // expression appears in the select list — order in an outer query instead.
    StringBuilder orderBy = new StringBuilder();
    DvSqlOrderBySupport.appendOrderBy(
        orderBy,
        getDistinctBusinessKeys(),
        bkQuotedBkFields,
        ctx.targetDatabaseMeta,
        ctx.config,
        ctx.variables,
        loadHubOrderByCollationSession(ctx));
    String finalSql;
    if (DvSqlOrderBySupport.isCollationOrderBySupported(ctx.targetDatabaseMeta)
        && orderBy.indexOf("COLLATE") >= 0) {
      finalSql = "SELECT * FROM (" + sql + ") collate_sort_tgt" + orderBy;
    } else {
      sql.append(orderBy);
      finalSql = sql.toString();
    }

    DvSqlSupport.assignDisplaySql(targetTableInputMeta, finalSql);

    TransformMeta tm =
        new TransformMeta("TableInput", ctx.targetTransformName, targetTableInputMeta);
    tm.setLocation(LOCATION_START_LINE_3);
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private @NonNull String calculateRecordSourceFieldName(HubUpdateContext ctx) throws HopException {
    String rsFieldName = ctx.variables.resolve(recordSourceFieldName);
    if (StringUtils.isEmpty(rsFieldName)) {
      rsFieldName = ctx.variables.resolve(ctx.config.getRecordSourceField());
    }
    if (StringUtils.isEmpty(rsFieldName)) {
      throw new HopException(
          "No source field is specified for the target table in either the Hub or the data vault configuration.");
    }
    return rsFieldName;
  }

  private DvSqlOrderByCollationSupport.Session loadHubOrderByCollationSession(
      HubUpdateContext ctx) {
    try {
      DatabaseMeta sourceDatabaseMeta = null;
      String sourceSchema = null;
      String sourceTable = null;
      if (ctx.dvSource
          instanceof org.apache.hop.datavault.metadata.database.DvDatabaseSource dbSource) {
        sourceSchema = dbSource.getSchemaName();
        sourceTable = dbSource.getTableName();
        if (!Utils.isEmpty(dbSource.getDatabaseName())) {
          sourceDatabaseMeta =
              ctx.metadataProvider
                  .getSerializer(DatabaseMeta.class)
                  .load(ctx.variables.resolve(dbSource.getDatabaseName()));
        }
      }
      return DvSqlOrderByCollationSupport.loadSession(
          sourceDatabaseMeta,
          sourceSchema,
          sourceTable,
          ctx.targetDatabaseMeta,
          null,
          ctx.targetTableName,
          ctx.variables);
    } catch (Exception e) {
      return DvSqlOrderByCollationSupport.Session.empty();
    }
  }

  private TransformMeta addMergeRows(
      HubUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta compareTransform,
      TransformMeta referenceTransform)
      throws HopException {
    if (referenceTransform == null || compareTransform == null) {
      return null;
    }

    MergeRowsPlusMeta mergeRowsMeta = new MergeRowsPlusMeta();
    mergeRowsMeta.setReferenceTransform(referenceTransform.getName());
    mergeRowsMeta.setCompareTransform(compareTransform.getName());
    mergeRowsMeta.setFlagField("flag");

    List<String> keyFields = new ArrayList<>();
    for (BusinessKey bk : getDistinctBusinessKeys()) {
      keyFields.add(bk.getName());
    }
    mergeRowsMeta.setKeyFields(keyFields);

    TransformMeta tm = new TransformMeta("MergeRowsPlus", "merge_diff", mergeRowsMeta);
    tm.setLocation(LOCATION_START_LINE_3.x + SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);

    pipelineMeta.addPipelineHop(new PipelineHopMeta(referenceTransform, tm));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(compareTransform, tm));

    return tm;
  }

  private TransformMeta addFilterNewRows(PipelineMeta pipelineMeta, TransformMeta mergeTransform)
      throws HopException {
    if (mergeTransform == null) {
      return null;
    }

    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    try {
      filterRowsMeta
          .getCompare()
          .setCondition(
              new Condition(
                  "flag",
                  Condition.Function.EQUAL,
                  null,
                  new ValueMetaAndData(new ValueMetaString("static"), "new")));
    } catch (HopValueException e) {
      throw new HopException("Error creating 'new rows' filter condition", e);
    }

    TransformMeta tm = new TransformMeta("FilterRows", "filter_new", filterRowsMeta);
    tm.setLocation(LOCATION_START_LINE_3.x + 3 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(mergeTransform, tm));
    return tm;
  }

  private TransformMeta addDvHashKey(
      HubUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor, Point hashLine) {
    String resultFieldName = ctx.hub.getHashKeyFieldName();
    if (Utils.isEmpty(resultFieldName)) {
      String bkName = "hashkey";
      if (!Utils.isEmpty(ctx.hub.getBusinessKeys())) {
        bkName = ctx.hub.getBusinessKeys().get(0).getName();
      }
      resultFieldName = bkName + "_hk";
    }

    String sourceName =
        ctx.dataVaultSource != null ? ctx.variables.resolve(ctx.dataVaultSource.getName()) : null;
    List<String> hashInputFields =
        DvBusinessKeyPartSupport.resolveHashInputStreamFieldNames(
            this, sourceName, ctx.config, ctx.variables);
    if (hashInputFields.isEmpty()) {
      for (BusinessKey bk : getDistinctBusinessKeys()) {
        hashInputFields.add(bk.getName());
      }
    }
    DvHashKeyMeta hashKeyMeta =
        DvHashKeyMetaFactory.create(ctx.config, hashInputFields, resultFieldName);

    TransformMeta tm = new TransformMeta("DvHashKey", "calc_" + resultFieldName, hashKeyMeta);
    int x =
        hashLine == LOCATION_START_LINE_3
            ? LOCATION_START_LINE_3.x + 4 * SPACING_WIDTH
            : hashLine.x + SPACING_WIDTH;
    tm.setLocation(new Point(x, hashLine.y));
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addConstantForLoadDate(
      HubUpdateContext ctx, PipelineMeta pipelineMeta, Date loadDate, TransformMeta predecessor)
      throws HopException {
    String loadDateField = "LOAD_DATE";
    if (ctx.config != null && !Utils.isEmpty(ctx.config.getLoadDateField())) {
      loadDateField = ctx.config.getLoadDateField();
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
    tm.setLocation(LOCATION_START_LINE_3.x + 5 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addConstantForLoadCycleId(
      HubUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    if (ctx.config == null || !ctx.config.isStoreLoadCycleId()) {
      return predecessor;
    }
    return DvLoadCycleSupport.addConstantForLoadCycleId(
        pipelineMeta,
        predecessor,
        true,
        ctx.config.getLoadCycleIdField(),
        ctx.variables,
        null,
        new Point(LOCATION_START_LINE_3.x + 6 * SPACING_WIDTH, LOCATION_START_LINE_3.y));
  }

  private static DvTargetLoadSupport.TargetLoadContext buildTargetLoadContext(
      HubUpdateContext ctx, String pipelineName) {
    String tableName = ctx.targetTableName;
    if (Utils.isEmpty(tableName)) {
      tableName = ctx.hub.getName();
    }
    return new DvTargetLoadSupport.TargetLoadContext(
        ctx.config,
        ctx.variables,
        ctx.targetDatabaseMeta,
        ctx.targetDbName,
        tableName,
        pipelineName,
        ctx.model != null ? ctx.model.getName() : null,
        LOCATION_START_LINE_3.x + 6 * SPACING_WIDTH,
        LOCATION_START_LINE_3.y);
  }

  /**
   * Find the business key with the given name.
   *
   * @param keyName The name of the key to look for.
   * @return The key or null if it couldn't be found.
   */
  public BusinessKey findBusinessKey(String keyName) {
    for (BusinessKey key : businessKeys) {
      if (key.getName().equalsIgnoreCase(keyName)) {
        return key;
      }
    }
    return null;
  }

  public List<String> getBusinessKeyFieldNames() {
    List<String> names = new ArrayList<>();
    for (BusinessKey key : getDistinctBusinessKeys()) {
      names.add(key.getName());
    }
    return names;
  }

  /**
   * Business keys that apply to the given record source. When {@link
   * BusinessKey#getRecordSourceName()} is empty the key applies to every source.
   */
  public List<BusinessKey> getBusinessKeysForSource(String sourceName, IVariables variables) {
    List<BusinessKey> result = new ArrayList<>();
    if (businessKeys == null) {
      return result;
    }
    String resolvedSourceName = resolveBusinessKeySourceName(sourceName, variables);
    for (BusinessKey bk : businessKeys) {
      if (bk != null && businessKeyAppliesToSource(bk, resolvedSourceName, variables)) {
        result.add(bk);
      }
    }
    return result;
  }

  /** One entry per hub business key name, preserving first-seen order for composite keys. */
  public List<BusinessKey> getDistinctBusinessKeys() {
    List<BusinessKey> result = new ArrayList<>();
    if (businessKeys == null) {
      return result;
    }
    Set<String> seen = new LinkedHashSet<>();
    for (BusinessKey bk : businessKeys) {
      if (bk != null && !Utils.isEmpty(bk.getName()) && seen.add(bk.getName())) {
        result.add(bk);
      }
    }
    return result;
  }

  private static boolean businessKeyAppliesToSource(
      BusinessKey bk, String sourceName, IVariables variables) {
    String bkSource = resolveBusinessKeySourceName(bk.getRecordSourceName(), variables);
    if (Utils.isEmpty(bkSource)) {
      return true;
    }
    return bkSource.equals(sourceName);
  }

  private static String resolveBusinessKeySourceName(String name, IVariables variables) {
    return variables != null && name != null ? variables.resolve(name) : name;
  }

  /**
   * Best-effort lookup of a catalog source field for a business key (first source part) so layout
   * generation can fall back to the source hop type when {@link BusinessKey#getDataType()} is
   * blank.
   */
  private SourceField findBusinessKeySourceField(
      BusinessKey bk,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model) {
    if (bk == null || metadataProvider == null) {
      return null;
    }
    List<String> sourceParts = bk.resolveSourceParts();
    if (sourceParts.isEmpty() && !Utils.isEmpty(bk.getName())) {
      sourceParts = List.of(bk.getName());
    }
    if (sourceParts.isEmpty()) {
      return null;
    }
    String sourceFieldName =
        variables != null ? variables.resolve(sourceParts.get(0)) : sourceParts.get(0);
    if (Utils.isEmpty(sourceFieldName)) {
      return null;
    }

    List<String> candidateSources = new ArrayList<>();
    if (!Utils.isEmpty(bk.getRecordSourceName())) {
      candidateSources.add(bk.getRecordSourceName());
    }
    if (recordSources != null) {
      for (String rs : recordSources) {
        if (!Utils.isEmpty(rs) && !candidateSources.contains(rs)) {
          candidateSources.add(rs);
        }
      }
    }

    for (String sourceName : candidateSources) {
      try {
        String resolved = variables != null ? variables.resolve(sourceName) : sourceName;
        DataVaultSource recordSource =
            DvSourceCatalogService.resolveSource(resolved, model, variables, metadataProvider);
        if (recordSource == null) {
          continue;
        }
        for (SourceField sf : recordSource.getFields(metadataProvider)) {
          if (sf == null || Utils.isEmpty(sf.getName())) {
            continue;
          }
          String sfName = variables != null ? variables.resolve(sf.getName()) : sf.getName();
          if (sourceFieldName.equals(sfName)) {
            return sf;
          }
        }
      } catch (Exception ignored) {
        // Best-effort only; layout still fails later if type remains unknown.
      }
    }
    return null;
  }

  /** Context object holding all loaded / derived objects for the hub update pipeline generation. */
  private static class HubUpdateContext {
    final DvHub hub;
    final DataVaultModel model;
    final IHopMetadataProvider metadataProvider;
    final IVariables variables;

    final DataVaultConfiguration config;
    final HashAlgorithm hashAlgorithm;

    final DataVaultSource dataVaultSource;
    final IDvSource dvSource;
    final DatabaseMeta targetDatabaseMeta;
    final String targetDbName;
    final String targetTableName;
    final String pipelineName;

    final String sourceTransformName;
    final String targetTransformName;
    final String hashKeyFieldName;

    HubUpdateContext(
        DvHub hub,
        DataVaultModel model,
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultConfiguration config,
        DataVaultSource dataVaultSource,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String targetTableName,
        String pipelineName,
        String sourceTransformName,
        String targetTransformName,
        String hashKeyFieldName)
        throws HopException {
      this.hub = hub;
      this.model = model;
      this.metadataProvider = metadataProvider;
      this.variables = variables;
      this.config = config;
      this.hashAlgorithm = (config != null) ? config.resolveHashAlgorithm() : HashAlgorithm.MD5;
      this.dataVaultSource = dataVaultSource;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.targetTableName = targetTableName;
      this.pipelineName = pipelineName;
      this.sourceTransformName = sourceTransformName;
      this.targetTransformName = targetTransformName;
      this.hashKeyFieldName = hashKeyFieldName != null ? hashKeyFieldName : "hashkey_hk";

      this.dvSource = dataVaultSource.getDvSource(metadataProvider);
    }

    /**
     * Factory: load configuration, target/source database metas, source fields for PKs, compute
     * names, checksum type, etc. Validations (missing critical metadata) throw HopException.
     * Returns null only for top-level null inputs (graceful no-op as before).
     */
    static HubUpdateContext create(
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultModel model,
        DvHub hub,
        DataVaultSource specificSource)
        throws HopException {
      if (metadataProvider == null || model == null || hub == null) {
        return null;
      }

      if (specificSource == null) {
        throw new HopException("Please specify a specific data vault source");
      }

      DataVaultConfiguration config = model.getConfigurationOrDefault();

      // Target DB
      DatabaseMeta targetDatabaseMeta = null;
      String targetDbName = (config != null) ? config.getTargetDatabase() : null;
      if (!Utils.isEmpty(targetDbName)) {
        targetDatabaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(targetDbName);
        if (targetDatabaseMeta == null) {
          throw new HopException(
              "Target database connection not found in metadata: " + targetDbName);
        }
      }

      String targetTableName =
          !Utils.isEmpty(hub.getTableName()) ? hub.getTableName() : hub.getName();
      String pipelineName =
          config.buildHubPipelineName(variables, targetTableName, specificSource.getName());

      String sourceTransformName;
      sourceTransformName = specificSource.getName();
      if (Utils.isEmpty(sourceTransformName)) {
        sourceTransformName = hub.getName();
      }

      String targetTransformName = "target_" + targetTableName;

      String hashKeyFieldName = hub.getHashKeyFieldName();
      if (Utils.isEmpty(hashKeyFieldName)) {
        if (!Utils.isEmpty(hub.getBusinessKeys())) {
          hashKeyFieldName = hub.getBusinessKeys().get(0).getName() + "_hk";
        } else {
          hashKeyFieldName = "hashkey_hk";
        }
      }

      // Basic validation example inside context (more can be added; serious ones throw above)
      // e.g. we could collect warnings but for now the critical loads already validated via throws.

      return new HubUpdateContext(
          hub,
          model,
          metadataProvider,
          variables,
          config,
          specificSource,
          targetDatabaseMeta,
          targetDbName,
          targetTableName,
          pipelineName,
          sourceTransformName,
          targetTransformName,
          hashKeyFieldName);
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

      // 1. First column: the hub's hash key field name (or fallback from first business key + _HK)
      String hashKeyName = hashKeyFieldName;
      if (Utils.isEmpty(hashKeyName)) {
        String bkName = "hashkey";
        if (!Utils.isEmpty(businessKeys)) {
          bkName = businessKeys.get(0).getName();
        }
        hashKeyName = bkName + "_hk";
      }

      IValueMeta hashMeta;
      HashKeyDataType hdt = config.resolveHashKeyDataType();
      HashAlgorithm algo = config.resolveHashAlgorithm();
      if (algo == null) {
        algo = HashAlgorithm.MD5;
      }
      int digestBytes = algo.getDigestLength();

      if (hdt == HashKeyDataType.BINARY) {
        hashMeta = new ValueMetaBinary(hashKeyName);
        hashMeta.setLength(digestBytes);
      } else if (hdt == HashKeyDataType.HEX) {
        hashMeta = new ValueMetaString(hashKeyName);
        hashMeta.setLength(digestBytes * 2);
      } else {
        // STRING: the decimal-dash format produced by DvHashKey
        // max length = N*3 + (N-1)  e.g. 63 for MD5
        int stringMax = digestBytes * 3 + (digestBytes > 0 ? digestBytes - 1 : 0);
        hashMeta = new ValueMetaString(hashKeyName);
        hashMeta.setLength(stringMax);
      }
      rowMeta.addValueMeta(hashMeta);

      // 2. Then business keys (type from the metadata). Physical column name is always the vault
      // BK name (sourceFieldName / sourceFieldNames are source mappings only). Composite BKs still
      // contribute a single vault column. Prefer the modeled dataType; when blank, fall back to a
      // matching source field hop type (same pattern as satellite attributes).
      //
      for (BusinessKey bk : getDistinctBusinessKeys()) {
        String vaultFieldName = variables.resolve(bk.getName());

        SourceField matchingSource =
            findBusinessKeySourceField(bk, variables, metadataProvider, model);
        int type =
            DvDataTypeSupport.resolveHopTypeId(variables.resolve(bk.getDataType()), matchingSource);
        int length = Const.toInt(variables.resolve(bk.getLength()), -1);
        if (length <= 0 && matchingSource != null) {
          length = Const.toInt(matchingSource.getLength(), -1);
        }
        int precision = Const.toInt(variables.resolve(bk.getPrecision()), -1);
        if (precision < 0 && matchingSource != null) {
          precision = Const.toInt(matchingSource.getPrecision(), -1);
        }

        IValueMeta bkMeta =
            ValueMetaFactory.createValueMeta(vaultFieldName, type, length, precision);

        if (bkMeta == null || bkMeta.getType() == IValueMeta.TYPE_NONE) {
          throw new HopException(
              "Please specify a valid data type for business key "
                  + bk.getName()
                  + " in Hub "
                  + getName());
        }
        rowMeta.addValueMeta(bkMeta);
      }

      // Record source column (per-hub name if specified on DvHub, else from config; supports
      // variables)
      // Length from config's recordSourceFieldLength (resolved + Const.toInt default 100)
      String rsFieldName = getRecordSourceFieldName();
      if (Utils.isEmpty(rsFieldName)) {
        rsFieldName = config.getRecordSourceField();
      }
      rsFieldName = variables.resolve(rsFieldName);
      if (Utils.isEmpty(rsFieldName)) {
        rsFieldName = "RECORD_SOURCE";
      }
      String lengthString =
          !Utils.isEmpty(config.getRecordSourceFieldLength())
              ? config.getRecordSourceFieldLength()
              : "100";
      lengthString = variables.resolve(lengthString);
      int rsLength = Const.toInt(lengthString, 100);
      IValueMeta rsMeta = new ValueMetaString(rsFieldName);
      rsMeta.setLength(rsLength);
      rowMeta.addValueMeta(rsMeta);

      // 3. Finally the load date field from config, Hop Timestamp type
      String loadDateField = config.getLoadDateField();
      if (Utils.isEmpty(loadDateField)) {
        loadDateField = "LOAD_DATE";
      }
      IValueMeta loadMeta = new ValueMetaTimestamp(loadDateField);
      rowMeta.addValueMeta(loadMeta);

      DvLoadCycleSupport.appendToLayout(
          rowMeta, config.isStoreLoadCycleId(), config.getLoadCycleIdField(), variables);
      DvOrphanHandlingSupport.appendInferredFlagToHubLayout(rowMeta, config, variables);

      return rowMeta;

    } catch (Exception e) {
      // Since interface doesn't declare throws, wrap
      throw new HopException("Error building target table layout for hub", e);
    }
  }

  @Override
  public int ensureSpecialRecords(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      ILoggingObject loggingObject)
      throws HopException {
    if (DvIntegrationSupport.shouldSkipSentinelRows(this)) {
      return 0;
    }
    return DvSpecialRecordSupport.ensureHubSpecialRecords(
        this, metadataProvider, variables, model, loadDate, loggingObject);
  }
}
