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
package org.hopper.edw.datavault.metadata.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.file.TextFileInputField;
import org.apache.hop.core.row.IValueMeta;
import org.hopper.edw.datavault.metadata.CsvFieldOptions;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.SourceFieldInputOptions;
import org.apache.hop.pipeline.transforms.csvinput.CsvInputField;
import org.junit.jupiter.api.Test;

class DvTextFileInputFieldSupportTest {

  @Test
  void appliesConfiguredCsvOptionsToCsvInputField() throws Exception {
    SourceField sourceField = csvSourceField("dd/MM/yyyy", ",", ".");

    CsvInputField field = new CsvInputField("created");
    DvTextFileInputFieldSupport.applySourceField(field, sourceField);

    assertEquals("dd/MM/yyyy", field.getFormat());
    assertEquals(",", field.getDecimalSymbol());
    assertEquals(".", field.getGroupSymbol());
  }

  @Test
  void appliesDefaultTimestampFormatWhenCsvFormatMissing() throws Exception {
    SourceField sourceField = new SourceField("created");
    sourceField.setHopType(IValueMeta.TYPE_TIMESTAMP);

    TextFileInputField field = new TextFileInputField("created");
    DvTextFileInputFieldSupport.applySourceField(field, sourceField);

    assertEquals(DvTextFileInputFieldSupport.DEFAULT_TIMESTAMP_FORMAT, field.getFormat());
  }

  private static SourceField csvSourceField(String format, String decimal, String grouping) {
    SourceField sourceField = new SourceField("created");
    sourceField.setHopType(IValueMeta.TYPE_DATE);
    CsvFieldOptions csv = new CsvFieldOptions();
    csv.setFormat(format);
    csv.setDecimalSymbol(decimal);
    csv.setGroupingSymbol(grouping);
    SourceFieldInputOptions inputOptions = new SourceFieldInputOptions();
    inputOptions.setCsv(csv);
    sourceField.setInputOptions(inputOptions);
    return sourceField;
  }
}
