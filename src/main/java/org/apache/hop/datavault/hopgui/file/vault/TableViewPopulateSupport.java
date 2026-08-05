/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.hopgui.file.vault;

import org.apache.hop.ui.core.widget.TableView;

/**
 * Helpers for filling {@link TableView} widgets without side effects that hurt dialog open time.
 *
 * <p>{@link TableView#clearAll()} always schedules {@code edit(0, 1)} asynchronously for
 * non-readonly tables. That forces CCOMBO {@code ComboValuesSelectionListener}s (including catalog
 * source name listing) during {@code getData()}, which is why large projects saw multi-second waits
 * when opening link dialogs.
 */
public final class TableViewPopulateSupport {

  private TableViewPopulateSupport() {}

  /**
   * Removes all rows without scheduling a cell edit. Callers should add new items and then {@link
   * TableView#optimizeTableView()} (which ensures at least one empty row when needed).
   */
  public static void clearRows(TableView view) {
    if (view == null || view.isDisposed() || view.table == null || view.table.isDisposed()) {
      return;
    }
    view.table.removeAll();
  }
}
