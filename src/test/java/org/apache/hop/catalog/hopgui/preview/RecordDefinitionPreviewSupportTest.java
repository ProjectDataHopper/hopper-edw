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
package org.apache.hop.catalog.hopgui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.catalog.model.DvCsvFormatRecord;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.PhysicalFileRef;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.CsvFieldOptions;
import org.apache.hop.datavault.metadata.DvSourcePreviewInputSupport;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.SourceFieldInputOptions;
import org.apache.hop.datavault.metadata.file.DvCsvSource;
import org.apache.hop.pipeline.transforms.csvinput.CsvInputField;
import org.apache.hop.pipeline.transforms.csvinput.CsvInputMeta;
import org.apache.hop.pipeline.transforms.fileinput.text.TextFileInputMeta;
import org.junit.jupiter.api.Test;

class RecordDefinitionPreviewSupportTest {

  @Test
  void supportsPreview_falseWhenDefinitionIsNull() {
    assertFalse(RecordDefinitionPreviewSupport.supportsPreview(null));
  }

  @Test
  void supportsPreview_trueForDvSourceDatabaseWithPhysicalTable() {
    RecordDefinition definition = dvSourceDefinition("DATABASE");
    definition.setPhysicalTable(physicalTable("Vault", "public", "customers"));
    assertTrue(RecordDefinitionPreviewSupport.supportsPreview(definition));
  }

  @Test
  void supportsPreview_trueForDvSourceCsvWithFolder() {
    RecordDefinition definition = dvSourceDefinition("CSV");
    definition.setPhysicalFile(physicalFile("/data/csv", ".*\\.csv"));
    assertTrue(RecordDefinitionPreviewSupport.supportsPreview(definition));
  }

  @Test
  void supportsPreview_trueForDvSourceCsvWithSingleFilename() {
    RecordDefinition definition = dvSourceDefinition("CSV");
    DvCsvFormatRecord csvFormat = new DvCsvFormatRecord();
    csvFormat.setSingleFilename("/data/customers.csv");
    definition.getDvSource().setCsvFormat(csvFormat);
    assertTrue(RecordDefinitionPreviewSupport.supportsPreview(definition));
  }

  @Test
  void supportsPreview_trueForDvSourceParquetWithFolder() {
    RecordDefinition definition = dvSourceDefinition("PARQUET");
    definition.setPhysicalFile(physicalFile("/data/parquet", ".*\\.parquet"));
    assertTrue(RecordDefinitionPreviewSupport.supportsPreview(definition));
  }

  @Test
  void supportsPreview_trueForHubWithPhysicalTable() {
    RecordDefinition definition = typedDefinition(RecordDefinitionType.DV_HUB);
    definition.setPhysicalTable(physicalTable("Vault", "dv", "hub_customer"));
    assertTrue(RecordDefinitionPreviewSupport.supportsPreview(definition));
  }

  @Test
  void supportsPreview_falseWhenLocationMetadataMissing() {
    RecordDefinition definition = dvSourceDefinition("DATABASE");
    assertFalse(RecordDefinitionPreviewSupport.supportsPreview(definition));
  }

  @Test
  void buildCsvPreview_usesCsvInputForSingleFilename() throws Exception {
    DvCsvSource csvSource = new DvCsvSource();
    csvSource.setSingleFilename("/tmp/customers.csv");
    csvSource.setDelimiter(",");
    csvSource.setFields(java.util.List.of(new SourceField("id"), new SourceField("name")));

    DvSourcePreviewInputSupport.PreviewPipeline preview =
        DvSourcePreviewInputSupport.buildPreviewPipeline(
            dataVaultSource("customers", csvSource), csvSource, new Variables(), null, 25);

    assertTrue(
        preview.pipelineMeta().findTransform(preview.previewTransformName()).getTransform()
            instanceof CsvInputMeta);
  }

  @Test
  void buildCsvPreview_appliesConfiguredFieldFormat() throws Exception {
    SourceField amount = new SourceField("amount");
    amount.setHopType(IValueMeta.TYPE_NUMBER);
    CsvFieldOptions csv = new CsvFieldOptions();
    csv.setFormat("#,##0.00");
    csv.setDecimalSymbol(",");
    csv.setGroupingSymbol(".");
    SourceFieldInputOptions inputOptions = new SourceFieldInputOptions();
    inputOptions.setCsv(csv);
    amount.setInputOptions(inputOptions);

    DvCsvSource csvSource = new DvCsvSource();
    csvSource.setSingleFilename("/tmp/amounts.csv");
    csvSource.setDelimiter(",");
    csvSource.setFields(java.util.List.of(amount));

    DvSourcePreviewInputSupport.PreviewPipeline preview =
        DvSourcePreviewInputSupport.buildPreviewPipeline(
            dataVaultSource("amounts", csvSource), csvSource, new Variables(), null, 25);

    CsvInputMeta csvInputMeta =
        (CsvInputMeta)
            preview.pipelineMeta().findTransform(preview.previewTransformName()).getTransform();
    CsvInputField field = csvInputMeta.getInputFields().getFirst();
    assertEquals("#,##0.00", field.getFormat());
    assertEquals(",", field.getDecimalSymbol());
    assertEquals(".", field.getGroupSymbol());
  }

  @Test
  void buildCsvPreview_usesTextFileInputForFolder() throws Exception {
    DvCsvSource csvSource = new DvCsvSource();
    csvSource.setFolder("/data/csv");
    csvSource.setIncludeFileMask(".*\\.csv");
    csvSource.setFields(java.util.List.of(new SourceField("id")));

    DvSourcePreviewInputSupport.PreviewPipeline preview =
        DvSourcePreviewInputSupport.buildPreviewPipeline(
            dataVaultSource("customers", csvSource), csvSource, new Variables(), null, 25);

    assertTrue(
        preview.pipelineMeta().findTransform(preview.previewTransformName()).getTransform()
            instanceof TextFileInputMeta);
  }

  private static RecordDefinition dvSourceDefinition(String sourceType) {
    RecordDefinition definition = typedDefinition(RecordDefinitionType.DV_SOURCE);
    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setSourceType(sourceType);
    definition.setDvSource(dvSource);
    return definition;
  }

  private static RecordDefinition typedDefinition(RecordDefinitionType type) {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("ns", "name"));
    definition.setType(type);
    return definition;
  }

  private static PhysicalTableRef physicalTable(
      String databaseMetaName, String schemaName, String tableName) {
    PhysicalTableRef physicalTable = new PhysicalTableRef();
    physicalTable.setDatabaseMetaName(databaseMetaName);
    physicalTable.setSchemaName(schemaName);
    physicalTable.setTableName(tableName);
    return physicalTable;
  }

  private static PhysicalFileRef physicalFile(String folder, String includeMask) {
    PhysicalFileRef physicalFile = new PhysicalFileRef();
    physicalFile.setFolder(folder);
    physicalFile.setIncludeFileMask(includeMask);
    return physicalFile;
  }

  private static org.apache.hop.datavault.metadata.DataVaultSource dataVaultSource(
      String name, org.apache.hop.datavault.metadata.IDvSource source) {
    org.apache.hop.datavault.metadata.DataVaultSource recordSource =
        new org.apache.hop.datavault.metadata.DataVaultSource();
    recordSource.setName(name);
    recordSource.setSource(source);
    return recordSource;
  }
}
