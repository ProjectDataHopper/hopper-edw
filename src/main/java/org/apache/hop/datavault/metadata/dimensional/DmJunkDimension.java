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
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmJunkDimensionBuilder;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Kimball junk dimension maintained via Combination Lookup. */
@Getter
@Setter
public class DmJunkDimension extends DmTableBase {

  @HopMetadataProperty(key = "key_field", groupKey = "key_fields")
  private List<DmNaturalKeyField> keyFields = new ArrayList<>();

  @HopMetadataProperty private String surrogateKeyField;

  @HopMetadataProperty(storeWithCode = true)
  private DmJunkSurrogateKeyStrategy surrogateKeyStrategy;

  @HopMetadataProperty private String surrogateKeySourceField;

  @HopMetadataProperty private boolean loadFromFactTable;

  @HopMetadataProperty private String factTableName;

  @HopMetadataProperty(storeWithCode = true)
  private DmJunkHashCodeStrategy hashCodeStrategy;

  @HopMetadataProperty private String hashCodeField;

  /** When true, the surrogate key column also serves as the hash lookup column (DV hub style). */
  @HopMetadataProperty private boolean useSurrogateKeyAsHashCodeField;

  public DmJunkDimension() {
    super(DmTableType.JUNK_DIMENSION);
  }

  public List<DmNaturalKeyField> getKeyFieldsOrEmpty() {
    return keyFields != null ? keyFields : List.of();
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model) {
    super.check(remarks, metadataProvider, variables, model);
    DmValidationSupport.validateJunkDimension(remarks, this, metadataProvider, variables);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DimensionalModel model)
      throws HopException {
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    return DmLayoutSupport.buildJunkDimensionTargetTableLayout(this, config, variables);
  }

  public DmJunkHashCodeStrategy getHashCodeStrategyOrDefault() {
    return hashCodeStrategy != null ? hashCodeStrategy : DmJunkHashCodeStrategy.INTEGER_LEGACY;
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model,
      Date loadTimestamp)
      throws HopException {
    if (DmJunkDimensionSupport.isFactEmbedded(this)) {
      return List.of();
    }
    return List.of(
        DmJunkDimensionBuilder.generatePipeline(metadataProvider, variables, model, this));
  }
}
