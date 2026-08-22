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

import java.util.Date;
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

/**
 * Logical dimension alias referencing another {@link DmDimension} without redefining keys or
 * attributes. Used for role-playing dimensions such as order date vs shipment date.
 */
@Getter
@Setter
public class DmDimensionAlias extends DmTableBase {

  private static final Class<?> PKG = DmDimensionAlias.class;

  @HopMetadataProperty private String referencedDimensionName;

  /** Optional path to another .hdm file containing the referenced dimension. */
  @HopMetadataProperty private String referencedModelFilename;

  public DmDimensionAlias() {
    super(DmTableType.DIMENSION_ALIAS);
  }

  public void syncPhysicalTableName(DimensionalModel model, IVariables variables) {
    syncPhysicalTableName(model, variables, null);
  }

  public void syncPhysicalTableName(
      DimensionalModel model, IVariables variables, IHopMetadataProvider metadataProvider) {
    DmDimension target =
        DmDimensionResolutionSupport.resolveAliasTarget(model, this, variables, metadataProvider);
    if (target != null && !Utils.isEmpty(target.getTableName())) {
      setTableName(target.getTableName());
    } else if (target != null && !Utils.isEmpty(target.getName())) {
      setTableName(target.getName());
    }
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model) {
    if (Utils.isEmpty(getName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DmTableBase.CheckResult.MissingName"),
              this));
    }
    syncPhysicalTableName(model, variables, metadataProvider);
    DmValidationSupport.validateDimensionAlias(remarks, this, model, metadataProvider, variables);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DimensionalModel model)
      throws HopException {
    DmDimension target =
        DmDimensionResolutionSupport.resolveAliasTarget(model, this, variables, metadataProvider);
    if (target == null) {
      throw new HopException(
          "Dimension alias '" + getName() + "' has no resolvable target dimension");
    }
    return target.getTargetTableLayout(metadataProvider, variables, model);
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
