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
package org.apache.hop.datavault.hopgui.file.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.ModelXmlWriteSupport;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.publish.SourceQueryCatalogPublisher;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

/**
 * Opens the source query builder from outside {@code .hsm} (e.g. Data Vault modeler), saves the
 * model, and optionally publishes a composite catalog feed.
 */
public final class HopGuiSourceQueryComposeSupport {

  private static final Class<?> PKG = HopGuiSourceQueryComposeSupport.class;

  private HopGuiSourceQueryComposeSupport() {}

  /**
   * @return published catalog feed name, or null when cancelled / not published
   */
  public static String composeAndPublish(HopGui hopGui, Shell shell) {
    if (hopGui == null || shell == null) {
      return null;
    }
    IVariables variables = hopGui.getVariables();
    IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();

    String filename =
        BaseDialog.presentFileDialog(
            false,
            shell,
            new String[] {"*.hsm", "*"},
            new String[] {
              BaseMessages.getString(PKG, "HopGuiSourceQueryComposeSupport.FileType.Hsm"),
              BaseMessages.getString(PKG, "HopGuiSourceQueryComposeSupport.FileType.All")
            },
            true);
    if (Utils.isEmpty(filename)) {
      return null;
    }

    try {
      SourceModel model = SourceModelLoadSupport.load(filename, variables, metadataProvider);
      List<String> queryNames = new ArrayList<>();
      for (SourceQuery q : model.getQueries()) {
        if (q != null && !Utils.isEmpty(q.getName())) {
          queryNames.add(q.getName());
        }
      }

      SourceQuery query;
      if (queryNames.isEmpty()) {
        query = new SourceQuery(uniqueQueryName(model, "query"));
        if (!model.getTables().isEmpty() && model.getTables().get(0) != null) {
          query.setDrivingTableName(model.getTables().get(0).getName());
        }
        model.getQueries().add(query);
      } else {
        // Edit first query for now; full picker can land later.
        query = model.findQuery(queryNames.get(0));
        if (query == null) {
          query = model.getQueries().get(0);
        }
      }

      boolean accepted =
          new HopGuiSourceQueryDialog(shell, query, model, variables, metadataProvider).open();
      if (!accepted) {
        // Remove unsaved new query if it was just added with empty columns.
        if (query.getColumns().isEmpty() && model.getQueries().contains(query)) {
          // keep model unchanged on disk
        }
        return null;
      }

      // Persist .hsm so composite pointer stays valid.
      model.setFilename(filename);
      ModelXmlWriteSupport.writeModelXml(SourceModel.XML_TAG, model, filename, variables);
      model.clearChanged();

      SourceQueryCatalogPublisher.PublishResult result =
          SourceQueryCatalogPublisher.publish(model, query, null, variables, metadataProvider);
      DvDatabaseSourceImportSupport.refreshCatalogPerspective();

      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "HopGuiSourceQueryComposeSupport.Success.Title"));
      box.setMessage(
          BaseMessages.getString(
              PKG, "HopGuiSourceQueryComposeSupport.Success.Message", result.catalogName()));
      box.open();
      return result.catalogName();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiSourceQueryComposeSupport.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiSourceQueryComposeSupport.Error.Message"),
          e);
      return null;
    }
  }

  private static String uniqueQueryName(SourceModel model, String base) {
    String name = base;
    int i = 2;
    while (model.findQuery(name) != null) {
      name = base + i;
      i++;
    }
    return name;
  }
}
