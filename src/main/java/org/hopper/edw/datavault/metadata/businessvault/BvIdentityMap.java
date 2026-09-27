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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.naming.NamingSchemeKind;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;

/**
 * Shared raw-to-durable hash map for one hub. Generated maps upsert from a same-as link and do not
 * truncate, so a durable key survives the next load. An external map is a table that already has
 * the same columns.
 */
@Getter
@Setter
@NamingSchemeKind(EdwNamingSchemeTypes.BV_IDENTITY_MAP)
public class BvIdentityMap extends BvTableBase {

  private static final Class<?> PKG = BvIdentityMap.class;

  @HopMetadataProperty private String parentHubName;

  @HopMetadataProperty private String sameAsLinkName;

  @HopMetadataProperty private String masterHashField;

  @HopMetadataProperty private String duplicateHashField;

  @HopMetadataProperty private String preferredBusinessKeyField;

  @HopMetadataProperty private String mdmIdField;

  @HopMetadataProperty private String effectivitySatelliteName;

  @HopMetadataProperty private String effectivityFromField;

  @HopMetadataProperty private String effectivityToField;

  @HopMetadataProperty(storeWithCode = true)
  private BvIdentityUnmappedPolicy unmappedPolicy = BvIdentityUnmappedPolicy.SELF;

  @HopMetadataProperty(storeWithCode = true)
  private BvIdentityMapMode mapMode = BvIdentityMapMode.GENERATED;

  public BvIdentityMap() {
    super(BvTableType.IDENTITY_MAP);
  }

  public BvIdentityUnmappedPolicy getUnmappedPolicyOrDefault() {
    return unmappedPolicy != null ? unmappedPolicy : BvIdentityUnmappedPolicy.SELF;
  }

  public BvIdentityMapMode getMapModeOrDefault() {
    return mapMode != null ? mapMode : BvIdentityMapMode.GENERATED;
  }

  public boolean isExternal() {
    return getMapModeOrDefault() == BvIdentityMapMode.EXTERNAL;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel) {
    super.check(remarks, metadataProvider, variables, model, dataVaultModel);
    if (Utils.isEmpty(parentHubName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvIdentityMap.CheckResult.MissingParentHub", getName()),
              this));
    }
    if (isExternal()) {
      return;
    }
    if (Utils.isEmpty(sameAsLinkName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvIdentityMap.CheckResult.MissingLink", getName()),
              this));
    } else if (dataVaultModel != null
        && dataVaultModel.findLink(resolve(sameAsLinkName, variables), variables, metadataProvider)
            == null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvIdentityMap.CheckResult.UnknownLink", getName(), sameAsLinkName),
              this));
    }
    if (Utils.isEmpty(masterHashField) || Utils.isEmpty(duplicateHashField)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvIdentityMap.CheckResult.MissingRoles", getName()),
              this));
    } else if (masterHashField.equalsIgnoreCase(duplicateHashField)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvIdentityMap.CheckResult.SameRole", getName()),
              this));
    }
    if (Utils.isEmpty(effectivitySatelliteName)) {
      return;
    }
    if (Utils.isEmpty(effectivityFromField) || Utils.isEmpty(effectivityToField)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvIdentityMap.CheckResult.MissingEffectivityColumns",
                  getName(),
                  effectivitySatelliteName),
              this));
    }
    if (dataVaultModel != null) {
      IDvTable effectivity =
          dataVaultModel.findTable(resolve(effectivitySatelliteName, variables));
      if (!(effectivity instanceof DvSatellite)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvIdentityMap.CheckResult.UnknownEffectivitySatellite",
                    getName(),
                    effectivitySatelliteName),
                this));
      }
    }
  }

  @Override
  public List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    if (isExternal()) {
      return List.of();
    }
    return BvIdentityMapPipelineSupport.generateBuildPipelines(
        metadataProvider, variables, model, dataVaultModel, this);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    return BvIdentityMapPipelineSupport.buildTargetLayout(this, model, dataVaultModel, variables);
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables == null ? value : variables.resolve(value);
  }
}
