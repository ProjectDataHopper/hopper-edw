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
package org.hopper.edw.datavault.hopgui.file.vault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.eclipse.swt.widgets.Control;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.hopgui.GuiBusySupport;
import org.hopper.edw.datavault.metadata.DataVaultModel;

/**
 * Builds Data Vault source CCOMBO columns that load catalog names only when the user focuses the
 * combo cell (with a wait cursor). Avoids catalog listing on dialog open.
 */
public final class DvSourceComboSupport {

  private DvSourceComboSupport() {}

  /**
   * Creates a dialog-scoped cache for source names so multiple lazy source columns share one
   * catalog list.
   */
  public static AtomicReference<String[]> newSharedSourceNameCache() {
    return new AtomicReference<>();
  }

  /**
   * Creates a CCOMBO column whose values are loaded lazily on first cell edit. Subsequent edits
   * reuse a private cache.
   */
  public static ColumnInfo createLazySourceColumn(
      String columnTitle,
      Control busyControl,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    return createLazySourceColumn(
        columnTitle, busyControl, model, variables, metadataProvider, newSharedSourceNameCache());
  }

  /**
   * Creates a CCOMBO column that loads catalog names lazily and reuses {@code sharedCache} across
   * columns in the same dialog (e.g. link hub sources and satellite sources).
   */
  public static ColumnInfo createLazySourceColumn(
      String columnTitle,
      Control busyControl,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      AtomicReference<String[]> sharedCache) {
    AtomicReference<String[]> cache =
        sharedCache != null ? sharedCache : newSharedSourceNameCache();
    ColumnInfo column = new ColumnInfo(columnTitle, ColumnInfo.COLUMN_TYPE_CCOMBO, new String[0]);
    column.setComboValuesSelectionListener(
        (item, rowNr, colNr) ->
            loadSourceNames(busyControl, model, variables, metadataProvider, cache));
    return column;
  }

  private static String[] loadSourceNames(
      Control busyControl,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      AtomicReference<String[]> cached) {
    String[] existing = cached.get();
    if (existing != null) {
      return existing;
    }
    AtomicReference<String[]> loaded = new AtomicReference<>(new String[0]);
    GuiBusySupport.showWhile(
        busyControl,
        () -> {
          try {
            List<String> names =
                DvSourceCatalogService.listSourceNames(model, variables, metadataProvider);
            if (names == null) {
              names = new ArrayList<>();
            } else {
              names = new ArrayList<>(names);
            }
            Collections.sort(names);
            loaded.set(names.toArray(new String[0]));
          } catch (Exception e) {
            loaded.set(new String[0]);
          }
        });
    String[] result = loaded.get();
    cached.set(result);
    return result;
  }
}
