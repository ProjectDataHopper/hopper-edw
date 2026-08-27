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
package org.hopper.edw.datavault.hopgui.coaching;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.edw.catalog.hopgui.perspective.importmenu.DataCatalogImportMenu;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.coaching.DvCoachingModelAdapter;
import org.hopper.edw.datavault.metadata.coaching.ICoachingModelAdapter;

/** Opens catalog import flows from the coach panel without leaving the modeler. */
public final class CoachingImportSupport {

  private CoachingImportSupport() {}

  public static void openImportMenu(
      HopGui hopGui, ICoachingModelAdapter adapter, IVariables variables, Runnable onComplete)
      throws HopException {
    DataVaultModel model = null;
    if (adapter instanceof DvCoachingModelAdapter dvAdapter) {
      model = dvAdapter.getModel();
    }
    String preferredCatalog =
        adapter.resolveCatalogConnectionName(variables, hopGui.getMetadataProvider());
    DataCatalogImportMenu.open(hopGui, model, preferredCatalog, onComplete);
  }
}
