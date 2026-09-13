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

import org.apache.hop.core.diagram.DiagramExportService;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.IVariables;

@ExtensionPoint(
    id = "RegisterEdwDiagramSubjectLoadersExtensionPoint",
    extensionPointId = "HopEnvironmentAfterInit",
    description = "Register EDW diagram subject loaders for hop export")
public class RegisterEdwDiagramSubjectLoadersExtensionPoint
    implements IExtensionPoint<PluginRegistry> {

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, PluginRegistry pluginRegistry) throws HopException {
    DiagramExportService service = DiagramExportService.getInstance();
    service.registerSubjectLoader(new SourceModelDiagramSubjectLoader());
    service.registerSubjectLoader(new DataVaultDiagramSubjectLoader());
    service.registerSubjectLoader(new BusinessVaultDiagramSubjectLoader());
    service.registerSubjectLoader(new DimensionalDiagramSubjectLoader());
    service.registerSubjectLoader(new ExecutionMapDiagramSubjectLoader());
    service.registerSubjectLoader(new LineageViewDiagramSubjectLoader());
  }
}
