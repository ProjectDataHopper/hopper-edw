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
package org.apache.hop.datavault.metadata.datatypemapping;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonField;
import org.apache.hop.datavault.metadata.sourcemodel.SourcePipeline;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolve and validate data type mappings for source-model entities. */
public final class SourceDataTypeMappingSupport {

  private SourceDataTypeMappingSupport() {}

  public static List<PhysicalSourceField> physicalFields(SourceTable table) {
    List<PhysicalSourceField> fields = new ArrayList<>();
    if (table == null) {
      return fields;
    }
    for (SourceColumn column : table.getColumns()) {
      PhysicalSourceField physical = PhysicalSourceField.from(column);
      if (physical != null) {
        fields.add(physical);
      }
    }
    return fields;
  }

  public static List<PhysicalSourceField> physicalFields(SourcePipeline pipeline) {
    List<PhysicalSourceField> fields = new ArrayList<>();
    if (pipeline == null) {
      return fields;
    }
    for (SourceColumn column : pipeline.getFields()) {
      PhysicalSourceField physical = PhysicalSourceField.from(column);
      if (physical != null) {
        fields.add(physical);
      }
    }
    return fields;
  }

  public static List<PhysicalSourceField> physicalFields(SourceJson jsonSource) {
    List<PhysicalSourceField> fields = new ArrayList<>();
    if (jsonSource == null) {
      return fields;
    }
    for (SourceJsonField field : jsonSource.getFields()) {
      PhysicalSourceField physical = PhysicalSourceField.from(field);
      if (physical != null && !Utils.isEmpty(physical.getName())) {
        fields.add(physical);
      }
    }
    return fields;
  }

  public static List<PhysicalSourceField> physicalFields(SourceQuery query, SourceTable driving) {
    List<PhysicalSourceField> fields = new ArrayList<>();
    if (query == null) {
      return fields;
    }
    for (SourceQueryColumn column : query.getColumns()) {
      if (column == null) {
        continue;
      }
      PhysicalSourceField physical = new PhysicalSourceField();
      String name = column.resolveAlias();
      if (Utils.isEmpty(name)) {
        name = column.getColumnName();
      }
      physical.setName(name);
      // Query columns often lack hop type until generation; leave as String baseline.
      if (driving != null && !Utils.isEmpty(column.getColumnName())) {
        SourceColumn sourceColumn = driving.findColumn(column.getColumnName());
        if (sourceColumn != null) {
          physical.setHopType(sourceColumn.getHopType());
          physical.setLength(sourceColumn.getLength());
          physical.setPrecision(sourceColumn.getPrecision());
          physical.setSourceDataType(sourceColumn.getSourceDataType());
        }
      }
      physical.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
      fields.add(physical);
    }
    return fields;
  }

  public static List<EffectiveSourceField> resolve(
      IDataTypeMappingTarget target,
      List<PhysicalSourceField> physicalFields,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (target == null) {
      return DataTypeMappingResolver.resolveAll(physicalFields, List.of(), List.of());
    }
    List<DataTypeMappingMeta> profiles =
        DataTypeMappingResolver.loadProfiles(metadataProvider, target.getDataTypeMappingNames());
    return DataTypeMappingResolver.resolveAll(
        physicalFields, profiles, target.getFieldTypeMappings());
  }

  public static List<ICheckResult> check(
      String sourceLabel,
      IDataTypeMappingTarget target,
      List<PhysicalSourceField> physicalFields,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<ICheckResult> remarks = new ArrayList<>();
    if (target == null) {
      return remarks;
    }
    List<DataTypeMappingMeta> profiles =
        DataTypeMappingResolver.loadProfiles(metadataProvider, target.getDataTypeMappingNames());
    for (DataTypeMappingMeta profile : profiles) {
      remarks.addAll(DataTypeMappingValidationSupport.checkProfile(profile));
    }
    // Missing profile names
    if (target.getDataTypeMappingNames() != null) {
      for (String name : target.getDataTypeMappingNames()) {
        if (Utils.isEmpty(name)) {
          continue;
        }
        boolean found = profiles.stream().anyMatch(p -> name.equals(p.getName()));
        if (!found) {
          remarks.add(
              new org.apache.hop.core.CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  "Data type mapping profile '" + name + "' not found in project metadata",
                  null));
        }
      }
    }
    List<EffectiveSourceField> effective =
        DataTypeMappingResolver.resolveAll(
            physicalFields, profiles, target.getFieldTypeMappings());
    remarks.addAll(
        DataTypeMappingValidationSupport.checkEffective(
            sourceLabel, physicalFields, effective, target.getFieldTypeMappings()));
    return remarks;
  }
}
