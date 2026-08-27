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
package org.hopper.edw.catalog.impl.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.catalog.model.CatalogCsvFieldOptions;
import org.hopper.edw.catalog.model.CatalogSourceField;
import org.hopper.edw.catalog.model.CatalogSourceFieldInputOptions;
import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.datavault.catalog.DvSourceCatalogMapper;
import org.hopper.edw.datavault.metadata.CsvFieldOptions;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.SourceFieldInputOptions;
import org.hopper.edw.datavault.metadata.file.DvCsvSource;
import org.hopper.edw.datavault.metadata.file.DvCsvSourceImportSupport;
import org.junit.jupiter.api.Test;

class RecordDefinitionDocumentCsvOptionsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void jsonRoundTrip_preservesCsvFieldInputOptions() throws Exception {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("ns", "customer"));
    definition.setType(RecordDefinitionType.DV_SOURCE);

    CatalogSourceField field = new CatalogSourceField();
    field.setName("LOAD_DATE");
    field.setHopType(3);
    CatalogCsvFieldOptions csv = new CatalogCsvFieldOptions();
    csv.setFormat("yyyy-MM-dd");
    csv.setDecimalSymbol(".");
    csv.setGroupingSymbol(",");
    CatalogSourceFieldInputOptions inputOptions = new CatalogSourceFieldInputOptions();
    inputOptions.setCsv(csv);
    field.setInputOptions(inputOptions);

    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setSourceType("CSV");
    dvSource.setFields(List.of(field));
    definition.setDvSource(dvSource);

    RecordDefinitionDocument doc = RecordDefinitionDocument.from(definition);
    String json = MAPPER.writeValueAsString(doc);
    assertTrue(json.contains("inputOptions"), json);
    assertTrue(json.contains("yyyy-MM-dd"), json);

    RecordDefinitionDocument restored = MAPPER.readValue(json, RecordDefinitionDocument.class);
    RecordDefinition roundTrip = restored.toRecordDefinition();
    CatalogSourceField restoredField = roundTrip.getDvSource().getFields().getFirst();
    assertNotNull(restoredField.getInputOptions());
    assertNotNull(restoredField.getInputOptions().getCsv());
    assertEquals("yyyy-MM-dd", restoredField.getInputOptions().getCsv().getFormat());
    assertEquals(".", restoredField.getInputOptions().getCsv().getDecimalSymbol());
    assertEquals(",", restoredField.getInputOptions().getCsv().getGroupingSymbol());
  }

  @Test
  void catalogImportMapping_persistsCsvInputOptionsInJson() throws Exception {
    SourceField loadDate = new SourceField("load_date");
    loadDate.setSourceDataType("Date");
    loadDate.setHopType(3);
    CsvFieldOptions csv = new CsvFieldOptions();
    csv.setFormat("yyyy-MM-dd");
    csv.setDecimalSymbol(".");
    csv.setGroupingSymbol(",");
    SourceFieldInputOptions inputOptions = new SourceFieldInputOptions();
    inputOptions.setCsv(csv);
    loadDate.setInputOptions(inputOptions);

    DvCsvSource csvSource = new DvCsvSource();
    csvSource.setFolder("/data");
    csvSource.setIncludeFileMask("customer\\.csv");
    DataVaultSource source =
        DvCsvSourceImportSupport.createDataVaultSource("customer", csvSource, List.of(loadDate));

    RecordDefinition definition =
        DvSourceCatalogMapper.toRecordDefinition(
            source, "hop/project/sources", new Variables(), null);

    RecordDefinitionDocument doc = RecordDefinitionDocument.from(definition);
    String json = MAPPER.writeValueAsString(doc);
    assertNotNull(definition.getDvSource().getFields().getFirst().getInputOptions());
    assertEquals(
        "yyyy-MM-dd",
        definition.getDvSource().getFields().getFirst().getInputOptions().getCsv().getFormat());
    assertTrue(json.contains("inputOptions"), json);
    assertTrue(json.contains("yyyy-MM-dd"), json);
  }
}
