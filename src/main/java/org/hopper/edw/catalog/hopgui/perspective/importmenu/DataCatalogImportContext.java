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
package org.hopper.edw.catalog.hopgui.perspective.importmenu;

import lombok.Getter;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.widgets.Shell;

/** Runtime context passed to data catalog import actions. */
@Getter
public final class DataCatalogImportContext {

  private final Shell shell;
  private final HopGui hopGui;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final DataVaultModel model;
  private final String preferredCatalogConnectionName;
  private final Runnable onComplete;

  public DataCatalogImportContext(
      Shell shell,
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      String preferredCatalogConnectionName,
      Runnable onComplete) {
    this.shell = shell;
    this.hopGui = hopGui;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.model = model;
    this.preferredCatalogConnectionName = preferredCatalogConnectionName;
    this.onComplete = onComplete;
  }
}
