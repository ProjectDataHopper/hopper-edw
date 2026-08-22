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

import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmJunkDimension;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/**
 * @deprecated Use {@link DmJunkDimensionBuilder} instead.
 */
@Deprecated
public final class DmCombinationLookupBuilder {

  private DmCombinationLookupBuilder() {}

  public static PipelineMeta generatePipeline(
      IHopMetadataProvider metadataProvider,
      org.apache.hop.core.variables.IVariables variables,
      DimensionalModel model,
      DmJunkDimension junkDimension)
      throws HopException {
    return DmJunkDimensionBuilder.generatePipeline(
        metadataProvider, variables, model, junkDimension);
  }
}
