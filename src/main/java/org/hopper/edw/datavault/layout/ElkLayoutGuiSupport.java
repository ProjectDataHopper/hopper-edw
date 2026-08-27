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
package org.hopper.edw.datavault.layout;

import java.util.List;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.Point;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.config.DataVaultConfigSingleton;

/** Shared GUI helpers for ELK layout toolbar actions. */
public final class ElkLayoutGuiSupport {

  private static final Class<?> PKG = ElkLayoutGuiSupport.class;

  private ElkLayoutGuiSupport() {}

  public static ElkLayout getConfig() {
    return DataVaultConfigSingleton.getConfig().getElkLayout();
  }

  /**
   * Opens the ELK layout options dialog pre-filled from global defaults. Returns the chosen layout
   * on OK, or {@code null} when cancelled.
   */
  public static ElkLayout promptForLayout(Shell shell) {
    ElkLayoutDialog dialog = new ElkLayoutDialog(shell, new ElkLayout(getConfig()));
    if (!dialog.open()) {
      return null;
    }
    ElkLayout layout = dialog.getLayout();
    if (dialog.isSaveAsDefault()) {
      ElkLayout global = DataVaultConfigSingleton.getConfig().getElkLayout();
      boolean keepEnabled = global.isEnabled();
      ElkLayout updated = new ElkLayout(layout);
      updated.setEnabled(keepEnabled);
      DataVaultConfigSingleton.getConfig().setElkLayout(updated);
      try {
        DataVaultConfigSingleton.saveConfig();
      } catch (Exception e) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "ElkLayoutGui.SaveDefault.Error.Header"),
            BaseMessages.getString(PKG, "ElkLayoutGui.SaveDefault.Error.Message"),
            e);
      }
    }
    return layout;
  }

  public static void showLayoutError(
      Shell shell, Class<?> messageClass, String keyPrefix, Exception error) {
    new ErrorDialog(
        shell,
        BaseMessages.getString(messageClass, keyPrefix + ".Layout.Error.Header"),
        BaseMessages.getString(messageClass, keyPrefix + ".Layout.Error.Message"),
        error);
  }

  public static Point[] captureLocations(List<? extends IGuiPosition> items) {
    Point[] locations = new Point[items.size()];
    for (int i = 0; i < items.size(); i++) {
      IGuiPosition item = items.get(i);
      if (item == null || item.getLocation() == null) {
        locations[i] = new Point(0, 0);
      } else {
        Point location = item.getLocation();
        locations[i] = new Point(location.x, location.y);
      }
    }
    return locations;
  }
}
