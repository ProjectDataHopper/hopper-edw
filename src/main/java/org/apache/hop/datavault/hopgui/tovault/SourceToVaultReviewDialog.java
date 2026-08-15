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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.apache.hop.core.Const;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.ProposedObjectKind;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.ProposedVaultObject;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceTableRole;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceToVaultClassification;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceToVaultClassifier;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceToVaultOptions;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceToVaultProposal;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;

/** Review-and-apply screen for source-model → raw Data Vault proposals. */
public class SourceToVaultReviewDialog {

  private static final Class<?> PKG = SourceToVaultReviewDialog.class;

  public enum Destination {
    NEW_MODEL,
    EXISTING_MODEL,
    CURRENT_MODEL
  }

  private final Shell parent;
  private final IVariables variables;
  private final SourceModel sourceModel;
  private final DataVaultModel existingVault;
  private final Collection<String> selectedTableNames;
  private final boolean chooseDestination;

  private Shell shell;
  private Button wCreateFkLinks;
  private Button wCreateHubSats;
  private Button wExcludeTechnical;
  private Button wExcludeFkColumns;
  private Button wCreateReferenceTables;
  private Button wCreateHierarchyLinks;
  private Button wCreateNaryLinks;
  private Button wIncludeNonTableSources;
  private Button wPublishCatalog;
  private Button wNewModel;
  private Button wExistingModel;
  private TableView wObjects;

  @Getter private boolean confirmed;
  @Getter private boolean publishToCatalog = true;
  @Getter private SourceToVaultClassification classification;
  @Getter private SourceToVaultOptions options = SourceToVaultOptions.defaults();
  @Getter private Destination destination = Destination.NEW_MODEL;

  public SourceToVaultReviewDialog(
      Shell parent,
      IVariables variables,
      SourceModel sourceModel,
      DataVaultModel existingVault,
      Collection<String> selectedTableNames,
      boolean chooseDestination) {
    this.parent = parent;
    this.variables = variables;
    this.sourceModel = sourceModel;
    this.existingVault = existingVault;
    this.selectedTableNames = selectedTableNames;
    this.chooseDestination = chooseDestination;
    this.destination = chooseDestination ? Destination.NEW_MODEL : Destination.CURRENT_MODEL;
    this.classification =
        SourceToVaultClassifier.classify(sourceModel, selectedTableNames, existingVault, options);
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Shell.Title"));
    shell.setLayout(new FormLayout());

    int margin = PropsUi.getMargin();
    DialogHelpSupport.createHelpButton(shell, HelpTopics.SOURCE_TO_VAULT_REVIEW);

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Apply.Label"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    wCreateFkLinks = optionCheckbox("CreateFkLinks", null);
    wCreateHubSats = optionCheckbox("CreateHubSats", wCreateFkLinks);
    wExcludeTechnical = optionCheckbox("ExcludeTechnical", wCreateHubSats);
    wExcludeFkColumns = optionCheckbox("ExcludeFkColumns", wExcludeTechnical);
    wCreateReferenceTables = optionCheckbox("CreateReferenceTables", wExcludeFkColumns);
    wCreateHierarchyLinks = optionCheckbox("CreateHierarchyLinks", wCreateReferenceTables);
    wCreateNaryLinks = optionCheckbox("CreateNaryLinks", wCreateHierarchyLinks);
    wIncludeNonTableSources = optionCheckbox("IncludeNonTableSources", wCreateNaryLinks);
    wPublishCatalog = optionCheckbox("PublishCatalog", wIncludeNonTableSources);
    wCreateFkLinks.setSelection(options.isCreateFkLinks());
    wCreateHubSats.setSelection(options.isCreateHubSatellites());
    wExcludeTechnical.setSelection(options.isExcludeTechnicalColumns());
    wExcludeFkColumns.setSelection(options.isExcludeFkColumnsFromSatellites());
    wCreateReferenceTables.setSelection(options.isCreateReferenceTables());
    wCreateHierarchyLinks.setSelection(options.isCreateHierarchyLinks());
    wCreateNaryLinks.setSelection(options.isCreateNaryLinksForMultiFkFeeds());
    wIncludeNonTableSources.setSelection(options.isIncludeNonTableSources());
    wPublishCatalog.setSelection(true);

    org.eclipse.swt.widgets.Control last = wPublishCatalog;
    if (chooseDestination) {
      wNewModel = new Button(shell, SWT.RADIO);
      PropsUi.setLook(wNewModel);
      wNewModel.setText(BaseMessages.getString(PKG, "SourceToVaultReviewDialog.NewModel.Label"));
      wNewModel.setSelection(true);
      FormData fdNew = new FormData();
      fdNew.left = new FormAttachment(0, 0);
      fdNew.top = new FormAttachment(wPublishCatalog, margin);
      wNewModel.setLayoutData(fdNew);

      wExistingModel = new Button(shell, SWT.RADIO);
      PropsUi.setLook(wExistingModel);
      wExistingModel.setText(
          BaseMessages.getString(PKG, "SourceToVaultReviewDialog.ExistingModel.Label"));
      FormData fdExisting = new FormData();
      fdExisting.left = new FormAttachment(wNewModel, margin * 2);
      fdExisting.top = new FormAttachment(wPublishCatalog, margin);
      wExistingModel.setLayoutData(fdExisting);
      last = wNewModel;
    }

    Label wlObjects = new Label(shell, SWT.LEFT);
    PropsUi.setLook(wlObjects);
    wlObjects.setText(BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Objects.Label"));
    FormData fdlObjects = new FormData();
    fdlObjects.left = new FormAttachment(0, 0);
    fdlObjects.top = new FormAttachment(last, margin);
    wlObjects.setLayoutData(fdlObjects);

    String[] yn = new String[] {"Y", "N"};
    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Column.Include"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              yn),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Column.Source"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Column.Kind"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Column.Name"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Column.Details"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Column.Confidence"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Column.Evidence"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false,
              true),
        };

