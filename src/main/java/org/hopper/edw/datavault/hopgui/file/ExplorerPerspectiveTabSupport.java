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
package org.hopper.edw.datavault.hopgui.file;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.TabItemHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;

/**
 * Helpers for attaching plugin file graphs to the Explorer perspective without reflection.
 *
 * <p>Hop's explorer used to expose a private {@code tabFolder} field; newer Hop versions support
 * multi-pane layouts and provide {@link ExplorerPerspective#getTabFolder()} / {@link
 * ExplorerPerspective#getItems()} instead.
 */
public final class ExplorerPerspectiveTabSupport {

  private ExplorerPerspectiveTabSupport() {}

  public static CTabFolder requireTabFolder(ExplorerPerspective explorer) throws HopException {
    if (explorer == null) {
      throw new HopException("Explorer perspective is not available");
    }
    CTabFolder folder = explorer.getTabFolder();
    if (folder == null || folder.isDisposed()) {
      throw new HopException("Explorer perspective has no active tab folder");
    }
    return folder;
  }

  public static void registerTabItem(
      ExplorerPerspective explorer, CTabItem tabItem, IHopFileTypeHandler handler)
      throws HopException {
    if (explorer == null) {
      throw new HopException("Explorer perspective is not available");
    }
    if (tabItem == null || handler == null) {
      throw new HopException("Tab item and file handler are required");
    }
    explorer.getItems().add(new TabItemHandler(tabItem, handler));
  }
}
