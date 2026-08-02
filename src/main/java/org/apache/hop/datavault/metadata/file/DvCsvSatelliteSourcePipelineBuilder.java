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
 *
 */

package org.apache.hop.datavault.metadata.file;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.AttributeSource;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvLinkHubSourceKeyFieldSupport;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvSatelliteParentKeySupport;
import org.apache.hop.datavault.metadata.DvSourceFieldMappingSupport;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

@Getter
@Setter
public class DvCsvSatelliteSourcePipelineBuilder extends DvFileSourcePipelineBuilder {

  protected DvLink linkedLink;
  protected DvLink.DvLinkHubSource dvLinkHubSource;
  protected DvLink.DvLinkSatelliteSource dvLinkSatelliteSource;

  public DvCsvSatelliteSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvSatellite dvTable,
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
    DvSatellite satellite = (DvSatellite) dvTable;
    Map<String, String> sourceToTarget = new LinkedHashMap<>();

    if (!Utils.isEmpty(satellite.getLinkName())) {
      buildLinkSatelliteMapping(satellite, sourceToTarget);
    } else {
      buildHubSatelliteMapping(satellite, sourceToTarget);
    }

    return columnMapping(sourceToTarget, source.getFields());
  }

  private void buildHubSatelliteMapping(DvSatellite satellite, Map<String, String> sourceToTarget)
      throws HopException {
    DvHub hub = findHub(satellite.getHubName());
    for (DvSatelliteParentKeySupport.ParentKeyField parentKey :
        DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, variables)) {
      sourceToTarget.put(parentKey.getSourceFieldName(), parentKey.getBusinessKeyName());
    }
    if (satellite.getAttributes().isEmpty()) {
      throw new HopException(
          "Please specify at least one attribute field for satellite " + satellite.getName());
    }
    for (SatelliteAttribute attribute : satellite.getAttributes()) {
      sourceToTarget.put(attribute.getName(), attribute.getName());
    }
    if (satellite.hasDrivingKey()) {
      sourceToTarget.put(
          satellite.getDrivingKeySourceField(), satellite.getDrivingKeySourceField());
    }
    addRecordSourceMapping(satellite, sourceToTarget);
  }

  private void buildLinkSatelliteMapping(DvSatellite satellite, Map<String, String> sourceToTarget)
      throws HopException {
    if (linkedLink == null) {
      linkedLink = findLink(satellite.getLinkName());
    }
    if (linkedLink == null) {
      throw new HopException(
          "Link '"
              + satellite.getLinkName()
              + "' not found in model for link satellite "
              + satellite.getName());
    }
    if (dvLinkHubSource == null) {
      throw new HopException(
          "No link hub source mapping found on link "
              + linkedLink.getName()
              + " for record source "
              + recordSource.getName()
              + " (required for link satellite "
              + satellite.getName()
              + ")");
    }

    Set<String> added = new LinkedHashSet<>();
    for (String hubName : linkedLink.getHubNames()) {
      DvHub hub = findHub(hubName);
      DvLink.HubSourceKeyField sourceKeyField =
          DvLinkHubSourceKeyFieldSupport.findHubSourceKeyField(dvLinkHubSource, hubName);
      for (String sourceFieldName :
          DvLinkHubSourceKeyFieldSupport.resolveSourceFieldNames(hub, sourceKeyField, variables)) {
        if (added.add(sourceFieldName)) {
          sourceToTarget.put(sourceFieldName, sourceFieldName);
        }
      }
    }
    for (String dckSourceField : linkedLink.resolveDependentChildSourceFieldNames(variables)) {
      if (added.add(dckSourceField)) {
        sourceToTarget.put(dckSourceField, dckSourceField);
      }
    }
    if (sourceToTarget.isEmpty()) {
      throw new HopException(
          "No hub business key source fields found for link "
              + linkedLink.getName()
              + " and record source "
              + recordSource.getName());
    }

    boolean mappedAttributes = false;
    if (dvLinkSatelliteSource != null
        && dvLinkSatelliteSource.getSatelliteSourceKeyFields() != null) {
      for (DvLink.SatelliteSourceKeyField skf :
          dvLinkSatelliteSource.getSatelliteSourceKeyFields()) {
        if (skf == null || !satellite.getName().equals(skf.getSatelliteName())) {
          continue;
        }
        if (skf.getAttributeSources() != null) {
          for (AttributeSource attributeSource : skf.getAttributeSources()) {
            if (attributeSource != null
                && StringUtils.isNotEmpty(attributeSource.getSourceFieldName())) {
              sourceToTarget.put(
                  attributeSource.getSourceFieldName(), attributeSource.getSourceFieldName());
              mappedAttributes = true;
            }
          }
        }
      }
    }
    if (!mappedAttributes) {
      for (SatelliteAttribute attribute : satellite.getAttributes()) {
        sourceToTarget.put(attribute.getName(), attribute.getName());
      }
    }

    if (satellite.hasDrivingKey()) {
      sourceToTarget.put(
          satellite.getDrivingKeySourceField(), satellite.getDrivingKeySourceField());
    }
    addRecordSourceMapping(satellite, sourceToTarget);
  }

  private void addRecordSourceMapping(DvSatellite satellite, Map<String, String> sourceToTarget)
      throws HopException {
    String sourceFieldName = variables.resolve(recordSource.getSourceIndicatorField());
    if (StringUtils.isNotEmpty(sourceFieldName)) {
      String targetSourceFieldName =
          DvSourceFieldMappingSupport.findTargetSourceFieldName(
              configuration, recordSource, satellite);
      sourceToTarget.put(sourceFieldName, targetSourceFieldName);
    }
  }
}