    wObjects =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            columns,
            1,
            null,
            PropsUi.getInstance());
    FormData fdObjects = new FormData();
    fdObjects.left = new FormAttachment(0, 0);
    fdObjects.top = new FormAttachment(wlObjects, margin);
    fdObjects.right = new FormAttachment(100, 0);
    fdObjects.bottom = new FormAttachment(wOk, -margin);
    wObjects.setLayoutData(fdObjects);

    wCreateFkLinks.addListener(SWT.Selection, e -> reclassify());
    wCreateHubSats.addListener(SWT.Selection, e -> reclassify());
    wExcludeTechnical.addListener(SWT.Selection, e -> reclassify());
    wExcludeFkColumns.addListener(SWT.Selection, e -> reclassify());
    wCreateReferenceTables.addListener(SWT.Selection, e -> reclassify());
    wCreateHierarchyLinks.addListener(SWT.Selection, e -> reclassify());
    wCreateNaryLinks.addListener(SWT.Selection, e -> reclassify());
    wIncludeNonTableSources.addListener(SWT.Selection, e -> reclassify());

    populateTable();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return confirmed;
  }

  private Button optionCheckbox(String key, org.eclipse.swt.widgets.Control above) {
    Button button = new Button(shell, SWT.CHECK);
    PropsUi.setLook(button);
    button.setText(BaseMessages.getString(PKG, "SourceToVaultReviewDialog." + key + ".Label"));
    button.setToolTipText(
        BaseMessages.getString(PKG, "SourceToVaultReviewDialog." + key + ".ToolTip"));
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top =
        above == null
            ? new FormAttachment(0, PropsUi.getMargin())
            : new FormAttachment(above, PropsUi.getMargin() / 2);
    button.setLayoutData(fd);
    return button;
  }

  private void reclassify() {
    captureEdits();
    readOptionsFromWidgets();
    classification =
        SourceToVaultClassifier.classify(sourceModel, selectedTableNames, existingVault, options);
    populateTable();
  }

  private void populateTable() {
    wObjects.table.removeAll();
    if (classification == null) {
      return;
    }
    for (SourceToVaultProposal proposal : classification.getProposals()) {
      if (proposal == null) {
        continue;
      }
      if (proposal.getRole() == SourceTableRole.SKIP || proposal.getObjects().isEmpty()) {
        TableItem item = new TableItem(wObjects.table, SWT.NONE);
        item.setText(1, "N");
        item.setText(2, Const.NVL(proposal.getSourceTableName(), ""));
        item.setText(3, "SKIP");
        item.setText(4, "");
        item.setText(5, "");
        item.setText(6, proposal.getConfidence() != null ? proposal.getConfidence().name() : "");
        item.setText(7, Const.NVL(proposal.getSkipReason(), Const.NVL(proposal.getEvidence(), "")));
        continue;
      }
      for (ProposedVaultObject object : proposal.getObjects()) {
        if (object == null) {
          continue;
        }
        TableItem item = new TableItem(wObjects.table, SWT.NONE);
        item.setText(1, object.isIncluded() ? "Y" : "N");
        item.setText(2, Const.NVL(proposal.getSourceTableName(), ""));
        item.setText(3, object.getKind() != null ? object.getKind().name() : "");
        item.setText(4, Const.NVL(object.getName(), ""));
        item.setText(5, details(object));
        item.setText(6, proposal.getConfidence() != null ? proposal.getConfidence().name() : "");
        item.setText(7, Const.NVL(proposal.getEvidence(), ""));
        item.setData(object);
      }
    }
    wObjects.setRowNums();
    wObjects.optWidth(true);
  }

  private static String details(ProposedVaultObject object) {
    if (object.getKind() == ProposedObjectKind.HUB) {
      return sourceKindPrefix(object) + "BK " + String.join(", ", object.getBusinessKeyColumns());
    }
    if (object.getKind() == ProposedObjectKind.LINK) {
      return sourceKindPrefix(object) + String.join(", ", object.getParticipatingHubNames());
    }
    if (object.getKind() == ProposedObjectKind.SATELLITE) {
      String parent =
          object.getParentHubName() != null
              ? object.getParentHubName()
              : Const.NVL(object.getParentLinkName(), "");
      return sourceKindPrefix(object) + parent;
    }
    if (object.getKind() == ProposedObjectKind.REFERENCE) {
      return sourceKindPrefix(object) + "NK " + String.join(", ", object.getBusinessKeyColumns());
    }
    if (object.getKind() == ProposedObjectKind.LINKED_TABLE) {
      return "alias of " + Const.NVL(object.getReferencedTableName(), "");
    }
    return "";
  }

  private static String sourceKindPrefix(ProposedVaultObject object) {
    if (object.getSourceKind() == null || object.getSourceKind() == SourceEndpointKind.TABLE) {
      return "";
    }
    return object.getSourceKind().name() + " · ";
  }

  private void captureEdits() {
    if (wObjects == null || classification == null) {
      return;
    }
    for (int i = 0; i < wObjects.table.getItemCount(); i++) {
      TableItem item = wObjects.table.getItem(i);
      Object data = item.getData();
      if (!(data instanceof ProposedVaultObject object)) {
        continue;
      }
      object.setIncluded("Y".equalsIgnoreCase(item.getText(1)));
      String name = item.getText(4);
      if (name != null && !name.isBlank()) {
        object.setName(name.trim());
        object.setTableName(name.trim());
      }
    }
  }

  private void readOptionsFromWidgets() {
    options.setCreateFkLinks(wCreateFkLinks.getSelection());
    options.setCreateHubSatellites(wCreateHubSats.getSelection());
    options.setExcludeTechnicalColumns(wExcludeTechnical.getSelection());
    options.setExcludeFkColumnsFromSatellites(wExcludeFkColumns.getSelection());
    options.setCreateReferenceTables(wCreateReferenceTables.getSelection());
    options.setCreateHierarchyLinks(wCreateHierarchyLinks.getSelection());
    options.setCreateNaryLinksForMultiFkFeeds(wCreateNaryLinks.getSelection());
    options.setIncludeNonTableSources(wIncludeNonTableSources.getSelection());
  }

  private void ok() {
    captureEdits();
    readOptionsFromWidgets();
    if (chooseDestination) {
      destination =
          wExistingModel != null && wExistingModel.getSelection()
              ? Destination.EXISTING_MODEL
              : Destination.NEW_MODEL;
    } else {
      destination = Destination.CURRENT_MODEL;
    }
    publishToCatalog = wPublishCatalog.getSelection();
    confirmed = true;
    dispose();
  }

  private void cancel() {
    confirmed = false;
    dispose();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      shell.dispose();
    }
  }

  public List<String> warnings() {
    return classification != null ? new ArrayList<>(classification.getWarnings()) : List.of();
  }
}
