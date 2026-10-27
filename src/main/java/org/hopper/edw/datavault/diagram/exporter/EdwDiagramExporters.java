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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.hop.core.diagram.DiagramExportService;
import org.apache.hop.core.diagram.IDiagramExporter;
import org.apache.hop.core.logging.LogChannel;

/**
 * Publishes the EDW diagram exporters on the class loader that defined the caller.
 *
 * <p>Hop removes the shared plugin-folder class loader when any plugin in that folder is removed
 * ({@code PluginRegistry.removePlugin}). Presentation embed does that for every {@code
 * org.hopper.*} metadata type. The open model stays on the original loader, and a later lookup
 * builds the {@code @DiagramExporter} plugins on a new one. {@code Class.isInstance} then rejects
 * the model and the export dialog reports that no exporters exist. Instances created here use the
 * same classes as the model.
 */
public final class EdwDiagramExporters {

  private static final Set<ClassLoader> REGISTERED =
      Collections.newSetFromMap(new IdentityHashMap<>());

  private EdwDiagramExporters() {}

  /** Register every EDW exporter defined by this class's loader. Safe to call more than once. */
  public static synchronized void registerForThisClassLoader() {
    ClassLoader classLoader = EdwDiagramExporters.class.getClassLoader();
    if (!REGISTERED.add(classLoader)) {
      return;
    }
    DiagramExportService service = DiagramExportService.getInstance();
    register(service, SourceModelSvgDiagramExporter::new);
    register(service, SourceModelPdfDiagramExporter::new);
    register(service, SourceModelPlantUmlDiagramExporter::new);
    register(service, SourceModelMermaidDiagramExporter::new);
    register(service, SourceModelDrawioDiagramExporter::new);
    register(service, DataVaultSvgDiagramExporter::new);
    register(service, DataVaultPdfDiagramExporter::new);
    register(service, DataVaultPlantUmlDiagramExporter::new);
    register(service, DataVaultMermaidDiagramExporter::new);
    register(service, DataVaultDrawioDiagramExporter::new);
    register(service, BusinessVaultSvgDiagramExporter::new);
    register(service, BusinessVaultPdfDiagramExporter::new);
    register(service, BusinessVaultPlantUmlDiagramExporter::new);
    register(service, BusinessVaultMermaidDiagramExporter::new);
    register(service, BusinessVaultDrawioDiagramExporter::new);
    register(service, DimensionalSvgDiagramExporter::new);
    register(service, DimensionalPdfDiagramExporter::new);
    register(service, DimensionalPlantUmlDiagramExporter::new);
    register(service, DimensionalMermaidDiagramExporter::new);
    register(service, DimensionalDrawioDiagramExporter::new);
    register(service, ExecutionMapSvgDiagramExporter::new);
    register(service, ExecutionMapPdfDiagramExporter::new);
    register(service, ExecutionMapPlantUmlDiagramExporter::new);
    register(service, ExecutionMapMermaidDiagramExporter::new);
    register(service, ExecutionMapDrawioDiagramExporter::new);
    register(service, LineageViewSvgDiagramExporter::new);
  }

  private static void register(
      DiagramExportService service, Supplier<IDiagramExporter<?>> factory) {
    try {
      service.registerStaticExporter(factory.get());
    } catch (RuntimeException e) {
      LogChannel.GENERAL.logError("Unable to register an EDW diagram exporter", e);
    }
  }
}
