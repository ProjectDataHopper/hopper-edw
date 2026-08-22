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
package org.hopper.edw.datavault.metadata.pipeline;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvBusinessKeyPartSupport;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvSourceFieldMappingSupport;
import org.hopper.edw.datavault.metadata.IDvSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Pipeline hub source: MetaInject + sort/distinct on hub identity fields. */
public class DvPipelineHubSourcePipelineBuilder extends DvPipelineSourcePipelineBuilder {

  public DvPipelineHubSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvHub hub,
      Point startPoint) {
    super(
        variables, metadataProvider, model, pipelineMeta, recordSource, dvSource, hub, startPoint);
  }

  @Override
  protected TransformMeta finishSourceChain(TransformMeta predecessor) throws HopException {
    DvHub hub = (DvHub) dvTable;
    String sourceName = variables.resolve(recordSource.getName());
    List<String> sortAndUniqueFields =
        new ArrayList<>(
            DvBusinessKeyPartSupport.resolveHubSourceIdentityStreamFields(
                hub, sourceName, variables));
    String targetSourceFieldName =
        DvSourceFieldMappingSupport.findTargetSourceFieldName(configuration, recordSource, hub);
    sortAndUniqueFields.add(variables.resolve(targetSourceFieldName));

    TransformMeta sorted = addSortRows(predecessor, sortAndUniqueFields);
    return addUniqueRows(sorted, sortAndUniqueFields);
  }
}
