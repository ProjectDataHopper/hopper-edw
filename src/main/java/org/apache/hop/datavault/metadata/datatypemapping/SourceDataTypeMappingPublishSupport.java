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
import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Resolves physical source fields through data type mappings into the catalog {@link SourceField}
 * layout (effective names, types, lengths, conversion options).
 */
public final class SourceDataTypeMappingPublishSupport {

  private SourceDataTypeMappingPublishSupport() {}

  /**
   * Apply mapping profiles and field overrides for a source-model target. When the target has no
   * mappings configured, returns a simple projection of the physical fields.
   */
  public static List<SourceField> toEffectiveSourceFields(
      IDataTypeMappingTarget target,
      List<PhysicalSourceField> physicalFields,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<PhysicalSourceField> physical =
        physicalFields != null ? physicalFields : List.of();
    if (target == null
        || ((target.getDataTypeMappingNames() == null || target.getDataTypeMappingNames().isEmpty())
            && (target.getFieldTypeMappings() == null
                || target.getFieldTypeMappings().isEmpty()))) {
      return physicalToSourceFields(physical);
    }
    List<DataTypeMappingMeta> profiles =
        DataTypeMappingResolver.loadProfiles(metadataProvider, target.getDataTypeMappingNames());
    List<EffectiveSourceField> effective =
        DataTypeMappingResolver.resolveAll(
            physical, profiles, target.getFieldTypeMappings());
    List<SourceField> fields = new ArrayList<>();
    for (EffectiveSourceField field : effective) {
      if (field != null) {
        fields.add(field.toSourceField());
      }
    }
    return fields;
  }

  public static List<SourceField> physicalToSourceFields(List<PhysicalSourceField> physicalFields) {
    List<SourceField> fields = new ArrayList<>();
    if (physicalFields == null) {
      return fields;
    }
    for (PhysicalSourceField physical : physicalFields) {
      if (physical == null || org.apache.hop.core.util.Utils.isEmpty(physical.getName())) {
        continue;
      }
      SourceField field = new SourceField(physical.getName().trim());
      field.setDescription(physical.getDescription());
      field.setSourceDataType(physical.getSourceDataType());
      field.setLength(physical.getLength());
      field.setPrecision(physical.getPrecision());
      field.setHopType(physical.effectiveHopType());
      field.setPrimaryKeyPosition(physical.getPrimaryKeyPosition());
      if (physical.getParseConversion() != null && physical.getParseConversion().hasAnyAttribute()) {
        org.apache.hop.datavault.metadata.SourceFieldInputOptions inputOptions =
            new org.apache.hop.datavault.metadata.SourceFieldInputOptions();
        inputOptions.setConversion(new FieldConversionOptions(physical.getParseConversion()));
        field.setInputOptions(inputOptions);
      }
      fields.add(field);
    }
    return fields;
  }
}
