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
package org.hopper.edw.datavault.opsanalytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.busmatrix.BusinessProcessCatalogMeta;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.hopper.edw.datavault.metadata.dimensional.DmTableType;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionScdType;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceType;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticSelection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OpsAnalyticsDimensionalBuilderTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void shapesTheAnalyticsStarAndSelections() throws Exception {
    DataVaultModel vault =
        OpsAnalyticsVaultBuilder.apply(
            OpsAnalyticsSourceModelBuilder.build(),
            new Variables(),
            new MemoryMetadataProvider());
    DimensionalModel model = OpsAnalyticsDimensionalBuilder.shape(vault);

    assertFact(model, "f_pipeline_load");
    assertFact(model, "f_transform_load");
    assertFact(model, "f_load_insight");
    assertFact(model, "f_workflow_overview");
    assertFact(model, "f_workflow_model");
    assertFact(model, "f_quality_run");
    assertFact(model, "f_quality_finding");
    assertFact(model, "f_quality_subject");
    assertFact(model, "f_quality_field");
    assertFact(model, "f_harvest_run");
    assertFact(model, "f_harvest_subject");
    assertFact(model, "f_harvest_change");
    assertNull(findByTable(model, "d_load_run"), () -> names(model));

    DmDimension pipelineDimension = (DmDimension) model.findTable("dim_pipeline");
    assertEquals("d_pipeline", pipelineDimension.getTableName());
    assertEquals(DmDimensionScdType.TYPE1, pipelineDimension.getScdTypeOrDefault());
    assertTrue(
        pipelineDimension.getSourceOrDefault().getSourceSql().toLowerCase(java.util.Locale.ROOT)
            .contains(" as load_dt"));
    assertTrue(
        model
            .findTable("dim_model")
            .getSourceOrDefault()
            .getSourceSql()
            .toLowerCase(java.util.Locale.ROOT)
            .contains(" as load_dt"));
    assertEquals(
        DmSourceType.DATE_GENERATOR,
        model.findTable(OpsAnalyticsDimensionalBuilder.DATE_DIMENSION)
            .getSourceOrDefault()
            .resolveSourceType());
    assertNotNull(model.findTable(OpsAnalyticsDimensionalBuilder.LOAD_DATE));
    assertNotNull(model.findTable(OpsAnalyticsDimensionalBuilder.MEASURED_DATE));
    assertNotNull(model.findTable(OpsAnalyticsDimensionalBuilder.HARVEST_DATE));
    assertNotNull(model.findTable(OpsAnalyticsDimensionalBuilder.ALERTED_DATE));

    DmFact pipeline = (DmFact) model.findTable("fact_pipeline_load");
    assertTrue(measureNames(pipeline).contains("duration_ms"));
    assertTrue(roleTargets(pipeline).contains("dim_pipeline"));
    assertTrue(roleTargets(pipeline).contains(OpsAnalyticsDimensionalBuilder.LOAD_DATE));
    assertTrue(pipeline.getSourceOrDefault().getSourceSql().contains("run_id"));
    assertTrue(pipeline.getSourceOrDefault().getSourceSql().contains("hub_load_run"));
    assertEquals("Load", pipeline.getBusinessProcessOrEmpty().getLevel1());

    DmFact finding = (DmFact) model.findTable("fact_quality_finding");
    assertTrue(degenerateNames(finding).contains("severity"));
    assertTrue(roleTargets(finding).contains("dim_subject"));
    assertTrue(measureNames(finding).contains("row_count"));
    assertTrue(finding.getSourceOrDefault().getSourceSql().contains("subject_key"));
    assertTrue(finding.getSourceOrDefault().getSourceSql().contains("hub_subject"));
    assertEquals("Quality", finding.getBusinessProcessOrEmpty().getLevel1());

    DmDimension transformDimension = (DmDimension) model.findTable("dim_transform");
    assertEquals(1, transformDimension.getNaturalKeysOrEmpty().size());
    assertEquals("transform_key", transformDimension.getNaturalKeysOrEmpty().get(0).getFieldName());
    DmFact transformFact = (DmFact) model.findTable("fact_transform_load");
    assertTrue(transformFact.getSourceOrDefault().getSourceSql().contains("transform_key"));
    assertTrue(transformFact.getSourceOrDefault().getSourceSql().contains("hub_transform"));
    assertTrue(transformFact.getSourceOrDefault().getSourceSql().contains("run_id"));
    assertTrue(
        ((DmFact) model.findTable("fact_quality_subject"))
            .getSourceOrDefault()
            .getSourceSql()
            .contains("quality_run_id"));
    assertTrue(
        ((DmFact) model.findTable("fact_quality_field"))
            .getSourceOrDefault()
            .getSourceSql()
            .contains("quality_run_id"));
    assertTrue(
        ((DmFact) model.findTable("fact_harvest_subject"))
            .getSourceOrDefault()
            .getSourceSql()
            .contains("harvest_run_id"));
    String workflowModelSql =
        ((DmFact) model.findTable("fact_workflow_model")).getSourceOrDefault().getSourceSql();
    assertTrue(workflowModelSql.contains("run_id AS load_run_id"), workflowModelSql);
    assertTrue(workflowModelSql.contains("hub_load_run"), workflowModelSql);

    DmFact insight = (DmFact) model.findTable("fact_load_insight");
    assertTrue(measureNames(insight).contains("row_count"));
    assertTrue(roleTargets(insight).contains(OpsAnalyticsDimensionalBuilder.LOAD_DATE));
    assertFalse(insight.getSourceOrDefault().getSourceSql().isBlank());

    DmFact overview = (DmFact) model.findTable("fact_workflow_overview");
    assertTrue(measureNames(overview).contains("duration_ms"));
    assertFalse(overview.getSourceOrDefault().getSourceSql().isBlank());

    SemanticModel semantic =
        OpsAnalyticsDimensionalBuilder.semanticLayer(
            model, new Variables(), new MemoryMetadataProvider());
    assertNotNull(semantic.findSelection(OpsAnalyticsDimensionalBuilder.SELECTION_PIPELINE_DURATION));
    SemanticSelection trend =
        semantic.findSelection(OpsAnalyticsDimensionalBuilder.SELECTION_MODEL_TREND);
    assertEquals("fact_workflow_model", trend.getEntityName());
    assertNotNull(semantic.findSelection(OpsAnalyticsDimensionalBuilder.SELECTION_QUALITY));
    assertNotNull(semantic.findEntity("fact_quality_finding").findMeasure("finding_count"));

    BusinessProcessCatalogMeta catalog = OpsAnalyticsDimensionalBuilder.busCatalog();
    assertEquals(OpsAnalyticsDimensionalBuilder.BUS_CATALOG, catalog.getName());
    assertEquals(1, catalog.getDomains().size());
    assertEquals(List.of("Load", "Quality", "Harvest"), level1Names(catalog));
  }

  @Test
  void shapedModelHasNoFactOrLinkValidationErrors() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DatabaseMeta database = new DatabaseMeta();
    database.setName(OpsAnalyticsDimensionalBuilder.TARGET_DATABASE);
    database.setDatabaseType("POSTGRESQL");
    database.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    database.setHostname("localhost");
    database.setDBName("test");
    database.setPort("54320");
    database.setUsername("test");
    database.setPassword("test");
    provider.getSerializer(DatabaseMeta.class).save(database);

    DataVaultModel vault =
        OpsAnalyticsVaultBuilder.apply(
            OpsAnalyticsSourceModelBuilder.build(), new Variables(), provider);
    DimensionalModel model = OpsAnalyticsDimensionalBuilder.shape(vault);
    List<String> errors =
        model.check(provider, new Variables(), null).stream()
            .filter(remark -> remark.getType() == ICheckResult.TYPE_RESULT_ERROR)
            .map(ICheckResult::getText)
            .filter(
                text ->
                    text.contains("at least 2 hubs")
                        || text.contains("composite keys")
                        || text.contains("at least one measure")
                        || text.contains("at least one dimension role")
                        || text.contains("requires source SQL"))
            .toList();
    assertTrue(errors.isEmpty(), errors::toString);
  }

  @Test
  void splitsFactsFromConformedDimensions() throws Exception {
    DataVaultModel vault =
        OpsAnalyticsVaultBuilder.apply(
            OpsAnalyticsSourceModelBuilder.build(),
            new Variables(),
            new MemoryMetadataProvider());
    DimensionalModel shaped = OpsAnalyticsDimensionalBuilder.shape(vault);
    String shared = "${PROJECT_HOME}/models/ops-analytics/dm/ops-analytics-dimensions.hdm";
    OpsAnalyticsDimensionalBuilder.OpsAnalyticsDimensionalModels split =
        OpsAnalyticsDimensionalBuilder.partition(shaped, shared);

    assertEquals(OpsAnalyticsDimensionalBuilder.DIMENSIONS_MODEL, split.dimensions().getName());
    assertNotNull(split.dimensions().findTable("dim_pipeline"));
    assertNotNull(split.dimensions().findTable(OpsAnalyticsDimensionalBuilder.DATE_DIMENSION));
    assertTrue(
        split.dimensions().getTables().stream().noneMatch(table -> table instanceof DmFact));
    assertEquals(12, split.facts().size());

    DimensionalModel pipeline =
        split.facts().stream()
            .filter(model -> model.findTable("fact_pipeline_load") != null)
            .findFirst()
            .orElseThrow();
    assertEquals(1, pipeline.getTables().stream().filter(table -> table instanceof DmFact).count());
    DmDimensionAlias pipelineAlias = (DmDimensionAlias) pipeline.findTable("dim_pipeline");
    assertEquals(DmTableType.DIMENSION_ALIAS, pipelineAlias.getTableType());
    assertEquals("dim_pipeline", pipelineAlias.getReferencedDimensionName());
    assertEquals(shared, pipelineAlias.getReferencedModelFilename());
    assertEquals("d_pipeline", pipelineAlias.getTableName());
    DmDimensionAlias loadDate = (DmDimensionAlias) pipeline.findTable(OpsAnalyticsDimensionalBuilder.LOAD_DATE);
    assertEquals(OpsAnalyticsDimensionalBuilder.DATE_DIMENSION, loadDate.getReferencedDimensionName());
    assertEquals(shared, loadDate.getReferencedModelFilename());

    SemanticModel semantic =
        OpsAnalyticsDimensionalBuilder.semanticLayer(
            pipeline, new Variables(), new MemoryMetadataProvider());
    assertNotNull(semantic.findSelection(OpsAnalyticsDimensionalBuilder.SELECTION_PIPELINE_DURATION));
    assertNull(semantic.findSelection(OpsAnalyticsDimensionalBuilder.SELECTION_QUALITY));
  }

  @Test
  void rewriteRetailDimensionalModelsWhenRequested() throws Exception {
    assumeTrue("1".equals(System.getenv("OPS_ANALYTICS_REWRITE_RETAIL")));
    Path retail = Path.of("retail-example");
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", retail.toAbsolutePath().toString());
    org.apache.hop.metadata.serializer.json.JsonMetadataProvider provider =
        new org.apache.hop.metadata.serializer.json.JsonMetadataProvider(
            org.apache.hop.core.encryption.Encr.getEncoder(),
            retail.resolve("metadata").toString(),
            variables);
    SourceModel source =
        SourceModelLoadSupport.load(
            retail.resolve("models/ops-analytics/ops-analytics.hsm").toString(),
            variables,
            provider);
    DataVaultModel vault = OpsAnalyticsVaultBuilder.apply(source, variables, provider);
    DimensionalModel model = OpsAnalyticsDimensionalBuilder.shape(vault);
    List<String> files =
        OpsAnalyticsProjectBuilder.writeDimensionalModels(
            model, retail.resolve("models/ops-analytics").toString(), variables, provider);
    org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta group =
        provider
            .getSerializer(org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta.class)
            .load(OpsAnalyticsProjectBuilder.GROUP_NAME);
    group.setDimensionalModelFiles(new java.util.ArrayList<>(files));
    provider
        .getSerializer(org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta.class)
        .save(group);
  }

  private static void assertFact(DimensionalModel model, String tableName) {
    assertNotNull(findByTable(model, tableName), () -> names(model));
  }

  private static IDmTable findByTable(DimensionalModel model, String tableName) {
    for (IDmTable table : model.getTables()) {
      if (table != null && tableName.equals(table.getTableName())) {
        return table;
      }
    }
    return null;
  }

  private static String names(DimensionalModel model) {
    return model.getTables().stream()
        .map(table -> table.getName() + "/" + table.getTableName())
        .collect(Collectors.joining(", "));
  }

  private static List<String> measureNames(DmFact fact) {
    return fact.getMeasuresOrEmpty().stream().map(measure -> measure.getFieldName()).toList();
  }

  private static List<String> roleTargets(DmFact fact) {
    return fact.getDimensionRolesOrEmpty().stream()
        .map(DmFactDimensionRole::getDimensionTableName)
        .toList();
  }

  private static List<String> degenerateNames(DmFact fact) {
    return fact.getDegenerateDimensionsOrEmpty().stream()
        .map(degenerate -> degenerate.getFieldName())
        .toList();
  }

  private static List<String> level1Names(BusinessProcessCatalogMeta catalog) {
    return catalog.getLevel1().stream().map(term -> term.getName()).toList();
  }
}
