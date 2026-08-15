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
package org.apache.hop.datavault.hopgui.tovault;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.sourcemodel.HopGuiSourceModelGraph;
import org.apache.hop.datavault.hopgui.file.sourcemodel.HopSourceModelFileType;
import org.apache.hop.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.layout.ElkGraphLayout;
import org.apache.hop.datavault.layout.ElkLayout;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.ModelXmlWriteSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourcePipeline;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceToVaultApplyResult;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceToVaultApplySupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.TabItemHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** GUI entry points that classify a source model and apply hubs, links, and satellites. */
public final class SourceToVaultGenerationSupport {

  private static final Class<?> PKG = SourceToVaultGenerationSupport.class;

  private SourceToVaultGenerationSupport() {}

  public static void generateFromSourceModel(HopGui hopGui, HopGuiSourceModelGraph sourceGraph) {
    if (hopGui == null || sourceGraph == null || sourceGraph.getModel() == null) {
      return;
    }
    SourceModel sourceModel = sourceGraph.getModel();
    List<String> selected = selectedSourceNames(sourceModel);
    try {
      SourceToVaultReviewDialog dialog =
          new SourceToVaultReviewDialog(
              sourceGraph.getShell(),
              hopGui.getVariables(),
              sourceModel,
              null,
              selected.isEmpty() ? null : selected,
              true);
      if (!dialog.open()) {
        return;
      }
      if (dialog.getDestination() == SourceToVaultReviewDialog.Destination.EXISTING_MODEL) {
        applyToExistingFile(hopGui, sourceGraph.getShell(), sourceModel, dialog);
      } else {
        applyToNewFile(hopGui, sourceGraph.getShell(), sourceModel, dialog);
      }
    } catch (Exception e) {
      new ErrorDialog(
          sourceGraph.getShell(),
          BaseMessages.getString(PKG, "SourceToVaultGenerationSupport.Error.Title"),
          BaseMessages.getString(PKG, "SourceToVaultGenerationSupport.Error.Message"),
          e);
    }
  }

  public static void generateFromVaultModel(HopGui hopGui, HopGuiVaultGraph vaultGraph) {
    if (hopGui == null || vaultGraph == null || vaultGraph.getModel() == null) {
      return;
    }
    try {
      String filename =
          BaseDialog.presentFileDialog(
              false,
              vaultGraph.getShell(),
              null,
              hopGui.getVariables(),
              new String[] {"*" + HopSourceModelFileType.SOURCE_MODEL_FILE_EXTENSION},
              new String[] {HopSourceModelFileType.SOURCE_MODEL_FILE_TYPE_DESCRIPTION},
              true);
      if (filename == null) {
        return;
      }
      SourceModel sourceModel =
          SourceModelLoadSupport.load(
              hopGui.getVariables().resolve(filename),
              hopGui.getVariables(),
              hopGui.getMetadataProvider());
      SourceToVaultReviewDialog dialog =
          new SourceToVaultReviewDialog(
              vaultGraph.getShell(),
              hopGui.getVariables(),
              sourceModel,
              vaultGraph.getModel(),
              null,
              false);
      if (!dialog.open()) {
        return;
      }
      SourceToVaultApplyResult[] holder = new SourceToVaultApplyResult[1];
      vaultGraph.runUndoableModelChange(
          () -> {
            holder[0] =
                SourceToVaultApplySupport.apply(
                    sourceModel,
                    vaultGraph.getModel(),
                    dialog.getClassification(),
                    dialog.isPublishToCatalog(),
                    hopGui.getVariables(),
                    hopGui.getMetadataProvider());
            layoutQuietly(vaultGraph.getModel(), holder[0]);
          });
      vaultGraph.redraw();
      showResult(vaultGraph.getShell(), holder[0]);
    } catch (Exception e) {
      new ErrorDialog(
          vaultGraph.getShell(),
          BaseMessages.getString(PKG, "SourceToVaultGenerationSupport.Error.Title"),
          BaseMessages.getString(PKG, "SourceToVaultGenerationSupport.Error.Message"),
          e);
    }
  }

  private static void applyToNewFile(
      HopGui hopGui, Shell shell, SourceModel sourceModel, SourceToVaultReviewDialog dialog)
      throws Exception {
    IVariables variables = hopGui.getVariables();
    String proposedName =
        Const.NVL(sourceModel.getName(), "data-vault-model")
            + HopVaultFileType.VAULT_FILE_EXTENSION;
    String proposedFilename = variables.getVariable("user.home") + File.separator + proposedName;
    String output =
        BaseDialog.presentFileDialog(
            true,
            shell,
            null,
            variables,
            HopVfs.getFileObject(proposedFilename),
            new String[] {"*" + HopVaultFileType.VAULT_FILE_EXTENSION},
            new String[] {HopVaultFileType.VAULT_FILE_TYPE_DESCRIPTION},
            true);
    if (output == null) {
      return;
    }
    String realFilename = variables.resolve(output);
    DataVaultModel vault = new DataVaultModel();
    vault.setName(stripExtension(HopVfs.getFileObject(realFilename).getName().getBaseName()));
    SourceToVaultApplyResult result =
        SourceToVaultApplySupport.apply(
            sourceModel,
            vault,
            dialog.getClassification(),
            dialog.isPublishToCatalog(),
            variables,
            hopGui.getMetadataProvider());
    layoutQuietly(vault, result);
    ModelXmlWriteSupport.writeModelXml(HopVaultFileType.XML_TAG, vault, realFilename, variables);
    hopGui.fileDelegate.fileOpen(realFilename);
    showResult(shell, result);
  }

