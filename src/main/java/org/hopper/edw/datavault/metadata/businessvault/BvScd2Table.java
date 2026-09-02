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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvBulkLoadPluginSupport;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.DvTargetLoadMode;

/** Business Vault SCD2 table derived from one or more DV satellites. */
@Getter
@Setter
public class BvScd2Table extends BvTableBase {

  private static final Class<?> PKG = BvScd2Table.class;

  @HopMetadataProperty(storeWithCode = true)
  private BvScd2BuildMode buildMode = BvScd2BuildMode.FULL_REBUILD;

  @HopMetadataProperty(storeWithCode = true)
  private BvScd2HashPartitionCount hashKeyPartitionCount = BvScd2HashPartitionCount.NONE;

  @HopMetadataProperty private String functionalTimestampField;

  @HopMetadataProperty private String incrementalWatermarkField;

  @HopMetadataProperty private String validFromField;

  @HopMetadataProperty private String validToField;

  @HopMetadataProperty private boolean includeHashKey = true;

  /**
   * When true, parent-hub business key columns are merged into the SCD2 stream as identity fields
   * for SQL calculations. They do not drive new versions. Persist them with {@link
   * #isLoadHubBusinessKeys()}.
   */
  @HopMetadataProperty private boolean includeHubBusinessKeys;

  /**
   * When true, hub business keys are joined for calculations but not written to the BV table.
   * Stored as the uncommon case so older {@code .hbv} files without this tag deserialize as false
   * (keys are loaded when Include hub business keys is enabled).
   */
  @HopMetadataProperty private boolean hubBusinessKeysCalculationOnly;

  @HopMetadataProperty(key = "field_mapping", groupKey = "field_mappings")
  private List<BvScd2FieldMapping> fieldMappings = new ArrayList<>();

  @HopMetadataProperty(key = "satellite_config", groupKey = "satellite_configs")
  private List<BvScd2SatelliteConfig> satelliteConfigs = new ArrayList<>();

  @HopMetadataProperty(key = "calculation", groupKey = "calculations")
  private List<BvScd2Calculation> calculations = new ArrayList<>();

  @HopMetadataProperty(key = "calculation_test", groupKey = "calculation_tests")
  private List<BvScd2CalculationTestCase> calculationTests = new ArrayList<>();

  @HopMetadataProperty(key = "collapse_test", groupKey = "collapse_tests")
  private List<BvScd2CollapseTestCase> collapseTests = new ArrayList<>();

  public BvScd2Table() {
    super(BvTableType.SCD2);
  }

  public List<BvScd2FieldMapping> getFieldMappings() {
    if (fieldMappings == null) {
      fieldMappings = new ArrayList<>();
    }
    return fieldMappings;
  }

  public List<BvScd2SatelliteConfig> getSatelliteConfigs() {
    if (satelliteConfigs == null) {
      satelliteConfigs = new ArrayList<>();
    }
    return satelliteConfigs;
  }

  public List<BvScd2Calculation> getCalculations() {
    if (calculations == null) {
      calculations = new ArrayList<>();
    }
    return calculations;
  }

  public List<BvScd2CalculationTestCase> getCalculationTests() {
    if (calculationTests == null) {
      calculationTests = new ArrayList<>();
    }
    return calculationTests;
  }

  public List<BvScd2CollapseTestCase> getCollapseTests() {
    if (collapseTests == null) {
      collapseTests = new ArrayList<>();
    }
    return collapseTests;
  }

  public boolean hasCalculations() {
    return !getCalculations().isEmpty();
  }

  public BvScd2BuildMode getBuildModeOrDefault() {
    return buildMode != null ? buildMode : BvScd2BuildMode.FULL_REBUILD;
  }

  public boolean isIncrementalBuild() {
    return getBuildModeOrDefault() == BvScd2BuildMode.INCREMENTAL;
  }

  public BvScd2HashPartitionCount getHashKeyPartitionCountOrDefault() {
    return hashKeyPartitionCount != null ? hashKeyPartitionCount : BvScd2HashPartitionCount.NONE;
  }

  public boolean isHashKeyPartitioned() {
    return getHashKeyPartitionCountOrDefault().isPartitioned();
  }

  /** Inverse of {@link #hubBusinessKeysCalculationOnly}; missing XML means the keys are loaded. */
  public boolean isLoadHubBusinessKeys() {
    return !hubBusinessKeysCalculationOnly;
  }

  public void setLoadHubBusinessKeys(boolean loadHubBusinessKeys) {
    this.hubBusinessKeysCalculationOnly = !loadHubBusinessKeys;
  }

