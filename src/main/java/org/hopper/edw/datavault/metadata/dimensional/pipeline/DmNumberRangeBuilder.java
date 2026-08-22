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
package org.hopper.edw.datavault.metadata.dimensional.pipeline;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmFactRangeDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmRangeBand;
import org.hopper.edw.datavault.metadata.dimensional.DmRangeDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmRangeDimensionSupport;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.numberrange.NumberRangeMeta;

/** Configures NumberRange transforms for fact range dimension roles. */
public final class DmNumberRangeBuilder {

  private DmNumberRangeBuilder() {}

  public static TransformMeta wireRangeDimensionRoles(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DimensionalModel model,
      List<DmFactRangeDimensionRole> roles)
      throws HopException {
    if (predecessor == null || roles == null || roles.isEmpty()) {
      return predecessor;
    }
    TransformMeta current = predecessor;
    for (DmFactRangeDimensionRole role : roles) {
      if (role == null || Utils.isEmpty(role.getRangeDimensionTableName())) {
        continue;
      }
      DmRangeDimension rangeDimension =
          DmRangeDimensionSupport.resolveRangeDimension(
              model, role.getRangeDimensionTableName(), ctx.variables);
      if (rangeDimension == null) {
        throw new HopException(
            "Range dimension '"
                + role.getRangeDimensionTableName()
                + "' was not found on the model");
      }
      current = addNumberRange(ctx, pipelineMeta, current, rangeDimension, role);
    }
    return current;
  }

  private static TransformMeta addNumberRange(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DmRangeDimension rangeDimension,
      DmFactRangeDimensionRole role)
      throws HopException {
    String sourceField = resolve(ctx, role.getSourceFieldName());
    String targetField = resolve(ctx, role.getTargetFieldName());
    if (Utils.isEmpty(sourceField)) {
      throw new HopException(
          "Range dimension role on table " + ctx.table.getName() + " is missing source field name");
    }
    if (Utils.isEmpty(targetField)) {
      throw new HopException(
          "Range dimension role on table " + ctx.table.getName() + " is missing target field name");
    }

    NumberRangeMeta numberRangeMeta = new NumberRangeMeta();
    numberRangeMeta.setInputField(sourceField);
    numberRangeMeta.setOutputField(targetField);
    numberRangeMeta.setFallBackValue(
        Utils.isEmpty(rangeDimension.getFallBackLabel())
            ? "unknown"
            : resolve(ctx, rangeDimension.getFallBackLabel()));
    for (DmRangeBand band : rangeDimension.getBandsOrEmpty()) {
      if (band == null) {
        continue;
      }
      numberRangeMeta.addRule(
          ConstOrEmpty(band.getLowerBound()),
          ConstOrEmpty(band.getUpperBound()),
          resolve(ctx, band.getLabel()));
    }
    if (numberRangeMeta.getRules().isEmpty()) {
      throw new HopException(
          "Range dimension '" + rangeDimension.getName() + "' has no bands configured");
    }

    String transformName = "range_" + targetField;
    TransformMeta transformMeta = new TransformMeta("NumberRange", transformName, numberRangeMeta);
    transformMeta.setLocation(
        predecessor.getLocation().x + DmPipelineBuilderSupport.SPACING_WIDTH,
        predecessor.getLocation().y);
    pipelineMeta.addTransform(transformMeta);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, transformMeta));
    return transformMeta;
  }

  private static String resolve(DmPipelineBuilderSupport.BuildContext ctx, String value) {
    if (ctx.variables != null && value != null) {
      return ctx.variables.resolve(value);
    }
    return value;
  }

  private static String ConstOrEmpty(String value) {
    return value != null ? value : "";
  }
}
