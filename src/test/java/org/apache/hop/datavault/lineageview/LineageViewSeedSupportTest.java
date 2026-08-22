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
package org.apache.hop.datavault.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.lineage.LineageLayer;
import org.apache.hop.datavault.lineage.LineageSnapshot;
import org.apache.hop.datavault.lineageview.backend.LineageDirection;
import org.apache.hop.datavault.lineageview.backend.LineageNodeKind;
import org.apache.hop.datavault.lineageview.backend.LineageQuery;
import org.apache.hop.datavault.lineageview.backend.LineageSeedKind;
import org.apache.hop.datavault.metadata.lineage.LineageBackendMeta;
import org.apache.hop.datavault.metadata.lineage.LocalModelsBackendSettings;
import org.apache.hop.datavault.metadata.lineage.MarquezBackendSettings;
import org.junit.jupiter.api.Test;

class LineageViewSeedSupportTest {

  @Test
  void modelTableRefreshWritesJobAndDatasetIds() {
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setSeedKind(LineageSeedKind.MODEL_TABLE);
    document.setModelLayer(LineageLayer.DM);
    document.setModelName("retail-pos");
    document.setLogicalTable("f_orders");

    LineageBackendMeta backend = new LineageBackendMeta("local");
    LocalModelsBackendSettings settings = new LocalModelsBackendSettings();
    settings.setJobNamespace("retail-job");
    settings.setDatasetNamespace("retail-dataset");
    backend.setSettings(settings);

    Variables variables = new Variables();
    LineageViewSeedSupport.refreshOpenLineageIds(document, backend, variables);

    assertTrue(document.getJobNamespace().startsWith("retail-job"));
    assertEquals("dm/retail-pos/f_orders", document.getJobName());
    assertEquals("retail-dataset", document.getDatasetNamespace());
    assertEquals("f_orders", document.getDatasetName());

    LineageQuery query = LineageViewSeedSupport.toQuery(document);
    assertTrue(query.getJob().isComplete());
    assertEquals(
        "job:" + document.getJobNamespace() + ":dm/retail-pos/f_orders",
        query.getJob().toNodeId(org.apache.hop.datavault.lineageview.backend.LineageNodeKind.JOB));
    assertEquals(
        "dataset:retail-dataset:f_orders",
        query
            .getDataset()
            .toNodeId(org.apache.hop.datavault.lineageview.backend.LineageNodeKind.DATASET));
  }

  @Test
  void datasetSeedIsLeftAlone() {
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setSeedKind(LineageSeedKind.DATASET);
    document.setDatasetNamespace("Vault");
    document.setDatasetName("hub_customer");
    document.setJobName("should-stay");
    LineageViewSeedSupport.refreshOpenLineageIds(document, null, new Variables());
    assertEquals("should-stay", document.getJobName());
  }

  @Test
  void modelTableRefreshOverwritesStaleIds() {
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setSeedKind(LineageSeedKind.MODEL_TABLE);
    document.setModelLayer(LineageLayer.DV);
    document.setModelName("crm");
    document.setLogicalTable("hub_customer");
    document.setJobNamespace("old-ns");
    document.setJobName("old/job");
    document.setDatasetNamespace("old-ds");
    document.setDatasetName("old_name");

    LineageBackendMeta backend = new LineageBackendMeta("local");
    LocalModelsBackendSettings settings = new LocalModelsBackendSettings();
    settings.setJobNamespace("retail-job");
    settings.setDatasetNamespace("retail-dataset");
    backend.setSettings(settings);

    LineageViewSeedSupport.refreshOpenLineageIds(document, backend, new Variables());
    assertEquals("dv/crm/hub_customer", document.getJobName());
    assertEquals("retail-dataset", document.getDatasetNamespace());
    assertEquals("hub_customer", document.getDatasetName());
  }

  @Test
  void emptyMarquezDefaultsKeepStoredJobNamespace() {
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setSeedKind(LineageSeedKind.MODEL_TABLE);
    document.setModelLayer(LineageLayer.DM);
    document.setModelName("retail-f-order-lines");
    document.setLogicalTable("f_order_lines");
    document.setJobNamespace("hop-data-vault/retail-example");

    LineageBackendMeta backend = new LineageBackendMeta("marquez");
    MarquezBackendSettings settings = new MarquezBackendSettings();
    settings.setDefaultJobNamespace("");
    settings.setDefaultDatasetNamespace("");
    backend.setSettings(settings);

    LineageViewSeedSupport.refreshOpenLineageIds(document, backend, new Variables());
    assertEquals("hop-data-vault/retail-example", document.getJobNamespace());
    assertEquals("dm/retail-f-order-lines/f_order_lines", document.getJobName());
  }

  @Test
  void marquezDefaultsResolveWithProjectKey() {
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setSeedKind(LineageSeedKind.MODEL_TABLE);
    document.setModelLayer(LineageLayer.DM);
    document.setModelName("retail-f-order-lines");
    document.setLogicalTable("f_order_lines");

    LineageBackendMeta backend = new LineageBackendMeta("marquez");
    MarquezBackendSettings settings = new MarquezBackendSettings();
    settings.setDefaultJobNamespace("${MARQUEZ_NAMESPACE_JOB}");
    settings.setDefaultDatasetNamespace("${MARQUEZ_NAMESPACE_DATASET}");
    backend.setSettings(settings);

    Variables variables = new Variables();
    variables.setVariable("MARQUEZ_NAMESPACE_JOB", "retail-job");
    variables.setVariable("MARQUEZ_NAMESPACE_DATASET", "retail-dataset");
    variables.setVariable("PROJECT_HOME", "/tmp/retail-example");

    LineageViewSeedSupport.refreshOpenLineageIds(document, backend, variables);
    assertEquals("retail-job/retail-example", document.getJobNamespace());
    assertEquals("retail-dataset", document.getDatasetNamespace());
    assertEquals(
        "dataset:retail-dataset:f_order_lines",
        LineageViewSeedSupport.toQuery(document).getDataset().toNodeId(LineageNodeKind.DATASET));
  }

  @Test
  void fromModelTableSeedsUpstreamView() {
    HopLineageViewDocument document =
        LineageViewSeedSupport.fromModelTable(
            LineageLayer.DM,
            "retail-f-order-lines",
            "f_order_lines",
            "${PROJECT_HOME}/models/retail-f-order-lines.hdm");
    assertEquals(LineageSeedKind.MODEL_TABLE, document.getSeedKind());
    assertEquals(LineageLayer.DM, document.getModelLayer());
    assertEquals("f_order_lines", document.getLogicalTable());
    assertEquals(LineageDirection.UPSTREAM, document.getDirection());
    assertEquals(6, document.getDepth());
    assertTrue(document.isIncludeJobs());
    assertTrue(document.isIncludeOpsOverlay());

    LineageSnapshot extra = new LineageSnapshot();
    extra.setModelFilename("${PROJECT_HOME}/models/retail-f-order-lines.hdm");
    LineageQuery query = LineageViewSeedSupport.toQuery(document, java.util.List.of(extra));
    assertEquals(1, query.getExtraSnapshots().size());
    assertSame(extra, query.getExtraSnapshots().get(0));
  }
}
