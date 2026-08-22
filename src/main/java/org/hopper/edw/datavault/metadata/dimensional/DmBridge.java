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
package org.hopper.edw.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.dimensional.pipeline.DmBridgeLoadBuilder;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Kimball bridge table for many-to-many relationships. */
@Getter
@Setter
public class DmBridge extends DmTableBase {

  @HopMetadataProperty(key = "dimension_ref", groupKey = "dimension_refs")
  private List<DmBridgeDimensionRef> dimensionRefs = new ArrayList<>();

  @HopMetadataProperty private String weightField;

  public DmBridge() {
    super(DmTableType.BRIDGE);
  }

  public List<DmBridgeDimensionRef> getDimensionRefsOrEmpty() {
    return dimensionRefs != null ? dimensionRefs : List.of();
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model) {
    super.check(remarks, metadataProvider, variables, model);
    DmValidationSupport.validateBridge(remarks, this, model, metadataProvider, variables);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DimensionalModel model)
      throws HopException {
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    return DmLayoutSupport.buildBridgeTargetTableLayout(
        this, model, config, variables, metadataProvider);
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model,
      Date loadTimestamp)
      throws HopException {
    return List.of(DmBridgeLoadBuilder.generatePipeline(metadataProvider, variables, model, this));
  }
}
