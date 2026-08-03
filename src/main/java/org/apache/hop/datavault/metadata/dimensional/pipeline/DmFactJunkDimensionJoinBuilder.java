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
package org.apache.hop.datavault.metadata.dimensional.pipeline;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmFactJunkDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimension;
import org.apache.hop.datavault.metadata.dimensional.DmJunkDimensionSupport;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Configures JunkDimension transforms for fact junk dimension roles. */
public final class DmFactJunkDimensionJoinBuilder {

  private DmFactJunkDimensionJoinBuilder() {}

  public static TransformMeta wireJunkDimensionRoles(
      DmPipelineBuilderSupport.BuildContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DimensionalModel model,
      List<DmFactJunkDimensionRole> roles)
      throws HopException {
    if (predecessor == null || roles == null || roles.isEmpty()) {
      return predecessor;
    }
    TransformMeta current = predecessor;
    for (DmFactJunkDimensionRole role : roles) {
      if (role == null || Utils.isEmpty(role.getJunkDimensionTableName())) {
        continue;
      }
      DmJunkDimension junkDimension =
          DmJunkDimensionSupport.resolveJunkDimension(
              model, role.getJunkDimensionTableName(), ctx.variables);
      if (junkDimension == null) {
        throw new HopException(
            "Junk dimension '" + role.getJunkDimensionTableName() + "' was not found on the model");
      }
      if (!DmJunkDimensionSupport.isFactEmbedded(junkDimension)) {
        throw new HopException(
            "Junk dimension '"
                + junkDimension.getName()
                + "' is referenced on fact "
                + ctx.table.getName()
                + " but its Source type is not Fact table (inline)");
      }
      current =
          DmJunkDimensionBuilder.addJunkDimension(ctx, pipelineMeta, current, junkDimension, role);
    }
    return current;
  }
}
