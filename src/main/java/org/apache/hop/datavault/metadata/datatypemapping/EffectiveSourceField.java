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
package org.apache.hop.datavault.metadata.datatypemapping;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.SourceField;

/**
 * Resolved pre-model field layout: physical source field plus applied mapping profile(s) and
 * overrides. This is what catalog publish, modelers, and load pipelines should consume.
 */
@Getter
@Setter
@NoArgsConstructor
public class EffectiveSourceField {

  private String sourceFieldName;
  private String effectiveFieldName;
  private String description;
  private String sourceDataType;
  private int hopType;
  private String length;
  private String precision;
  private int primaryKeyPosition;
  private FieldConversionOptions conversion = new FieldConversionOptions();

  /** Provenance notes for UI (e.g. profile:rule ids). */
  private List<String> provenance = new ArrayList<>();

  private boolean typeChanged;
  private boolean lengthChanged;
  private boolean renamed;
  private boolean conversionChanged;

  public FieldConversionOptions getConversion() {
    if (conversion == null) {
      conversion = new FieldConversionOptions();
    }
    return conversion;
  }

  public List<String> getProvenance() {
    if (provenance == null) {
      provenance = new ArrayList<>();
    }
    return provenance;
  }

  public void addProvenance(String note) {
    if (!Utils.isEmpty(note)) {
      getProvenance().add(note);
    }
  }

  public boolean isPrimaryKey() {
    return primaryKeyPosition > 0;
  }

  public boolean isMapped() {
    return typeChanged || lengthChanged || renamed || conversionChanged;
  }

  /** Convert to a catalog/DV {@link SourceField} using effective attributes. */
  public SourceField toSourceField() {
    SourceField field = new SourceField(effectiveFieldName);
    field.setDescription(description);
    field.setSourceDataType(sourceDataType);
    field.setLength(length);
    field.setPrecision(precision);
    field.setHopType(hopType > 0 ? hopType : IValueMeta.TYPE_STRING);
    field.setPrimaryKeyPosition(primaryKeyPosition);
    if (renamed && !Utils.isEmpty(sourceFieldName) && !sourceFieldName.equals(effectiveFieldName)) {
      field.setSourceStreamName(sourceFieldName);
    }
    if (conversion != null && conversion.hasAnyAttribute()) {
      org.apache.hop.datavault.metadata.SourceFieldInputOptions inputOptions =
          field.getInputOptions();
      if (inputOptions == null) {
        inputOptions = new org.apache.hop.datavault.metadata.SourceFieldInputOptions();
        field.setInputOptions(inputOptions);
      }
      inputOptions.setConversion(new FieldConversionOptions(conversion));
    }
    return field;
  }

  public int effectiveHopType() {
    return hopType > 0 ? hopType : IValueMeta.TYPE_STRING;
  }
}