  public String resolveIncrementalWatermarkField(
      BusinessVaultConfiguration bvConfig, DataVaultConfiguration dvConfig, IVariables variables) {
    if (!Utils.isEmpty(incrementalWatermarkField)) {
      return variables.resolve(incrementalWatermarkField);
    }
    return BvScd2PipelineSupport.resolveFunctionalTimestampField(
        this, bvConfig, dvConfig, variables);
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel) {
    super.check(remarks, metadataProvider, variables, model, dataVaultModel);
    boolean hasSatellite =
        getDerivatives().stream()
            .anyMatch(ref -> ref != null && ref.getDvTableType() == DvTableType.SATELLITE);
    if (!hasSatellite) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvScd2Table.CheckResult.MissingSatelliteDerivative", getName()),
              this));
    }
    BusinessVaultConfiguration bvConfig =
        model != null ? model.getConfigurationOrDefault() : new BusinessVaultConfiguration();
    DataVaultConfiguration dvConfig =
        dataVaultModel != null ? dataVaultModel.getConfigurationOrDefault() : null;
    if (Utils.isEmpty(
        BvScd2PipelineSupport.resolveFunctionalTimestampField(
            this, bvConfig, dvConfig, variables))) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvScd2Table.CheckResult.MissingFunctionalTimestamp", getName()),
              this));
    }

    if (isIncrementalBuild()) {
      if (Utils.isEmpty(resolveIncrementalWatermarkField(bvConfig, dvConfig, variables))) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "BvScd2Table.CheckResult.MissingIncrementalWatermark", getName()),
                this));
      }
      if (Utils.isEmpty(bvConfig.getOpenEndSentinel())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "BvScd2Table.CheckResult.MissingOpenEndSentinel", getName()),
                this));
      }
      validateIncrementalMultiSatelliteHints(remarks, variables);
    }

    if (isHashKeyPartitioned()) {
      if (isIncrementalBuild()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "BvScd2Table.CheckResult.HashKeyPartitionIncremental", getName()),
                this));
      }
      if (bvConfig.resolveTargetLoadMode() == DvTargetLoadMode.STAGING_FILE
          && metadataProvider != null) {
        try {
          DatabaseMeta targetDatabase =
              BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);
          if (targetDatabase != null
              && !DvBulkLoadPluginSupport.isModeAvailable(
                  targetDatabase, DvTargetLoadMode.STAGING_FILE)) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    BaseMessages.getString(
                        PKG, "BvScd2Table.CheckResult.HashKeyPartitionStagingFile", getName()),
                    this));
          }
        } catch (HopException e) {
          remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), this));
        }
      }
    }

    if (dataVaultModel == null) {
      boolean hasDerivative =
          getDerivatives().stream()
              .anyMatch(ref -> ref != null && !Utils.isEmpty(ref.getDvTableName()));
      if (hasDerivative) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "BvScd2Table.CheckResult.MissingDataVaultModel", getName()),
                this));
      }
    } else {
      BvScd2FieldMappingValidationSupport.validate(
          remarks, this, bvConfig, dvConfig, dataVaultModel, variables, metadataProvider);
      BvScd2CalculationValidationSupport.validate(
          remarks, this, bvConfig, dataVaultModel, variables);
      BvScd2PipelineSupport.validateTargetDatabases(
          remarks, metadataProvider, model, dataVaultModel, this);
    }
  }

  private void validateIncrementalMultiSatelliteHints(
      List<ICheckResult> remarks, IVariables variables) {
    long satelliteCount =
        getDerivatives().stream()
            .filter(ref -> ref != null && ref.getDvTableType() == DvTableType.SATELLITE)
            .count();
    if (satelliteCount <= 1) {
      return;
    }
    long configuredIndicators =
        getSatelliteConfigs().stream()
            .filter(
                config ->
                    config != null
                        && !Utils.isEmpty(config.getSatelliteName())
                        && !Utils.isEmpty(
                            variables != null
                                ? variables.resolve(config.getSourceIndicatorValue())
                                : config.getSourceIndicatorValue()))
            .count();
    if (configuredIndicators < satelliteCount) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "BvScd2Table.CheckResult.IncrementalMultiSatelliteSourceIndicators",
                  getName()),
              this));
    }
  }

  @Override
  public List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    return BvScd2PipelineSupport.generateBuildPipelines(
        metadataProvider, variables, model, dataVaultModel, this);
  }

  @Override
  public List<WorkflowMeta> generateBuildWorkflows(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    return BvScd2PipelineSupport.generateBuildWorkflows(
        metadataProvider, variables, model, dataVaultModel, this);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    if (dataVaultModel == null) {
      return null;
    }
    BusinessVaultConfiguration bvConfig =
        model != null ? model.getConfigurationOrDefault() : new BusinessVaultConfiguration();
    return BvScd2PipelineSupport.buildTargetTableLayout(this, bvConfig, dataVaultModel, variables);
  }
}
