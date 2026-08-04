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
package org.apache.hop.catalog.transform.recordoutput;

import java.util.Locale;
import java.util.Objects;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.CsvFieldOptions;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.SourceFieldInputOptions;
import org.apache.hop.i18n.BaseMessages;

/** Pure helpers for mapping stream rows to catalog {@link SourceField} definitions. */
public final class RecordDefinitionOutputFieldSupport {

  private static final Class<?> PKG = RecordDefinitionOutputMeta.class;

  private RecordDefinitionOutputFieldSupport() {}

  /**
   * Builds a {@link SourceField} from one input row using the configured column indexes. Negative
   * indexes mean "mapping not configured" and that attribute is left unset.
   */
  public static SourceField sourceFieldFromRow(
      IRowMeta rowMeta,
      Object[] row,
      int nameIndex,
      int typeIndex,
      int lengthIndex,
      int precisionIndex,
      int primaryKeyPositionIndex,
      int formatIndex,
      int decimalIndex,
      int groupingSymbolIndex)
      throws HopException {
    if (rowMeta == null || row == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionOutput.Error.MissingFieldRow"));
    }
    if (nameIndex < 0) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionOutput.Error.MissingFieldNameMapping"));
    }

    String name = stringAt(rowMeta, row, nameIndex);
    if (Utils.isEmpty(name)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionOutput.Error.EmptyFieldName"));
    }

    SourceField field = new SourceField(name.trim());

    String typeDesc = stringAt(rowMeta, row, typeIndex);
    if (Utils.isEmpty(typeDesc)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionOutput.Error.EmptyFieldType", name));
    }
    int hopType = ValueMetaFactory.getIdForValueMeta(typeDesc.trim());
    if (hopType == IValueMeta.TYPE_NONE) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionOutput.Error.UnknownFieldType", typeDesc, name));
    }
    field.setSourceDataType(typeDesc.trim());
    field.setHopType(hopType);

    String length = stringAt(rowMeta, row, lengthIndex);
    if (!Utils.isEmpty(length)) {
      field.setLength(length.trim());
    }

    String precision = stringAt(rowMeta, row, precisionIndex);
    if (!Utils.isEmpty(precision)) {
      field.setPrecision(precision.trim());
    }

    String pkText = stringAt(rowMeta, row, primaryKeyPositionIndex);
    if (!Utils.isEmpty(pkText)) {
      field.setPrimaryKeyPosition(Const.toInt(pkText.trim(), 0));
    }

    String format = stringAt(rowMeta, row, formatIndex);
    String decimal = stringAt(rowMeta, row, decimalIndex);
    String grouping = stringAt(rowMeta, row, groupingSymbolIndex);
    if (!Utils.isEmpty(format) || !Utils.isEmpty(decimal) || !Utils.isEmpty(grouping)) {
      CsvFieldOptions csv = new CsvFieldOptions();
      if (!Utils.isEmpty(format)) {
        csv.setFormat(format.trim());
      }
      if (!Utils.isEmpty(decimal)) {
        csv.setDecimalSymbol(decimal.trim());
      }
      if (!Utils.isEmpty(grouping)) {
        csv.setGroupingSymbol(grouping.trim());
      }
      SourceFieldInputOptions inputOptions = new SourceFieldInputOptions();
      inputOptions.setCsv(csv);
      field.setInputOptions(inputOptions);
    }

    return field;
  }

  /** Returns true when the grouping key changed between consecutive field rows. */
  public static boolean groupChanged(String previousGroupValue, String currentGroupValue) {
    if (previousGroupValue == null) {
      return false;
    }
    return !Objects.equals(previousGroupValue, currentGroupValue);
  }

  public static String groupingValue(IRowMeta rowMeta, Object[] row, int groupingFieldIndex)
      throws HopException {
    if (groupingFieldIndex < 0) {
      return "";
    }
    return Const.NVL(stringAt(rowMeta, row, groupingFieldIndex), "");
  }

  /**
   * Resolves delivery type from an optional stream value (code or description), falling back to the
   * fixed meta value (default {@link DvSourceDeliveryType#CHANGES_ONLY}).
   */
  public static DvSourceDeliveryType resolveDeliveryType(
      String streamValue, DvSourceDeliveryType fallback) {
    if (Utils.isEmpty(streamValue)) {
      return fallback != null ? fallback : DvSourceDeliveryType.CHANGES_ONLY;
    }
    String trimmed = streamValue.trim();
    for (DvSourceDeliveryType type : DvSourceDeliveryType.values()) {
      if (type.getCode().equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed)) {
        return type;
      }
      if (type.getDescription() != null && type.getDescription().equalsIgnoreCase(trimmed)) {
        return type;
      }
    }
    // Last resort: case-insensitive contains match on description for localized labels
    String upper = trimmed.toUpperCase(Locale.ROOT);
    for (DvSourceDeliveryType type : DvSourceDeliveryType.values()) {
      if (type.getDescription() != null
          && type.getDescription().toUpperCase(Locale.ROOT).equals(upper)) {
        return type;
      }
    }
    return fallback != null ? fallback : DvSourceDeliveryType.CHANGES_ONLY;
  }

  private static String stringAt(IRowMeta rowMeta, Object[] row, int index) throws HopException {
    if (index < 0 || index >= rowMeta.size()) {
      return null;
    }
    return rowMeta.getString(row, index);
  }
}
