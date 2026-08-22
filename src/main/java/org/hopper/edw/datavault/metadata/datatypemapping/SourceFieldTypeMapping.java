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
package org.hopper.edw.datavault.metadata.datatypemapping;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Per-source field fine-tune for data type mapping (stored on HSM table/query/json/pipeline cards).
 * Applied after project-level profiles; non-empty attributes win.
 */
@Getter
@Setter
@NoArgsConstructor
public class SourceFieldTypeMapping {

  /** Physical / Fields-tab name. */
  @HopMetadataProperty private String sourceFieldName;

  /** Rename; empty keeps the source name. */
  @HopMetadataProperty private String targetFieldName;

  @HopMetadataProperty(intCodeConverter = ValueMetaBase.ValueTypeCodeConverter.class)
  private int targetHopType = IValueMeta.TYPE_NONE;

  @HopMetadataProperty private String length;
  @HopMetadataProperty private String precision;

  @HopMetadataProperty private FieldConversionOptions conversion = new FieldConversionOptions();

  @HopMetadataProperty private boolean disabled;

  public SourceFieldTypeMapping(String sourceFieldName) {
    this.sourceFieldName = sourceFieldName;
  }

  public FieldConversionOptions getConversion() {
    if (conversion == null) {
      conversion = new FieldConversionOptions();
    }
    return conversion;
  }
}
