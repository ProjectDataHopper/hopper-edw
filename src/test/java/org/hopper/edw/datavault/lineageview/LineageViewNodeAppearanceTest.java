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
package org.hopper.edw.datavault.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.gui.IGc.EColor;
import org.hopper.edw.datavault.hopgui.file.modelgraph.DmTableDisplaySupport;
import org.hopper.edw.datavault.hopgui.file.modelgraph.DvTableDisplaySupport;
import org.hopper.edw.datavault.lineageview.backend.HopExportFacet;
import org.hopper.edw.datavault.lineageview.backend.HopLocationFacet;
import org.hopper.edw.datavault.lineageview.backend.LineageEdge;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageGraphLayer;
import org.hopper.edw.datavault.lineageview.backend.LineageNode;
import org.hopper.edw.datavault.lineageview.backend.LineageNodeKind;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.junit.jupiter.api.Test;

class LineageViewNodeAppearanceTest {

  @Test
  void iconsFollowTableType() {
    assertIcon(
        "HUB", DvTableDisplaySupport.getImagePath(DvTableType.HUB), "Hub", EColor.BACKGROUND);
    assertIcon("FACT", DmTableDisplaySupport.FACT_ICON, "Fact", EColor.BACKGROUND);
    assertIcon("DIMENSION", DmTableDisplaySupport.DIMENSION_ICON, "Dimension", EColor.BACKGROUND);
    assertIcon("SCD2", LineageViewNodeAppearance.BUSINESS_VAULT_ICON, "SCD2", EColor.BACKGROUND);

    LineageNode date =
        dataset(
            "d_date",
            HopExportFacet.builder()
                .tableType("DIMENSION")
                .logicalName("d_date")
                .modelLayer("DM")
                .build());
    LineageViewNodeAppearance dateLook = LineageViewNodeAppearance.of(null, date);
    assertEquals(LineageViewNodeAppearance.DATE_DIMENSION_ICON, dateLook.iconPath());
    assertEquals("Dimension", dateLook.typeLabel());
    assertEquals("DM", dateLook.layerLabel());
  }

  @Test
  void sourceAndJobUseTheirOwnLogos() {
    LineageNode source =
        LineageNode.builder()
            .id("dataset:ns:staging")
            .kind(LineageNodeKind.DATASET)
            .name("SQL staging")
            .layer(LineageGraphLayer.SOURCE)
            .build();
    LineageViewNodeAppearance sourceLook = LineageViewNodeAppearance.of(null, source);
    assertEquals(LineageViewNodeAppearance.SOURCE_ICON, sourceLook.iconPath());
    assertEquals("Source", sourceLook.typeLabel());
    assertEquals(EColor.LIGHTGRAY, sourceLook.fill());
    assertFalse(sourceLook.hopUiIcon());

    LineageNode catalog =
        source.toBuilder()
            .hopLocation(HopLocationFacet.builder().catalogConnection("local-catalog").build())
            .build();
    assertEquals(
        LineageViewNodeAppearance.CATALOG_ICON,
        LineageViewNodeAppearance.of(null, catalog).iconPath());

    LineageNode job =
        LineageNode.builder()
            .id("job:ns:dm/retail/f_orders")
            .kind(LineageNodeKind.JOB)
            .name("dm/retail/f_orders")
            .layer(LineageGraphLayer.DM)
            .hopExport(
                HopExportFacet.builder()
                    .tableType("FACT")
                    .logicalName("f_orders")
                    .modelLayer("DM")
                    .build())
            .build();
    LineageViewNodeAppearance jobLook = LineageViewNodeAppearance.of(null, job);
    assertEquals(LineageViewNodeAppearance.PIPELINE_ICON, jobLook.iconPath());
    assertEquals("Job", jobLook.typeLabel());
    assertEquals("DM", jobLook.layerLabel());
    assertEquals(EColor.LIGHTBLUE, jobLook.fill());
    assertTrue(jobLook.hopUiIcon());
    assertSame(job, LineageViewNodeAppearance.withResolvedExport(null, job));
  }

  @Test
  void datasetBorrowsTheProducingJobOnly() {
    HopExportFacet fact =
        HopExportFacet.builder()
            .tableType("FACT")
            .logicalName("f_order_lines")
            .physicalTableName("f_order_lines")
            .modelLayer("DM")
            .modelFilename("${PROJECT_HOME}/models/retail-f-order-lines.hdm")
            .build();
    LineageNode job =
        LineageNode.builder()
            .id("job:ns:load")
            .kind(LineageNodeKind.JOB)
            .name("dm/retail-f-order-lines/f_order_lines")
            .layer(LineageGraphLayer.DM)
            .hopExport(fact)
            .build();
    LineageNode output =
        LineageNode.builder()
            .id("dataset:ns:f_order_lines")
            .kind(LineageNodeKind.DATASET)
            .name("f_order_lines")
            .layer(LineageGraphLayer.SOURCE)
            .hopLocation(
                HopLocationFacet.builder()
                    .kind("DATABASE")
                    .connectionName("Vault")
                    .tableName("f_order_lines")
                    .build())
            .build();
    LineageNode input =
        LineageNode.builder()
            .id("dataset:ns:customer")
            .kind(LineageNodeKind.DATASET)
            .name("customer")
            .layer(LineageGraphLayer.SOURCE)
            .build();
    LineageGraph graph =
        LineageGraph.builder()
            .nodes(List.of(input, job, output))
            .edges(
                List.of(
                    LineageEdge.builder().fromNodeId(input.getId()).toNodeId(job.getId()).build(),
                    LineageEdge.builder().fromNodeId(job.getId()).toNodeId(output.getId()).build()))
            .build();

    LineageViewNodeAppearance outputLook = LineageViewNodeAppearance.of(graph, output);
    assertEquals(DmTableDisplaySupport.FACT_ICON, outputLook.iconPath());
    assertEquals("Fact", outputLook.typeLabel());
    assertEquals("DM", outputLook.layerLabel());
    assertEquals(EColor.BACKGROUND, outputLook.fill());

    LineageNode resolved = LineageViewNodeAppearance.withResolvedExport(graph, output);
    assertEquals("FACT", resolved.getHopExport().getTableType());
    assertNull(output.getHopExport());

    LineageNode jobForMenu = LineageViewNodeAppearance.forActions(graph, job);
    assertEquals("f_order_lines", jobForMenu.getHopLocation().getTableName());

    LineageViewNodeAppearance inputLook = LineageViewNodeAppearance.of(graph, input);
    assertEquals(LineageViewNodeAppearance.SOURCE_ICON, inputLook.iconPath());
    assertEquals(EColor.LIGHTGRAY, inputLook.fill());
    assertSame(input, LineageViewNodeAppearance.withResolvedExport(graph, input));
  }

  private static void assertIcon(String tableType, String icon, String label, EColor fill) {
    LineageViewNodeAppearance look =
        LineageViewNodeAppearance.of(
            null,
            dataset(
                "t",
                HopExportFacet.builder()
                    .tableType(tableType)
                    .logicalName("t")
                    .modelLayer("DV")
                    .build()));
    assertEquals(icon, look.iconPath());
    assertEquals(label, look.typeLabel());
    assertEquals(fill, look.fill());
    assertFalse(look.hopUiIcon());
  }

  private static LineageNode dataset(String name, HopExportFacet export) {
    return LineageNode.builder()
        .id("dataset:ns:" + name)
        .kind(LineageNodeKind.DATASET)
        .name(name)
        .hopExport(export)
        .build();
  }
}
