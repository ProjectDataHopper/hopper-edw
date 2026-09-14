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
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.naming.NamingSchemeKind;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;

/**
 * Query-friendly Business Vault bridge: two or more hub hash keys (usually from a DV link),
 * optional weight, optional authoring SQL. Loaded by Business Vault Update.
 */
@Getter
@Setter
@NamingSchemeKind(EdwNamingSchemeTypes.BV_BRIDGE)
public class BvBridge extends BvTableBase {

  /** Optional allocation / hierarchy weight column on the bridge. */
  @HopMetadataProperty private String weightField;

  /**
   * Optional load SQL. When empty, the generated pipeline selects hub hash keys from the first
   * bound Data Vault link.
   */
  @HopMetadataProperty private String sqlQuery;

  /** Optional target schema for the physical bridge table. */
  @HopMetadataProperty private String schemaName;

  public BvBridge() {
    super(BvTableType.BRIDGE);
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel) {
    super.check(remarks, metadataProvider, variables, model, dataVaultModel);
    BvBridgeValidationSupport.validate(
        remarks, this, model, dataVaultModel, metadataProvider, variables);
  }

  @Override
  public List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    return BvBridgePipelineSupport.generateBuildPipelines(
        metadataProvider, variables, model, dataVaultModel, this);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    return BvBridgeLayoutSupport.buildTargetTableLayout(
        this, model, dataVaultModel, variables, metadataProvider);
  }
}
