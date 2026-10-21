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
package org.hopper.edw.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.hopper.edw.datavault.catalog.DvSourceFieldSupport;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingMeta;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingRule;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipeline;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelContractValidationSupportTest {

  private static final String CATALOG = "local-catalog";

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @Test
  void blankSatelliteLengthUsesPipelineDataTypeMapping(@TempDir Path tempDir) throws Exception {
    Variables variables = new Variables();
    MemoryMetadataProvider metadata = metadata(tempDir, true);
    String hsm = writePipelineModel(tempDir, true);
    String hdv = writeVault(tempDir);

    publishContract(
        hsm,
        variables,
        metadata,
        field("carrier_code", "2000"),
        field("package_id", "7"),
        field("wide_note", "4000"),
        field("pinned", "2000"),
        field("explicit_wide", "3000"));

    List<ValidationIssue> issues =
        validate(
            hdv,
            variables,
            metadata,
            "carrier_code",
            "package_id",
            "wide_note",
            "pinned",
            "explicit_wide");

    assertTrue(issues.stream().noneMatch(issue -> "carrier_code".equals(issue.fieldName())));
    assertTrue(issues.stream().noneMatch(issue -> "package_id".equals(issue.fieldName())));
    assertTrue(issues.stream().noneMatch(issue -> "explicit_wide".equals(issue.fieldName())));
    assertEquals(2, issues.size(), () -> issues.toString());
    assertTrue(message(issues, "wide_note").contains("has length 2000"));
    assertTrue(message(issues, "wide_note").contains("4000"));
    assertTrue(message(issues, "pinned").contains("has length 10"));
    assertTrue(message(issues, "pinned").contains("2000"));
    assertTrue(
        issues.stream().allMatch(issue -> issue.kind() == IssueKind.MODEL_ATTRIBUTE_NARROWER));
  }

  @Test
  void blankSatelliteLengthWithoutMappingStaysNarrower(@TempDir Path tempDir) throws Exception {
    Variables variables = new Variables();
    MemoryMetadataProvider metadata = metadata(tempDir, false);
    String hsm = writePipelineModel(tempDir, false);
    String hdv =
        writeVault(
            tempDir,
            attribute("carrier_code", null),
            attribute("package_id", null));

    publishContract(hsm, variables, metadata, field("carrier_code", "2000"), field("package_id", "7"));

    List<ValidationIssue> issues = validate(hdv, variables, metadata, "carrier_code", "package_id");

    assertEquals(1, issues.size(), () -> issues.toString());
    assertEquals("carrier_code", issues.getFirst().fieldName());
    assertTrue(issues.getFirst().message().contains("has length ?"));
    assertTrue(issues.getFirst().message().contains("2000"));
  }

  private static List<ValidationIssue> validate(
      String hdv,
      Variables variables,
      MemoryMetadataProvider metadata,
      String... fields)
      throws Exception {
    RecordDefinitionKey key = new RecordDefinitionKey("hop/example/sources", "asn-package-lines");
    SourceUsage usage =
        SourceUsage.builder()
            .modelType(SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT)
            .modelName("retail-360")
            .modelFilename(hdv)
            .modelElementName("sat_package_line")
            .catalogConnection(CATALOG)
            .mappedFields(List.of(fields))
            .build();
    ValidationReport report = new ValidationReport("retail-sources");
    report.addRecordValidation(
        new ValidationReport.RecordDefinitionValidation(
            key, CATALOG, "PIPELINE", true, null, List.of(usage), List.of()));
    ValidationModels models =
        new ValidationModels(
            new ResourceDefinitionGroupMeta("retail-sources"), List.of(), List.of(), List.of());
    ValidationReport enriched =
        ModelContractValidationSupport.enrich(report, models, null, variables, metadata);
    return enriched.getRecordValidations().getFirst().issues();
  }

  private static void publishContract(
      String hsm,
      Variables variables,
      MemoryMetadataProvider metadata,
      SourceField... fields)
      throws Exception {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/example/sources", "asn-package-lines"));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setSourceType("PIPELINE");
    dvSource.setPipelineSourceModelFilename(hsm);
    dvSource.setPipelineSourceName("asn-package-lines");
    definition.setDvSource(dvSource);
    DvSourceFieldSupport.applyLayoutToDefinition(definition, List.of(fields), variables);
    RecordDefinitionRegistry.getInstance().upsert(CATALOG, definition, variables, metadata);
  }

  private static MemoryMetadataProvider metadata(Path tempDir, boolean withProfile) throws Exception {
    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    if (withProfile) {
      metadata.getSerializer(DataTypeMappingMeta.class).save(stringDefaultLengthProfile());
    }
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName(CATALOG);
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(tempDir.resolve("catalog").toString().replace('\\', '/'));
    catalog.setCatalog(fileCatalog);
    metadata.getSerializer(DataCatalogMeta.class).save(catalog);
    RecordDefinitionRegistry.getInstance().invalidate();
    return metadata;
  }

  private static String writePipelineModel(Path tempDir, boolean withMapping) throws Exception {
    SourcePipeline pipeline = new SourcePipeline("asn-package-lines");
    pipeline.setPipelineFilename("${PROJECT_HOME}/pipelines/parse-asn-xml.hpl");
    pipeline.setOutputTransformName("ASN lines");
    if (withMapping) {
      pipeline.getDataTypeMappingNames().add("premodel-defaults");
    }
    pipeline.getFields().add(column("carrier_code", null));
    pipeline.getFields().add(column("package_id", "7"));
    pipeline.getFields().add(column("wide_note", null));
    pipeline.getFields().add(column("pinned", null));
    pipeline.getFields().add(column("explicit_wide", null));

    SourceModel model = new SourceModel();
    model.setName("source-tables-crm");
    model.getPipelineSources().add(pipeline);
    Path hsm = tempDir.resolve("source-tables-crm.hsm");
    ModelXmlWriteSupport.writeModelXml(
        SourceModel.XML_TAG, model, hsm.toAbsolutePath().toString(), new Variables());
    return hsm.toAbsolutePath().toString().replace('\\', '/');
  }

  private static String writeVault(Path tempDir) throws Exception {
    return writeVault(
        tempDir,
        attribute("carrier_code", null),
        attribute("package_id", null),
        attribute("wide_note", null),
        attribute("pinned", "10"),
        attribute("explicit_wide", "5000"));
  }

  private static String writeVault(Path tempDir, SatelliteAttribute... attributes) throws Exception {
    DvSatellite satellite = new DvSatellite();
    satellite.setName("sat_package_line");
    satellite.setRecordSourceName("asn-package-lines");
    for (SatelliteAttribute attribute : attributes) {
      satellite.getAttributes().add(attribute);
    }
    DataVaultModel model = new DataVaultModel();
    model.setName("retail-360");
    model.getTables().add(satellite);
    Path hdv = tempDir.resolve("retail-360.hdv");
    ModelXmlWriteSupport.writeModelXml(
        HopVaultFileType.XML_TAG, model, hdv.toAbsolutePath().toString(), new Variables());
    return hdv.toAbsolutePath().toString().replace('\\', '/');
  }

  private static SourceColumn column(String name, String length) {
    SourceColumn column = new SourceColumn(name);
    column.setHopType(IValueMeta.TYPE_STRING);
    column.setLength(length);
    return column;
  }

  private static SourceField field(String name, String length) {
    SourceField field = new SourceField(name);
    field.setHopType(IValueMeta.TYPE_STRING);
    field.setLength(length);
    return field;
  }

  private static SatelliteAttribute attribute(String name, String length) {
    SatelliteAttribute attribute = new SatelliteAttribute(name);
    attribute.setDataType("String");
    attribute.setLength(length);
    return attribute;
  }

  private static DataTypeMappingMeta stringDefaultLengthProfile() {
    DataTypeMappingRule rule = new DataTypeMappingRule();
    rule.setId("string-default-length");
    rule.setName("String without length");
    rule.setEnabled(true);
    rule.setMatchHopType("String");
    rule.setMatchLengthAbsent(true);
    rule.setTargetHopType(IValueMeta.TYPE_STRING);
    rule.setTargetLength("2000");
    DataTypeMappingMeta profile = new DataTypeMappingMeta("premodel-defaults");
    profile.getRules().add(rule);
    return profile;
  }

  private static String message(List<ValidationIssue> issues, String fieldName) {
    return issues.stream()
        .filter(issue -> fieldName.equals(issue.fieldName()))
        .map(ValidationIssue::message)
        .findFirst()
        .orElse("");
  }
}
