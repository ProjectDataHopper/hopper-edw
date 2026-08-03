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
package org.apache.hop.datavault.metadata.file;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvReferenceTable;
import org.apache.hop.datavault.metadata.DvSourceFieldMappingSupport;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.SatelliteAttribute;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** CSV/delimited file source leg for {@link DvReferenceTable} FULL_REPLACE loads. */
@Getter
@Setter
public class DvCsvReferenceSourcePipelineBuilder extends DvFileSourcePipelineBuilder {

  public DvCsvReferenceSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvReferenceTable dvTable,
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
    DvReferenceTable reference = (DvReferenceTable) dvTable;
    Map<String, String> sourceToTarget = new LinkedHashMap<>();
    String sourceName = variables.resolve(recordSource.getName());

    List<BusinessKey> keys = reference.getNaturalKeysForSource(sourceName, variables);
    if (keys.isEmpty()) {
      throw new HopException(
          "Please map at least one natural key to record source "
              + sourceName
              + " on reference table "
              + reference.getName());
    }
    for (BusinessKey key : keys) {
      String targetName = variables.resolve(key.getName());
      String sourceField =
          !Utils.isEmpty(key.getSourceFieldName())
              ? variables.resolve(key.getSourceFieldName())
              : targetName;
      if (StringUtils.isNotEmpty(sourceField)) {
        sourceToTarget.put(sourceField, targetName);
      }
    }

    if (reference.getAttributes() != null) {
      for (SatelliteAttribute attr : reference.getAttributes()) {
        if (attr == null || Utils.isEmpty(attr.getName())) {
          continue;
        }
        String name = variables.resolve(attr.getName());
        sourceToTarget.put(name, name);
      }
    }

    String sourceFieldName = variables.resolve(recordSource.getSourceIndicatorField());
    if (StringUtils.isNotEmpty(sourceFieldName)) {
      String targetSourceFieldName =
          DvSourceFieldMappingSupport.findTargetSourceFieldName(
              configuration, recordSource, reference);
      sourceToTarget.put(sourceFieldName, targetSourceFieldName);
    }

    return columnMapping(sourceToTarget, source.getFields());
  }

  @Override
  protected TransformMeta finishSourceChain(
      TransformMeta predecessor, Point location, ColumnMapping mapping) throws HopException {
    // Full replace still benefits from distinct natural keys when files have duplicates.
    DvReferenceTable reference = (DvReferenceTable) dvTable;
    List<String> uniqueFields = new ArrayList<>();
    for (BusinessKey key : reference.getNaturalKeys()) {
      if (key != null && !Utils.isEmpty(key.getName())) {
        uniqueFields.add(variables.resolve(key.getName()));
      }
    }
    if (uniqueFields.isEmpty()) {
      return predecessor;
    }
    TransformMeta sorted = addSortRows(predecessor, location, uniqueFields);
    return addUniqueRows(sorted, location, uniqueFields);
  }
}
