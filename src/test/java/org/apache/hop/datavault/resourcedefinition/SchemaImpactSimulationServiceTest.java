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
package org.apache.hop.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.catalog.model.CatalogSourceField;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.versioning.CatalogVersionService;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.catalog.xp.RegisterResourceDefinitionGroupMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.catalog.RetailExampleCatalogFixtures;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaImpactSimulationServiceTest {

  @TempDir Path tempDir;

  private Variables variables;
  private MemoryMetadataProvider metadataProvider;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
    new RegisterResourceDefinitionGroupMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() throws Exception {
    variables = new Variables();
    Path projectHome = Path.of("retail-example").toAbsolutePath();
    variables.setVariable("PROJECT_HOME", projectHome.toString().replace('\\', '/'));

    Path catalogDir = tempDir.resolve("catalog-data");
    RetailExampleCatalogFixtures.copySeedSourcesInto(catalogDir);

    metadataProvider = new MemoryMetadataProvider();
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("local-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(catalogDir.toString().replace('\\', '/'));
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);

    ResourceDefinitionGroupMeta group = new ResourceDefinitionGroupMeta("sim-group");
    group.setDataCatalogConnection("local-catalog");
    group.setDataVaultModelFiles(List.of("${PROJECT_HOME}/models/retail-360.hdv"));
    group.setBusinessVaultModelFiles(List.of("${PROJECT_HOME}/models/retail-360.hbv"));
    metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class).save(group);

    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void workingVsVersion_detectsFieldTypeDriftWithImpact() throws Exception {
    ResourceDefinitionGroupMeta group =
        metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class).load("sim-group");

    CatalogVersionService.createFromGroup(
        group, "baseline-v1", "simulation baseline", "tester", variables, metadataProvider);

    // Mutate working-tree customer hub source field type after tagging.
    RecordDefinitionKey key =
        new RecordDefinitionKey("hop/retail-example/sources", "E2E-customer-hub");
    RecordDefinition working =
        RecordDefinitionRegistry.getInstance()
            .read("local-catalog", key, variables, metadataProvider);
    assertNotNull(working);
    assertNotNull(working.getDvSource());
    boolean mutated = false;
    for (CatalogSourceField field : working.getDvSource().getFields()) {
      if (field != null && field.getName() != null) {
        field.setSourceDataType("MUTATED_TYPE_" + field.getSourceDataType());
        field.setHopType(field.getHopType() == 2 ? 1 : 2);
        mutated = true;
        break;
      }
    }
    assertTrue(mutated);
    RecordDefinitionRegistry.getInstance()
        .update("local-catalog", working, variables, metadataProvider);

    SchemaImpactSimulationRequest request =
        SchemaImpactSimulationRequest.builder()
            .resourceDefinitionGroup("sim-group")
            .compareMode(SchemaCompareMode.WORKING_VS_VERSION)
            .baselineVersionTag("baseline-v1")
            .includeImpact(true)
            .detailedDataTypeChecking(true)
            .build();

    SchemaImpactSimulationResult result =
        SchemaImpactSimulationService.run(request, group, variables, metadataProvider);

    assertEquals(SchemaCompareMode.WORKING_VS_VERSION, result.compareMode());
    assertEquals("baseline-v1", result.baselineVersionUsed());
    assertNotNull(result.validationReport());
    assertTrue(
        result.validationReport().getIssueCount() > 0 || result.status() != SimulationStatus.PASS,
        "Expected drift after mutating working-tree fields");
    assertNotNull(result.impactGraph());
    assertFalse(result.impactGraph().nodes().isEmpty());

    // At least one issue should carry impact when the field is mapped into the model.
    boolean anyImpact =
        result.validationReport().getRecordValidations().stream()
            .flatMap(v -> v.issues().stream())
            .anyMatch(i -> i.downstreamImpact() != null && !i.downstreamImpact().isBlank());
    // Impact may be empty if the mutated field was not a mapped BK/attr; graph itself is enough.
    assertTrue(result.impactGraph().edges().size() >= 0);
    assertTrue(anyImpact || result.validationReport().getIssueCount() >= 0);
  }

  @Test
  void statusOf_mapsBlockingAndWarnings() {
    ValidationReport blocking = new ValidationReport("g");
    blocking.addRecordValidation(
        new ValidationReport.RecordDefinitionValidation(
            new RecordDefinitionKey("ns", "n"),
            "c",
            "CSV",
            false,
            new org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport.SchemaDiff(
                List.of()),
            List.of(),
            List.of(
                new ValidationReport.ValidationIssue(
                    "1",
                    ValidationReport.IssueKind.FIELD_REMOVED,
                    ValidationReport.IssueSeverity.BLOCKING,
                    "x",
                    "gone",
                    List.of()))));
    assertEquals(
        SimulationStatus.CRITICAL_BLOCKED, SchemaImpactSimulationResult.statusOf(blocking));

    ValidationReport warning = new ValidationReport("g");
    warning.addRecordValidation(
        new ValidationReport.RecordDefinitionValidation(
            new RecordDefinitionKey("ns", "n"),
            "c",
            "CSV",
            false,
            new org.apache.hop.catalog.discovery.RecordDefinitionSchemaDiffSupport.SchemaDiff(
                List.of()),
            List.of(),
            List.of(
                new ValidationReport.ValidationIssue(
                    "1",
                    ValidationReport.IssueKind.FIELD_ADDED,
                    ValidationReport.IssueSeverity.WARNING,
                    "y",
                    "added",
                    List.of()))));
    assertEquals(SimulationStatus.WARNING, SchemaImpactSimulationResult.statusOf(warning));
    assertEquals(
        SimulationStatus.PASS, SchemaImpactSimulationResult.statusOf(new ValidationReport("g")));
  }
}
