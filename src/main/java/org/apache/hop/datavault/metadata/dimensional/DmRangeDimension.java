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
package org.apache.hop.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Metadata-only numeric band definition reused by fact range dimension roles. */
@Getter
@Setter
public class DmRangeDimension extends DmTableBase {

  @HopMetadataProperty private String fallBackLabel = "unknown";

  @HopMetadataProperty(key = "band", groupKey = "bands")
  private List<DmRangeBand> bands = new ArrayList<>();

  public DmRangeDimension() {
    super(DmTableType.RANGE_DIMENSION);
  }

  public List<DmRangeBand> getBandsOrEmpty() {
    return bands != null ? bands : List.of();
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model) {
    super.check(remarks, metadataProvider, variables, model);
    DmValidationSupport.validateRangeDimension(remarks, this, variables);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DimensionalModel model) {
    return new RowMeta();
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model,
      Date loadTimestamp) {
    return List.of();
  }

  @Override
  public List<String> generateBuildDdl(
      IHopMetadataProvider metadataProvider, IVariables variables, DimensionalModel model) {
    return List.of();
  }
}