  private static void applyToExistingFile(
      HopGui hopGui, Shell shell, SourceModel sourceModel, SourceToVaultReviewDialog dialog)
      throws Exception {
    IVariables variables = hopGui.getVariables();
    String filename =
        BaseDialog.presentFileDialog(
            false,
            shell,
            null,
            variables,
            new String[] {"*" + HopVaultFileType.VAULT_FILE_EXTENSION},
            new String[] {HopVaultFileType.VAULT_FILE_TYPE_DESCRIPTION},
            true);
    if (filename == null) {
      return;
    }
    String realFilename = variables.resolve(filename);
    HopGuiVaultGraph openGraph = findOpenVaultGraph(realFilename);
    if (openGraph != null) {
      SourceToVaultApplyResult[] holder = new SourceToVaultApplyResult[1];
      openGraph.runUndoableModelChange(
          () -> {
            holder[0] =
                SourceToVaultApplySupport.apply(
                    sourceModel,
                    openGraph.getModel(),
                    dialog.getClassification(),
                    dialog.isPublishToCatalog(),
                    variables,
                    hopGui.getMetadataProvider());
            layoutQuietly(openGraph.getModel(), holder[0]);
          });
      openGraph.redraw();
      showResult(shell, holder[0]);
      return;
    }

    DataVaultModel vault = loadVault(realFilename, hopGui.getMetadataProvider());
    SourceToVaultApplyResult result =
        SourceToVaultApplySupport.apply(
            sourceModel,
            vault,
            dialog.getClassification(),
            dialog.isPublishToCatalog(),
            variables,
            hopGui.getMetadataProvider());
    layoutQuietly(vault, result);
    ModelXmlWriteSupport.writeModelXml(HopVaultFileType.XML_TAG, vault, realFilename, variables);
    hopGui.fileDelegate.fileOpen(realFilename);
    showResult(shell, result);
  }

  private static DataVaultModel loadVault(String filename, IHopMetadataProvider metadataProvider)
      throws HopException {
    try {
      Document document = XmlHandler.loadXmlFile(filename);
      Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      DataVaultModel model = new DataVaultModel();
      XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, metadataProvider);
      model.setFilename(filename);
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Error loading Data Vault model from '" + filename + "'", e);
    }
  }

  private static HopGuiVaultGraph findOpenVaultGraph(String filename) {
    ExplorerPerspective explorer = ExplorerPerspective.getInstance();
    if (explorer == null || Utils.isEmpty(filename)) {
      return null;
    }
    String normalized = filename.replace('\\', '/');
    for (TabItemHandler item : explorer.getItems()) {
      if (item != null && item.getTypeHandler() instanceof HopGuiVaultGraph graph) {
        String open = graph.getFilename();
        if (!Utils.isEmpty(open) && normalized.equalsIgnoreCase(open.replace('\\', '/'))) {
          return graph;
        }
      }
    }
    return null;
  }

  private static void layoutQuietly(DataVaultModel model, SourceToVaultApplyResult result) {
    try {
      ElkGraphLayout.fromDataVaultModel(model).layout(ElkLayout.createDefault());
    } catch (Exception e) {
      result.getWarnings().add("Layout skipped: " + e.getMessage());
    }
  }

  private static void showResult(Shell shell, SourceToVaultApplyResult result) {
    StringBuilder message = new StringBuilder();
    message.append(
        BaseMessages.getString(
            PKG,
            "SourceToVaultGenerationSupport.Success.Message",
            result.getCreatedTableNames().size(),
            result.getReusedTableNames().size()));
    if (!result.getPublishedFeeds().isEmpty()) {
      message
          .append(Const.CR)
          .append(
              BaseMessages.getString(
                  PKG,
                  "SourceToVaultGenerationSupport.Success.Published",
                  String.join(", ", result.getPublishedFeeds())));
    }
    if (!result.getWarnings().isEmpty()) {
      message.append(Const.CR).append(Const.CR);
      message.append(
          BaseMessages.getString(PKG, "SourceToVaultGenerationSupport.Success.Warnings"));
      for (String warning : result.getWarnings()) {
        message.append(Const.CR).append("- ").append(warning);
      }
    }
    MessageBox box =
        new MessageBox(
            shell,
            result.getWarnings().isEmpty()
                ? SWT.OK | SWT.ICON_INFORMATION
                : SWT.OK | SWT.ICON_WARNING);
    box.setText(BaseMessages.getString(PKG, "SourceToVaultGenerationSupport.Success.Title"));
    box.setMessage(message.toString());
    box.open();
  }

  private static List<String> selectedSourceNames(SourceModel model) {
    List<String> names = new ArrayList<>();
    for (SourceTable table : model.getTables()) {
      if (table != null && table.isSelected() && !Utils.isEmpty(table.getName())) {
        names.add(table.getName());
      }
    }
    for (SourceQuery query : model.getQueries()) {
      if (query != null && query.isSelected() && !Utils.isEmpty(query.getName())) {
        names.add(query.getName());
      }
    }
    for (SourceJson json : model.getJsonSources()) {
      if (json != null && json.isSelected() && !Utils.isEmpty(json.getName())) {
        names.add(json.getName());
      }
    }
    for (SourcePipeline pipeline : model.getPipelineSources()) {
      if (pipeline != null && pipeline.isSelected() && !Utils.isEmpty(pipeline.getName())) {
        names.add(pipeline.getName());
      }
    }
    return names;
  }

  private static String stripExtension(String filename) {
    if (filename == null) {
      return "data-vault-model";
    }
    int dot = filename.lastIndexOf('.');
    return dot > 0 ? filename.substring(0, dot) : filename;
  }
}
