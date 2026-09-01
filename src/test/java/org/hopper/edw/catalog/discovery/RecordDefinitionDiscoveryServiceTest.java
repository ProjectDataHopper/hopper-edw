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
package org.hopper.edw.catalog.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.catalog.model.DvSourceRecord;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.datavault.catalog.DvSourceFieldSupport;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingMeta;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingRule;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipeline;
import org.hopper.edw.datavault.metadata.sourcemodel.publish.SourcePipelineCatalogPublisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordDefinitionDiscoveryServiceTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void pipelineRediscoveryAppliesAttachedStringDefaultLength(@TempDir Path tempDir)
      throws Exception {
    MemoryMetadataProvider metadata = metadataWithStringDefault();
    String hsmPath = writePipelineHsm(tempDir);
    Variables variables = new Variables();

    RecordDefinitionDiscoveryService.DiscoveryResult discovery =
        RecordDefinitionDiscoveryService.discover(
            DvSourceType.PIPELINE, pipelineRef(hsmPath), variables, metadata);

    assertEquals(1, discovery.fields().size());
    assertEquals("asn_id", discovery.fields().get(0).getName());
    assertEquals("2000", discovery.fields().get(0).getLength());

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(
            DvSourceFieldSupport.sourceFieldsFromDefinition(catalogContract(hsmPath, metadata)),
            discovery.fields());
    assertTrue(diff.isInSync(), () -> RecordDefinitionSchemaDiffSupport.formatDiff(diff));
  }

  @Test
  void pipelineRediscoveryWithoutMetadataProviderDiffersFromCatalogContract(@TempDir Path tempDir)
      throws Exception {
    MemoryMetadataProvider metadata = metadataWithStringDefault();
    String hsmPath = writePipelineHsm(tempDir);

    RecordDefinitionDiscoveryService.DiscoveryResult withoutProfiles =
        RecordDefinitionDiscoveryService.discover(
            DvSourceType.PIPELINE, pipelineRef(hsmPath), new Variables(), null);

    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(
            DvSourceFieldSupport.sourceFieldsFromDefinition(catalogContract(hsmPath, metadata)),
            withoutProfiles.fields());
    assertTrue(diff.hasChanges());
    assertEquals(
        RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED, diff.changes().get(0).kind());
    assertEquals("asn_id", diff.changes().get(0).fieldName());
  }

  private static MemoryMetadataProvider metadataWithStringDefault() throws Exception {
    MemoryMetadataProvider metadata = new MemoryMetadataProvider();
    metadata.getSerializer(DataTypeMappingMeta.class).save(stringDefaultLengthProfile());
    return metadata;
  }

  private static String writePipelineHsm(Path tempDir) throws Exception {
    SourcePipeline pipeline = new SourcePipeline("asn-package-lines");
    pipeline.setPipelineFilename("${PROJECT_HOME}/pipelines/parse-asn-xml.hpl");
    pipeline.setOutputTransformName("ASN lines");
    pipeline.getDataTypeMappingNames().add("premodel-defaults");
    SourceColumn asnId = new SourceColumn("asn_id");
    asnId.setHopType(IValueMeta.TYPE_STRING);
    pipeline.getFields().add(asnId);

    SourceModel model = new SourceModel();
    model.setName("source-tables-crm");
    model.getPipelineSources().add(pipeline);

    Path hsm = tempDir.resolve("source-tables-crm.hsm");
    ModelXmlWriteSupport.writeModelXml(
        SourceModel.XML_TAG, model, hsm.toAbsolutePath().toString(), new Variables());
    return hsm.toAbsolutePath().toString();
  }

  private static PhysicalSourceRef pipelineRef(String hsmPath) {
    return PhysicalSourceRef.builder()
        .pipelineSourceModelFilename(hsmPath)
        .pipelineSourceName("asn-package-lines")
        .pipelineFilename("${PROJECT_HOME}/pipelines/parse-asn-xml.hpl")
        .pipelineTransformName("ASN lines")
        .build();
  }

  private static RecordDefinition catalogContract(String hsmPath, MemoryMetadataProvider metadata)
      throws Exception {
    SourceModel loaded = SourceModelLoadSupport.load(hsmPath, new Variables(), metadata);
    SourcePipeline pipelineSource = loaded.findPipelineSource("asn-package-lines");
    List<SourceField> published =
        SourcePipelineCatalogPublisher.buildFieldsFromProjection(pipelineSource, metadata);
    assertEquals("2000", published.get(0).getLength());

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/sources", "asn-package-lines"));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setSourceType("PIPELINE");
    dvSource.setPipelineSourceModelFilename(hsmPath);
    dvSource.setPipelineSourceName("asn-package-lines");
    definition.setDvSource(dvSource);
    DvSourceFieldSupport.applyLayoutToDefinition(definition, published, null);
    return definition;
  }

  private static DataTypeMappingMeta stringDefaultLengthProfile() {
    DataTypeMappingRule rule = new DataTypeMappingRule();
    rule.setId("string-default-length");
    rule.setMatchHopType("String");
    rule.setMatchLengthAbsent(true);
    rule.setTargetHopType(IValueMeta.TYPE_STRING);
    rule.setTargetLength("2000");
    DataTypeMappingMeta profile = new DataTypeMappingMeta("premodel-defaults");
    profile.getRules().add(rule);
    return profile;
  }
}
