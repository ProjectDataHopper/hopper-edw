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
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionLoadStrategySupport.DmDimensionLoadStrategy;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmDimensionLookupBuilder;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmInsertUpdateBuilder;
import org.apache.hop.datavault.metadata.dimensional.pipeline.DmScd2DimensionBuilder;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Kimball dimension table. */
@Getter
@Setter
public class DmDimension extends DmTableBase {

  /**
   * @deprecated Load strategy is derived from per-attribute {@link DmScdUpdatePolicy} values.
   *     Retained for deserializing legacy {@code .hdm} files; not written on save.
   */
  @Deprecated
  @HopMetadataProperty(storeWithCode = true)
  private DmDimensionScdType scdType = DmDimensionScdType.TYPE1;

  @HopMetadataProperty(key = "natural_key", groupKey = "natural_keys")
  private List<DmNaturalKeyField> naturalKeys = new ArrayList<>();

  @HopMetadataProperty(key = "attribute", groupKey = "attributes")
  private List<DmDimensionAttribute> attributes = new ArrayList<>();

  @HopMetadataProperty(key = "outrigger", groupKey = "outriggers")
  private List<DmDimensionOutriggerRef> outriggers = new ArrayList<>();

  @HopMetadataProperty private String surrogateKeyField;

  @HopMetadataProperty(storeWithCode = true)
  private DmSurrogateKeyStrategy surrogateKeyStrategy;

  @HopMetadataProperty private String surrogateKeySourceField;

  /**
   * Optional source column used as the SCD2 effective start ({@code dateFromField}) on new
   * versions. When empty, the dimensional update load timestamp is used.
   */
  @HopMetadataProperty private String effectiveDateSourceField;

  public DmDimension() {
    super(DmTableType.DIMENSION);
  }

  /**
   * @deprecated Use {@link DmDimensionLoadStrategySupport#resolveDerivedScdType(DmDimension)}.
   */
  @Deprecated
  public DmDimensionScdType getScdTypeOrDefault() {
    return DmDimensionLoadStrategySupport.resolveDerivedScdType(this);
  }

  public DmDimensionLoadStrategy resolveLoadStrategy() {
    return DmDimensionLoadStrategySupport.resolveLoadStrategy(this);
  }

  public List<DmNaturalKeyField> getNaturalKeysOrEmpty() {
    return naturalKeys != null ? naturalKeys : List.of();
  }

  public List<DmDimensionAttribute> getAttributesOrEmpty() {
    return attributes != null ? attributes : List.of();
  }

  public List<DmDimensionOutriggerRef> getOutriggersOrEmpty() {
    return outriggers != null ? outriggers : List.of();
  }

  public String resolveEffectiveDateSourceField(IVariables variables) {
    if (Utils.isEmpty(effectiveDateSourceField)) {
      return null;
    }
    return variables != null
        ? variables.resolve(effectiveDateSourceField)
        : effectiveDateSourceField;
  }

  /**
   * Materializes per-attribute policies from a legacy stored {@link #scdType} when attributes lack
   * explicit policies.
   */
  public void normalizeLegacyScdType() {
    if (hasExplicitAttributePolicies()) {
      return;
    }
    DmDimensionScdType legacy = scdType != null ? scdType : DmDimensionScdType.TYPE1;
    if (legacy == DmDimensionScdType.HYBRID) {
      return;
    }
    DmScdUpdatePolicy defaultPolicy =
        switch (legacy) {
          case TYPE2 -> DmScdUpdatePolicy.TYPE2;
          case TYPE3 -> DmScdUpdatePolicy.TYPE3_CURRENT;
          default -> DmScdUpdatePolicy.TYPE1;
        };
    for (DmDimensionAttribute attribute : getAttributesOrEmpty()) {
      if (attribute != null && attribute.getScdUpdatePolicy() == null) {
        attribute.setScdUpdatePolicy(defaultPolicy);
      }
    }
  }

  private boolean hasExplicitAttributePolicies() {
    for (DmDimensionAttribute attribute : getAttributesOrEmpty()) {
      if (attribute != null && attribute.getScdUpdatePolicy() != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model) {
    normalizeLegacyScdType();
    super.check(remarks, metadataProvider, variables, model);
    DmValidationSupport.validateDimension(remarks, this, model, metadataProvider, variables);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DimensionalModel model)
      throws HopException {
    normalizeLegacyScdType();
    DimensionalConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DimensionalConfiguration();
    IRowMeta sourceRowMeta =
        DmSourceFieldResolutionSupport.tryResolveSourceRowMeta(
            metadataProvider, variables, model, this);
    return DmLayoutSupport.buildDimensionTargetTableLayout(this, config, variables, sourceRowMeta);
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model,
      Date loadTimestamp)
      throws HopException {
    normalizeLegacyScdType();
    PipelineMeta pipelineMeta =
        switch (resolveLoadStrategy()) {
          case PURE_TYPE2 ->
              DmScd2DimensionBuilder.generatePipeline(
                  metadataProvider, variables, model, this, loadTimestamp);
          case DIMENSION_LOOKUP ->
              DmDimensionLookupBuilder.generateHybridDimensionPipeline(
                  metadataProvider, variables, model, this);
          default ->
              DmInsertUpdateBuilder.generatePipeline(metadataProvider, variables, model, this);
        };
    return List.of(pipelineMeta);
  }
}
