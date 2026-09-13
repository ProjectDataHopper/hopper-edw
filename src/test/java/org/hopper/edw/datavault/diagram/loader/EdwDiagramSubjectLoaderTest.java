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
package org.hopper.edw.datavault.diagram.loader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.diagram.DiagramExportOptions;
import org.apache.hop.core.diagram.DiagramExportResult;
import org.apache.hop.core.diagram.DiagramExportService;
import org.apache.hop.core.diagram.ExportContext;
import org.apache.hop.core.diagram.IExportContext;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.diagram.exporter.DataVaultSvgDiagramExporter;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EdwDiagramSubjectLoaderTest {

  private static final Path DV_PATH =
      Path.of("integration-tests/tests/multi-satellite-bv/customer-360.hdv")
          .toAbsolutePath()
          .normalize();

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void loadersClaimTheirExtensionsIncludingVfsUris() {
    DataVaultDiagramSubjectLoader dv = new DataVaultDiagramSubjectLoader();
    assertTrue(dv.supportsFile("models/sales.hdv"));
    assertTrue(dv.supportsFile("file:///tmp/sales.hdv"));
    assertTrue(dv.supportsFile("file:///tmp/sales.HDV"));
    assertFalse(dv.supportsFile("models/sales.hbv"));
    assertTrue(new BusinessVaultDiagramSubjectLoader().supportsFile("x.hbv"));
    assertTrue(new DimensionalDiagramSubjectLoader().supportsFile("x.hdm"));
    assertTrue(new SourceModelDiagramSubjectLoader().supportsFile("x.hsm"));
    assertTrue(new ExecutionMapDiagramSubjectLoader().supportsFile("x.hem"));
    assertTrue(new LineageViewDiagramSubjectLoader().supportsFile("x.hlv"));
  }

  @Test
  void hopExportLoadsDataVaultFileAndWritesSvg() throws Exception {
    DiagramExportService service = DiagramExportService.getInstance();
    service.registerSubjectLoader(new DataVaultDiagramSubjectLoader());
    service.registerStaticExporter(new DataVaultSvgDiagramExporter());

    Object subject =
        service
            .findSubjectLoader(DV_PATH.toString())
            .loadSubject(DV_PATH.toString(), null, new Variables());
    assertTrue(subject instanceof DataVaultModel);

    DiagramExportOptions options = new DiagramExportOptions(null, "SVG");
    IExportContext context =
        new ExportContext(new Variables(), null, null, IExportContext.ExportEnvironment.TEST);
    DiagramExportResult result = service.exportFile(DV_PATH.toString(), options, context);
    assertTrue(result.isSuccess());
    assertTrue(result.getContent().contains("<svg"));
    assertTrue(result.getContent().contains("hub_customer"));
  }
}
