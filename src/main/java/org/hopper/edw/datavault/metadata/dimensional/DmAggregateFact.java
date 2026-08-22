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
import org.hopper.edw.datavault.metadata.dimensional.pipeline.DmFactLikeLoadBuilder;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Kimball aggregate fact at a reduced grain. */
@Getter
@Setter
public class DmAggregateFact extends DmTableBase implements IDmFactLikeTable {

  @HopMetadataProperty private String baseFactTableName;

  @HopMetadataProperty(key = "dimension_role", groupKey = "dimension_roles")
  private List<DmFactDimensionRole> dimensionRoles = new ArrayList<>();

  @HopMetadataProperty(key = "measure", groupKey = "measures")
  private List<DmFactMeasure> measures = new ArrayList<>();

  @HopMetadataProperty(key = "degenerate_dimension", groupKey = "degenerate_dimensions")
  private List<DmFactDegenerateDimension> degenerateDimensions = new ArrayList<>();

  @HopMetadataProperty(key = "range_dimension_role", groupKey = "range_dimension_roles")
  private List<DmFactRangeDimensionRole> rangeDimensionRoles = new ArrayList<>();

  @HopMetadataProperty(key = "junk_dimension_role", groupKey = "junk_dimension_roles")
  private List<DmFactJunkDimensionRole> junkDimensionRoles = new ArrayList<>();

  public DmAggregateFact() {
    super(DmTableType.AGGREGATE_FACT);
  }

  @Override
  public List<DmFactDimensionRole> getDimensionRolesOrEmpty() {
    return dimensionRoles != null ? dimensionRoles : List.of();
  }

  @Override
  public List<DmFactMeasure> getMeasuresOrEmpty() {
    return measures != null ? measures : List.of();
  }

  @Override
  public List<DmFactDegenerateDimension> getDegenerateDimensionsOrEmpty() {
    return degenerateDimensions != null ? degenerateDimensions : List.of();
  }

  @Override
  public List<DmFactRangeDimensionRole> getRangeDimensionRolesOrEmpty() {
    return rangeDimensionRoles != null ? rangeDimensionRoles : List.of();
  }

  @Override
  public List<DmFactJunkDimensionRole> getJunkDimensionRolesOrEmpty() {
    return junkDimensionRoles != null ? junkDimensionRoles : List.of();
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model) {
    super.check(remarks, metadataProvider, variables, model);
    DmValidationSupport.validateAggregateFact(remarks, this, model, metadataProvider, variables);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DimensionalModel model)
      throws HopException {
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    return DmLayoutSupport.buildAggregateFactTargetTableLayout(
        this, model, config, variables, metadataProvider);
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model,
      Date loadTimestamp)
      throws HopException {
    return List.of(
        DmFactLikeLoadBuilder.generatePipeline(metadataProvider, variables, model, this));
  }
}
