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
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvTableType;

/** Business Vault SCD2 table derived from one or more DV satellites. */
@Getter
@Setter
public class BvScd2Table extends BvTableBase {

  private static final Class<?> PKG = BvScd2Table.class;

  @HopMetadataProperty(storeWithCode = true)
  private BvScd2BuildMode buildMode = BvScd2BuildMode.FULL_REBUILD;

  @HopMetadataProperty private String functionalTimestampField;

  @HopMetadataProperty private String incrementalWatermarkField;

  @HopMetadataProperty private String validFromField;

  @HopMetadataProperty private String validToField;

  @HopMetadataProperty private boolean includeHashKey = true;

  @HopMetadataProperty(key = "field_mapping", groupKey = "field_mappings")
  private List<BvScd2FieldMapping> fieldMappings = new ArrayList<>();

  @HopMetadataProperty(key = "satellite_config", groupKey = "satellite_configs")
  private List<BvScd2SatelliteConfig> satelliteConfigs = new ArrayList<>();

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

  public BvScd2BuildMode getBuildModeOrDefault() {
    return buildMode != null ? buildMode : BvScd2BuildMode.FULL_REBUILD;
  }

  public boolean isIncrementalBuild() {
    return getBuildModeOrDefault() == BvScd2BuildMode.INCREMENTAL;
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

    if (dataVaultModel != null) {
      BvScd2FieldMappingValidationSupport.validate(
          remarks, this, bvConfig, dvConfig, dataVaultModel, variables);
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
