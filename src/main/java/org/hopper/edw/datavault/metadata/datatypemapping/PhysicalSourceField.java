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
import org.hopper.edw.catalog.model.CatalogSourceField;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.CsvFieldOptions;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.SourceFieldInputOptions;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJsonField;

/**
 * Neutral physical/parsed field view used as input to {@link DataTypeMappingResolver}. Adapts
 * SourceColumn, SourceField, SourceJsonField, and catalog fields without coupling the resolver to
 * every model type.
 */
@Getter
@Setter
@NoArgsConstructor
public class PhysicalSourceField {

  private String name;
  private String description;
  private String sourceDataType;
  private String length;
  private String precision;
  private int hopType;
  private int primaryKeyPosition;
  private FieldConversionOptions parseConversion = new FieldConversionOptions();

  public PhysicalSourceField(String name) {
    this.name = name;
  }

  public FieldConversionOptions getParseConversion() {
    if (parseConversion == null) {
      parseConversion = new FieldConversionOptions();
    }
    return parseConversion;
  }

  public static PhysicalSourceField from(SourceColumn column) {
    if (column == null) {
      return null;
    }
    PhysicalSourceField field = new PhysicalSourceField(column.getName());
    field.setDescription(column.getDescription());
    field.setSourceDataType(column.getSourceDataType());
    field.setLength(column.getLength());
    field.setPrecision(column.getPrecision());
    field.setHopType(column.getHopType());
    field.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
    return field;
  }

  public static PhysicalSourceField from(SourceField sourceField) {
    if (sourceField == null) {
      return null;
    }
    PhysicalSourceField field = new PhysicalSourceField(sourceField.getName());
    field.setDescription(sourceField.getDescription());
    field.setSourceDataType(sourceField.getSourceDataType());
    field.setLength(sourceField.getLength());
    field.setPrecision(sourceField.getPrecision());
    field.setHopType(sourceField.getHopType());
    field.setPrimaryKeyPosition(sourceField.getPrimaryKeyPosition());
    SourceFieldInputOptions inputOptions = sourceField.getInputOptions();
    if (inputOptions != null) {
      FieldConversionOptions conv = field.getParseConversion();
      if (inputOptions.getConversion() != null) {
        conv.mergeFrom(inputOptions.getConversion());
      }
      if (inputOptions.getCsv() != null) {
        CsvFieldOptions csv = inputOptions.getCsv();
        if (!Utils.isEmpty(csv.getFormat()) && Utils.isEmpty(conv.getConversionMask())) {
          conv.setConversionMask(csv.getFormat());
        }
        if (!Utils.isEmpty(csv.getDecimalSymbol()) && Utils.isEmpty(conv.getDecimalSymbol())) {
          conv.setDecimalSymbol(csv.getDecimalSymbol());
        }
        if (!Utils.isEmpty(csv.getGroupingSymbol()) && Utils.isEmpty(conv.getGroupingSymbol())) {
          conv.setGroupingSymbol(csv.getGroupingSymbol());
        }
        if (!Utils.isEmpty(csv.getCurrencySymbol()) && Utils.isEmpty(conv.getCurrencySymbol())) {
          conv.setCurrencySymbol(csv.getCurrencySymbol());
        }
      }
    }
    return field;
  }

  public static PhysicalSourceField from(SourceJsonField jsonField) {
    if (jsonField == null) {
      return null;
    }
    PhysicalSourceField field = new PhysicalSourceField(jsonField.resolveName());
    field.setHopType(jsonField.getHopType());
    if (jsonField.getLength() >= 0) {
      field.setLength(Integer.toString(jsonField.getLength()));
    }
    if (jsonField.getPrecision() >= 0) {
      field.setPrecision(Integer.toString(jsonField.getPrecision()));
    }
    field.setPrimaryKeyPosition(jsonField.getPrimaryKeyPosition());
    FieldConversionOptions conv = field.getParseConversion();
    conv.setConversionMask(jsonField.getFormat());
    conv.setDecimalSymbol(jsonField.getDecimalSymbol());
    conv.setGroupingSymbol(jsonField.getGroupSymbol());
    conv.setCurrencySymbol(jsonField.getCurrencySymbol());
    if (jsonField.getTrimType() > 0) {
      conv.setTrimType(Integer.toString(jsonField.getTrimType()));
    }
    return field;
  }

  public static PhysicalSourceField from(CatalogSourceField catalogField) {
    if (catalogField == null) {
      return null;
    }
    PhysicalSourceField field = new PhysicalSourceField(catalogField.getName());
    field.setDescription(catalogField.getDescription());
    field.setSourceDataType(catalogField.getSourceDataType());
    field.setLength(catalogField.getLength());
    field.setPrecision(catalogField.getPrecision());
    field.setHopType(catalogField.getHopType());
    field.setPrimaryKeyPosition(catalogField.getPrimaryKeyPosition());
    return field;
  }

  public boolean isPrimaryKey() {
    return primaryKeyPosition > 0;
  }

  public boolean isLengthAbsent() {
    if (Utils.isEmpty(length)) {
      return true;
    }
    try {
      return Integer.parseInt(length.trim()) < 0;
    } catch (NumberFormatException e) {
      return true;
    }
  }

  public int parseLengthOr(int defaultValue) {
    if (Utils.isEmpty(length)) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(length.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public int effectiveHopType() {
    return hopType > 0 ? hopType : IValueMeta.TYPE_STRING;
  }
}
