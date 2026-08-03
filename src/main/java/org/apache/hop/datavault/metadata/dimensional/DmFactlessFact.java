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
package org.apache.hop.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmFactLikeLoadBuilder;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Kimball factless fact (dimension foreign keys only). */
@Getter
@Setter
public class DmFactlessFact extends DmTableBase implements IDmFactLikeTable {

  @HopMetadataProperty(key = "dimension_role", groupKey = "dimension_roles")
  private List<DmFactDimensionRole> dimensionRoles = new ArrayList<>();

  @HopMetadataProperty(key = "degenerate_dimension", groupKey = "degenerate_dimensions")
  private List<DmFactDegenerateDimension> degenerateDimensions = new ArrayList<>();

  @HopMetadataProperty(key = "range_dimension_role", groupKey = "range_dimension_roles")
  private List<DmFactRangeDimensionRole> rangeDimensionRoles = new ArrayList<>();

  @HopMetadataProperty(key = "junk_dimension_role", groupKey = "junk_dimension_roles")
  private List<DmFactJunkDimensionRole> junkDimensionRoles = new ArrayList<>();

  public DmFactlessFact() {
    super(DmTableType.FACTLESS_FACT);
  }

  @Override
  public List<DmFactDimensionRole> getDimensionRolesOrEmpty() {
    return dimensionRoles != null ? dimensionRoles : List.of();
  }

  @Override
  public List<DmFactMeasure> getMeasuresOrEmpty() {
    return List.of();
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
    DmValidationSupport.validateFactlessFact(remarks, this, model, metadataProvider, variables);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DimensionalModel model)
      throws HopException {
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    return DmLayoutSupport.buildFactlessFactTargetTableLayout(
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
