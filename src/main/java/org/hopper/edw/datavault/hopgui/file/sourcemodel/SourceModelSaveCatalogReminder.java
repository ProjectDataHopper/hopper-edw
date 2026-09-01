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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageDialogWithToggle;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.config.DataVaultConfig;
import org.hopper.edw.datavault.config.DataVaultConfigSingleton;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceCatalogPublishSyncSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceCatalogPublishSyncSupport.StalePublishedFeed;

/** After a source model save, optionally asks to publish catalog feeds that lag the canvas. */
public final class SourceModelSaveCatalogReminder {

  private static final Class<?> PKG = HopGuiSourceModelGraph.class;

  private SourceModelSaveCatalogReminder() {}

  public static void offerAfterSave(HopGui hopGui, HopGuiSourceModelGraph graph) {
    if (hopGui == null || graph == null || graph.getModel() == null) {
      return;
    }
    DataVaultConfig config = DataVaultConfigSingleton.getConfig();
    if (config == null || !config.isRemindUnpublishedCatalogOnSourceModelSave()) {
      return;
    }
    Shell shell = graph.getShell();
    if (shell == null || shell.isDisposed()) {
      return;
    }
    List<StalePublishedFeed> stale;
    try {
      stale =
          SourceCatalogPublishSyncSupport.listStalePublishedFeeds(
              graph.getModel(), graph.getVariables(), hopGui.getMetadataProvider());
    } catch (Exception ignored) {
      return;
    }
    if (stale == null || stale.isEmpty()) {
      return;
    }

    StringBuilder body = new StringBuilder();
    body.append(BaseMessages.getString(PKG, "HopGuiSourceModelGraph.SaveCatalogReminder.Message"));
    body.append(Const.CR).append(Const.CR);
    for (StalePublishedFeed feed : stale) {
      body.append("  • ").append(formatFeed(feed)).append(Const.CR);
    }
    body.append(Const.CR);
    body.append(BaseMessages.getString(PKG, "HopGuiSourceModelGraph.SaveCatalogReminder.Question"));

    try {
      MessageDialogWithToggle dialog =
          new MessageDialogWithToggle(
              shell,
              BaseMessages.getString(PKG, "HopGuiSourceModelGraph.SaveCatalogReminder.Title"),
              body.toString().trim(),
              SWT.ICON_WARNING,
              new String[] {
                BaseMessages.getString(PKG, "HopGuiSourceModelGraph.SaveCatalogReminder.Yes"),
                BaseMessages.getString(PKG, "HopGuiSourceModelGraph.SaveCatalogReminder.No")
              },
              BaseMessages.getString(PKG, "HopGuiSourceModelGraph.SaveCatalogReminder.Toggle"),
              false);
      int answer = dialog.open();
      if (dialog.getToggleState()) {
        config.setRemindUnpublishedCatalogOnSourceModelSave(false);
        try {
          DataVaultConfigSingleton.saveConfig();
        } catch (Exception e) {
          new ErrorDialog(
              shell,
              BaseMessages.getString(PKG, "HopGuiSourceModelGraph.PushToCatalog.Error.Title"),
              BaseMessages.getString(
                  PKG, "HopGuiSourceModelGraph.SaveCatalogReminder.ToggleSaveFailed"),
              e);
        }
      }
      if (answer == 0) {
        graph.publishStaleCatalogFeeds(stale);
      }
    } catch (Exception ignored) {
      // File is already saved; never fail the save because the reminder could not be shown.
    }
  }

  static String formatFeed(StalePublishedFeed feed) {
    if (feed == null) {
      return "";
    }
    String label = Const.NVL(feed.cardName(), "?");
    if (!Utils.isEmpty(feed.feedName()) && !feed.feedName().equals(feed.cardName())) {
      label = label + " → " + feed.feedName();
    }
    if (!Utils.isEmpty(feed.details())) {
      return label + " (" + feed.details() + ")";
    }
    return label;
  }
}
