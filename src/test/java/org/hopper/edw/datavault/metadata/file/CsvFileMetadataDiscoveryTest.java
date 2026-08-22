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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.hopper.edw.datavault.metadata.SourceField;
import org.junit.jupiter.api.Test;

class CsvFileMetadataDiscoveryTest {

  @Test
  void parseRows_readsMaskDecimalAndGroupingUsingRowMetaColumnNames() throws Exception {
    IRowMeta rowMeta = fileMetadataRowMeta();
    Object[] row = new Object[rowMeta.size()];
    row[rowMeta.indexOfValue("charset")] = StandardCharsets.UTF_8;
    row[rowMeta.indexOfValue("delimiter")] = ',';
    row[rowMeta.indexOfValue("enclosure")] = "\"";
    row[rowMeta.indexOfValue("skip_header_lines")] = 0L;
    row[rowMeta.indexOfValue("header_line_present")] = Boolean.TRUE;
    row[rowMeta.indexOfValue("name")] = "amount";
    row[rowMeta.indexOfValue("type")] = "Number";
    row[rowMeta.indexOfValue("length")] = 10L;
    row[rowMeta.indexOfValue("precision")] = 2L;
    row[rowMeta.indexOfValue("mask")] = "#,##0.00";
    row[rowMeta.indexOfValue("decimal_symbol")] = ",";
    row[rowMeta.indexOfValue("grouping_symbol")] = ".";

    List<Object[]> rows = new ArrayList<>();
    rows.add(row);
    CsvFileMetadataDiscovery.DiscoveryResult result =
        CsvFileMetadataDiscovery.parseRowsForTests(rowMeta, rows);

    assertEquals(1, result.fields().size());
    SourceField field = result.fields().getFirst();
    assertNotNull(field.getInputOptions());
    assertNotNull(field.getInputOptions().getCsv());
    assertEquals("#,##0.00", field.getInputOptions().getCsv().getFormat());
    assertEquals(",", field.getInputOptions().getCsv().getDecimalSymbol());
    assertEquals(".", field.getInputOptions().getCsv().getGroupingSymbol());
  }

  private static IRowMeta fileMetadataRowMeta() throws Exception {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("charset", 2, -1, -1));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("delimiter", 2, -1, -1));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("enclosure", 2, -1, -1));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("field_count", 5, -1, 0));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("skip_header_lines", 5, -1, 0));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("skip_footer_lines", 5, -1, 0));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("header_line_present", 4, -1, -1));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("name", 2, -1, -1));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("type", 2, -1, -1));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("length", 5, -1, 0));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("precision", 5, -1, 0));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("mask", 2, -1, -1));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("decimal_symbol", 2, -1, -1));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("grouping_symbol", 2, -1, -1));
    return rowMeta;
  }
}
