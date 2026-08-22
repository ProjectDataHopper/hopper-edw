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
package org.apache.hop.datavault.metadata.file;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvBusinessKeyPartSupport;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSourceFieldMappingSupport;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

public class DvCsvHubSourcePipelineBuilder extends DvFileSourcePipelineBuilder {

  public DvCsvHubSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvHub dvTable,
      Point startPoint) {
    super(
        variables,
        metadataProvider,
        model,
        pipelineMeta,
        recordSource,
        dvSource,
        dvTable,
        startPoint);
  }

  @Override
  protected ColumnMapping buildColumnMapping() throws HopException {
    DvHub hub = (DvHub) dvTable;
    if (hub.getBusinessKeys().isEmpty()) {
      throw new HopException("Please specify at least one business key in Hub " + hub.getName());
    }

    Map<String, String> sourceToTarget = new LinkedHashMap<>();
    String sourceName = variables.resolve(recordSource.getName());
    DvBusinessKeyPartSupport.putHubSourceColumnMappings(sourceToTarget, hub, sourceName, variables);

    String sourceFieldName = variables.resolve(recordSource.getSourceIndicatorField());
    if (StringUtils.isNotEmpty(sourceFieldName)) {
      String targetSourceFieldName =
          DvSourceFieldMappingSupport.findTargetSourceFieldName(configuration, recordSource, hub);
      sourceToTarget.put(sourceFieldName, targetSourceFieldName);
    }

    return columnMapping(sourceToTarget, source.getFields());
  }

  @Override
  protected TransformMeta finishSourceChain(
      TransformMeta predecessor, Point location, ColumnMapping mapping) throws HopException {
    DvHub hub = (DvHub) dvTable;
    String sourceName = variables.resolve(recordSource.getName());
    List<String> sortAndUniqueFields =
        new ArrayList<>(
            DvBusinessKeyPartSupport.resolveHubSourceIdentityStreamFields(
                hub, sourceName, variables));
    String targetSourceFieldName =
        DvSourceFieldMappingSupport.findTargetSourceFieldName(configuration, recordSource, hub);
    sortAndUniqueFields.add(variables.resolve(targetSourceFieldName));

    TransformMeta sorted = addSortRows(predecessor, location, sortAndUniqueFields);
    return addUniqueRows(sorted, location, sortAndUniqueFields);
  }
}
