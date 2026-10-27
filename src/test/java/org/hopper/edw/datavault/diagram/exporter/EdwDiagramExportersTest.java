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
package org.hopper.edw.datavault.diagram.exporter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hop.core.diagram.DiagramExportService;
import org.apache.hop.core.diagram.IDiagramExporter;
import org.apache.hop.core.plugins.HopURLClassLoader;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.junit.jupiter.api.Test;

class EdwDiagramExportersTest {

  @Test
  void exportersFollowTheModelClassLoader() throws Exception {
    URL classes = DimensionalModel.class.getProtectionDomain().getCodeSource().getLocation();
    try (HopURLClassLoader other =
        new HopURLClassLoader(new URL[] {classes}, DimensionalModel.class.getClassLoader())) {
      Class<?> modelClass = other.loadClass(DimensionalModel.class.getName());
      assertNotSame(DimensionalModel.class, modelClass);
      Object model = modelClass.getDeclaredConstructor().newInstance();
      assertFalse(
          new DimensionalSvgDiagramExporter().supportsSubject(model),
          "An exporter from the test loader must not claim a model from another loader");

      other
          .loadClass(EdwDiagramExporters.class.getName())
          .getMethod("registerForThisClassLoader")
          .invoke(null);

      DiagramExportService service = DiagramExportService.getInstance();
      List<IDiagramExporter<?>> found = service.findExportersForSubject(model);
      try {
        Set<String> ids = found.stream().map(IDiagramExporter::getId).collect(Collectors.toSet());
        assertTrue(ids.contains("dimensional-svg"), ids.toString());
        assertTrue(ids.contains("dimensional-mermaid"), ids.toString());
        assertTrue(ids.contains("dimensional-plantuml"), ids.toString());
        assertTrue(ids.contains("dimensional-drawio"), ids.toString());
        for (IDiagramExporter<?> exporter : found) {
          assertTrue(
              exporter.supportsSubject(model),
              exporter.getId() + " was returned but does not support the model");
        }
      } finally {
        for (IDiagramExporter<?> exporter : found) {
          service.unregisterStaticExporter(exporter);
        }
      }
    }
  }
}
