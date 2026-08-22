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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.TextFileInputField;
import org.apache.hop.core.gui.ITextFileInputField;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.CsvFieldOptions;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.pipeline.transforms.csvinput.CsvInputField;

/** Applies {@link SourceField} typing and CSV parsing options to Hop text-file input fields. */
public final class DvTextFileInputFieldSupport {

  public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
  public static final String DEFAULT_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";

  private DvTextFileInputFieldSupport() {}

  public static void applySourceField(CsvInputField field, SourceField sourceField)
      throws HopException {
    applyHopType(field, sourceField, true);
    applyFormatAndSymbols(field, sourceField);
  }

  public static void applySourceField(TextFileInputField field, SourceField sourceField)
      throws HopException {
    applyHopType(field, sourceField, false);
    applyFormatAndSymbols(field, sourceField);
  }

  private static void applyHopType(
      ITextFileInputField field, SourceField sourceField, boolean csvInput) throws HopException {
    if (sourceField.getHopType() > 0) {
      field.setType(sourceField.getHopType());
    } else if (csvInput && field instanceof CsvInputField csvInputField) {
      csvInputField.setTypeWithString(
          !Utils.isEmpty(sourceField.getSourceDataType())
              ? sourceField.getSourceDataType()
              : "String");
    } else if (!csvInput && field instanceof TextFileInputField textFileInputField) {
      textFileInputField.setTypeWithString(
          !Utils.isEmpty(sourceField.getSourceDataType())
              ? sourceField.getSourceDataType()
              : "String");
    } else {
      field.setType(IValueMeta.TYPE_STRING);
    }
    if (!Utils.isEmpty(sourceField.getLength())) {
      field.setLength(Integer.parseInt(sourceField.getLength()));
    }
    if (!Utils.isEmpty(sourceField.getPrecision())) {
      field.setPrecision(Integer.parseInt(sourceField.getPrecision()));
    }
  }

  private static void applyFormatAndSymbols(ITextFileInputField field, SourceField sourceField) {
    CsvFieldOptions csv = csvOptions(sourceField);
    String format = csv != null ? csv.getFormat() : null;
    if (!Utils.isEmpty(format)) {
      field.setFormat(format);
    } else {
      int hopType = sourceField.getHopType();
      if (hopType == IValueMeta.TYPE_DATE) {
        field.setFormat(DEFAULT_DATE_FORMAT);
      } else if (hopType == IValueMeta.TYPE_TIMESTAMP) {
        field.setFormat(DEFAULT_TIMESTAMP_FORMAT);
      }
    }
    if (csv == null) {
      return;
    }
    if (!Utils.isEmpty(csv.getDecimalSymbol())) {
      field.setDecimalSymbol(csv.getDecimalSymbol());
    }
    if (!Utils.isEmpty(csv.getGroupingSymbol())) {
      field.setGroupSymbol(csv.getGroupingSymbol());
    }
    if (!Utils.isEmpty(csv.getCurrencySymbol())) {
      field.setCurrencySymbol(csv.getCurrencySymbol());
    }
  }

  private static CsvFieldOptions csvOptions(SourceField sourceField) {
    if (sourceField == null || sourceField.getInputOptions() == null) {
      return null;
    }
    return sourceField.getInputOptions().getCsv();
  }
}
