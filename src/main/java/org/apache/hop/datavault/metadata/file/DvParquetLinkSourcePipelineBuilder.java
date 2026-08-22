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

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DrivingKeySource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvLinkHubSourceKeyFieldSupport;
import org.apache.hop.datavault.metadata.DvSourceFieldMappingSupport;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

@Getter
@Setter
public class DvParquetLinkSourcePipelineBuilder extends DvParquetFileSourcePipelineBuilder {

  private DvLink.DvLinkHubSource dvLinkHubSource;

  public DvParquetLinkSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvLink dvTable,
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
    DvLink link = (DvLink) dvTable;
    if (dvLinkHubSource == null) {
      throw new HopException("No DV link hub source was configured");
    }

    Map<String, String> sourceToTarget = new LinkedHashMap<>();
    for (String hubName : link.getHubNames()) {
      DvHub hub = findHub(hubName);
      DvLink.HubSourceKeyField sourceKeyField =
          DvLinkHubSourceKeyFieldSupport.findHubSourceKeyField(dvLinkHubSource, hubName);
      for (DvLinkHubSourceKeyFieldSupport.ResolvedBusinessKeySource resolvedMapping :
          DvLinkHubSourceKeyFieldSupport.resolveBusinessKeySources(
              hub, sourceKeyField, variables)) {
        if (hub.findBusinessKey(resolvedMapping.getBusinessKeyField()) == null) {
          throw new HopException(
              "The specified business key field "
                  + resolvedMapping.getBusinessKeyField()
                  + " can not be found in Link table "
                  + link.getName()
                  + " with record source "
                  + recordSource.getName());
        }
        sourceToTarget.put(
            resolvedMapping.getSourceFieldName(), resolvedMapping.getSourceFieldName());
      }
      if (sourceKeyField.getDrivingKeySources() != null) {
        for (DrivingKeySource keySource : sourceKeyField.getDrivingKeySources()) {
          String drivingKey = keySource.getDrivingKey();
          String drivingKeySource = keySource.getSourceField();
          if (!link.getDrivingKeyNames().contains(drivingKey)) {
            throw new HopException(
                "The referenced driving key "
                    + drivingKey
                    + " doesn't exist in Link table "
                    + link.getName()
                    + ", reading from source "
                    + recordSource.getName());
          }
          sourceToTarget.put(drivingKeySource, drivingKeySource);
        }
      }
    }

    if (link.getDependentChildKeys() != null) {
      for (org.apache.hop.datavault.metadata.DependentChildKey dck : link.getDependentChildKeys()) {
        if (dck == null) {
          continue;
        }
        String depSource = variables.resolve(dck.resolveSourceFieldName());
        if (depSource != null && !depSource.isEmpty()) {
          sourceToTarget.put(depSource, depSource);
        }
      }
    }

    String sourceFieldName = variables.resolve(recordSource.getSourceIndicatorField());
    if (StringUtils.isNotEmpty(sourceFieldName)) {
      String targetSourceFieldName =
          DvSourceFieldMappingSupport.findTargetSourceFieldName(configuration, recordSource, link);
      sourceToTarget.put(sourceFieldName, targetSourceFieldName);
    }

    return columnMapping(sourceToTarget, parquetSource.getFields());
  }
}
