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
package org.hopper.edw.datavault.hopgui.tovault;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.ProposedObjectKind;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.ProposedVaultObject;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceTableRole;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultClassification;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultClassifier;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultNaming;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultOptions;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultProposal;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitOptions;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitSupport;

/** Review-and-apply screen for source-model → raw Data Vault proposals. */
public class SourceToVaultReviewDialog {

  private static final Class<?> PKG = SourceToVaultReviewDialog.class;

  public enum Destination {
    NEW_MODEL,
    EXISTING_MODEL,
    CURRENT_MODEL,
    SPLIT_MODELS
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
  private Button wSeedParentHubs;
  private Button wPublishCatalog;
  private Button wNewModel;
  private Button wExistingModel;
  private Button wCurrentModel;
  private Button wSplitModel;
  private TextVar wSplitFolder;
  private Button wSplitBrowse;
  private Text wSplitPrefix;
  private Combo wSplitExisting;
  private Label wSplitExample;
  private TableView wObjects;

  @Getter private boolean confirmed;
  @Getter private boolean publishToCatalog = true;
  @Getter private SourceToVaultClassification classification;
  @Getter private SourceToVaultOptions options = SourceToVaultOptions.defaults();
  @Getter private Destination destination = Destination.NEW_MODEL;
  @Getter private final SourceToVaultSplitOptions splitOptions = new SourceToVaultSplitOptions();

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
    this.classification = classifyWithNaming();
  }

  private SourceToVaultClassification classifyWithNaming() {
    SourceToVaultClassification[] box = new SourceToVaultClassification[1];
    IHopMetadataProvider provider =
        HopGui.getInstance() != null ? HopGui.getInstance().getMetadataProvider() : null;
    SourceToVaultNaming.runWithProvider(
        provider,
        () ->
            box[0] =
                SourceToVaultClassifier.classify(
                    sourceModel, selectedTableNames, existingVault, options));
    return box[0];
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
    wSeedParentHubs = optionCheckbox("SeedParentHubs", wIncludeNonTableSources);
    wPublishCatalog = optionCheckbox("PublishCatalog", wSeedParentHubs);
    wCreateFkLinks.setSelection(options.isCreateFkLinks());
    wCreateHubSats.setSelection(options.isCreateHubSatellites());
    wExcludeTechnical.setSelection(options.isExcludeTechnicalColumns());
    wExcludeFkColumns.setSelection(options.isExcludeFkColumnsFromSatellites());
    wCreateReferenceTables.setSelection(options.isCreateReferenceTables());
    wCreateHierarchyLinks.setSelection(options.isCreateHierarchyLinks());
    wCreateNaryLinks.setSelection(options.isCreateNaryLinksForMultiFkFeeds());
    wIncludeNonTableSources.setSelection(options.isIncludeNonTableSources());
    wSeedParentHubs.setSelection(options.isSeedParentHubsFromChildFeeds());
    wPublishCatalog.setSelection(true);

    Control last = addDestinationRadios(wPublishCatalog, margin);
    last = addSplitFields(last, margin);

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
    updateSplitEnabled();
    BaseTransformDialog.setSize(shell, 980, 760);
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return confirmed;
  }

  private Control addDestinationRadios(Control above, int margin) {
    org.eclipse.swt.widgets.Listener toggle =
        e -> {
          updateSplitEnabled();
          refreshSplitExample();
        };
    if (chooseDestination) {
      wNewModel = radio(above, margin, "SourceToVaultReviewDialog.NewModel.Label", true, 0);
      wExistingModel =
          radio(above, margin, "SourceToVaultReviewDialog.ExistingModel.Label", false, wNewModel);
      wNewModel.addListener(SWT.Selection, toggle);
      wExistingModel.addListener(SWT.Selection, toggle);
      above = wNewModel;
    } else {
      wCurrentModel =
          radio(above, margin, "SourceToVaultReviewDialog.CurrentModel.Label", true, 0);
      wCurrentModel.addListener(SWT.Selection, toggle);
      above = wCurrentModel;
    }
    wSplitModel = radio(above, margin, "SourceToVaultReviewDialog.SplitModels.Label", false, 0);
    wSplitModel.addListener(SWT.Selection, toggle);
    return wSplitModel;
  }

  private Button radio(Control above, int margin, String key, boolean selected, Object left) {
    Button button = new Button(shell, SWT.RADIO);
    PropsUi.setLook(button);
    button.setText(BaseMessages.getString(PKG, key));
    button.setSelection(selected);
    FormData fd = new FormData();
    if (left instanceof Button leftButton) {
      fd.left = new FormAttachment(leftButton, margin * 2);
      fd.top = new FormAttachment(above, margin);
    } else {
      fd.left = new FormAttachment(0, 0);
      fd.top = new FormAttachment(above, margin);
    }
    button.setLayoutData(fd);
    return button;
  }

  private Control addSplitFields(Control above, int margin) {
    int middle = PropsUi.getInstance().getMiddlePct();

    Label wlFolder = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlFolder);
    wlFolder.setText(BaseMessages.getString(PKG, "SourceToVaultReviewDialog.SplitFolder.Label"));
    FormData fdlFolder = new FormData();
    fdlFolder.left = new FormAttachment(0, 0);
    fdlFolder.right = new FormAttachment(middle, -margin);
    fdlFolder.top = new FormAttachment(above, margin);
    wlFolder.setLayoutData(fdlFolder);

    wSplitBrowse = new Button(shell, SWT.PUSH);
    wSplitBrowse.setText(BaseMessages.getString(PKG, "SourceToVaultReviewDialog.SplitBrowse.Label"));
    FormData fdBrowse = new FormData();
    fdBrowse.right = new FormAttachment(100, 0);
    fdBrowse.top = new FormAttachment(above, margin);
    wSplitBrowse.setLayoutData(fdBrowse);
    wSplitBrowse.addListener(
        SWT.Selection,
        e -> {
          String folder =
              BaseDialog.presentDirectoryDialog(shell, wSplitFolder.getText(), null, variables);
          if (!Utils.isEmpty(folder)) {
            wSplitFolder.setText(folder);
            refreshSplitExample();
          }
        });

    wSplitFolder = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSplitFolder);
    wSplitFolder.setText(defaultSplitFolder());
    wSplitFolder.setToolTipText(
        BaseMessages.getString(PKG, "SourceToVaultReviewDialog.SplitFolder.ToolTip"));
    FormData fdFolder = new FormData();
    fdFolder.left = new FormAttachment(middle, 0);
    fdFolder.right = new FormAttachment(wSplitBrowse, -margin);
    fdFolder.top = new FormAttachment(above, margin);
    wSplitFolder.setLayoutData(fdFolder);

    Label wlPrefix = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlPrefix);
    wlPrefix.setText(BaseMessages.getString(PKG, "SourceToVaultReviewDialog.SplitPrefix.Label"));
    FormData fdlPrefix = new FormData();
    fdlPrefix.left = new FormAttachment(0, 0);
    fdlPrefix.right = new FormAttachment(middle, -margin);
    fdlPrefix.top = new FormAttachment(wSplitFolder, margin);
    wlPrefix.setLayoutData(fdlPrefix);

    wSplitPrefix = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSplitPrefix);
    wSplitPrefix.setToolTipText(
        BaseMessages.getString(PKG, "SourceToVaultReviewDialog.SplitPrefix.ToolTip"));
    FormData fdPrefix = new FormData();
    fdPrefix.left = new FormAttachment(middle, 0);
    fdPrefix.right = new FormAttachment(100, 0);
    fdPrefix.top = new FormAttachment(wSplitFolder, margin);
    wSplitPrefix.setLayoutData(fdPrefix);
    wSplitPrefix.addListener(SWT.Modify, e -> refreshSplitExample());

    Label wlExisting = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlExisting);
    wlExisting.setText(
        BaseMessages.getString(PKG, "SourceToVaultReviewDialog.SplitExisting.Label"));
    FormData fdlExisting = new FormData();
    fdlExisting.left = new FormAttachment(0, 0);
    fdlExisting.right = new FormAttachment(middle, -margin);
    fdlExisting.top = new FormAttachment(wSplitPrefix, margin);
    wlExisting.setLayoutData(fdlExisting);

    wSplitExisting = new Combo(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wSplitExisting);
    wSplitExisting.setItems(
        new String[] {
          BaseMessages.getString(PKG, "SourceToVaultReviewDialog.SplitExisting.Skip"),
          BaseMessages.getString(PKG, "SourceToVaultReviewDialog.SplitExisting.Replace")
        });
    wSplitExisting.select(0);
    wSplitExisting.setToolTipText(
        BaseMessages.getString(PKG, "SourceToVaultReviewDialog.SplitExisting.ToolTip"));
    FormData fdExisting = new FormData();
    fdExisting.left = new FormAttachment(middle, 0);
    fdExisting.right = new FormAttachment(100, 0);
    fdExisting.top = new FormAttachment(wSplitPrefix, margin);
    wSplitExisting.setLayoutData(fdExisting);

    wSplitExample = new Label(shell, SWT.LEFT);
    PropsUi.setLook(wSplitExample);
    wSplitExample.setText("");
    FormData fdExample = new FormData();
    fdExample.left = new FormAttachment(middle, 0);
    fdExample.right = new FormAttachment(100, 0);
    fdExample.top = new FormAttachment(wSplitExisting, margin);
    wSplitExample.setLayoutData(fdExample);
    return wSplitExample;
  }

  private String defaultSplitFolder() {
    try {
      String filename = sourceModel != null ? sourceModel.getFilename() : null;
      if (!Utils.isEmpty(filename)) {
        String resolved = variables != null ? variables.resolve(filename) : filename;
        var parent = HopVfs.getFileObject(resolved).getParent();
        if (parent != null) {
          return HopVfs.getFilename(parent);
        }
      }
    } catch (Exception ignored) {
      // Fall through to the project models folder.
    }
    if (variables != null) {
      String home = variables.getVariable("PROJECT_HOME");
      if (!Utils.isEmpty(home) && !home.contains("${")) {
        return "${PROJECT_HOME}/models";
      }
    }
    return "";
  }

  private void updateSplitEnabled() {
    boolean enabled = wSplitModel != null && wSplitModel.getSelection();
    if (wSplitFolder != null) {
      wSplitFolder.setEnabled(enabled);
    }
    if (wSplitBrowse != null) {
      wSplitBrowse.setEnabled(enabled);
    }
    if (wSplitPrefix != null) {
      wSplitPrefix.setEnabled(enabled);
    }
    if (wSplitExisting != null) {
      wSplitExisting.setEnabled(enabled);
    }
    if (wSplitExample != null) {
      wSplitExample.setEnabled(enabled);
      if (!enabled) {
        wSplitExample.setText("");
      }
    }
  }

  private void refreshSplitExample() {
    if (wSplitExample == null || wSplitModel == null || !wSplitModel.getSelection()) {
      return;
    }
    try {
      List<String> names = new ArrayList<>();
      String hub = firstIncludedName("HUB");
      String link = firstIncludedName("LINK");
      IHopMetadataProvider provider = metadataProvider();
      if (!Utils.isEmpty(hub)) {
        names.add(
            SourceToVaultSplitSupport.fileBaseName(hub, wSplitPrefix.getText(), provider)
                + ".hdv");
      }
      if (!Utils.isEmpty(link)) {
        names.add(
            SourceToVaultSplitSupport.fileBaseName(link, wSplitPrefix.getText(), provider)
                + ".hdv");
      }
      if (names.isEmpty()) {
        wSplitExample.setText(
            BaseMessages.getString(PKG, "SourceToVaultReviewDialog.SplitExample.Empty"));
      } else {
        wSplitExample.setText(
            BaseMessages.getString(
                PKG, "SourceToVaultReviewDialog.SplitExample.Label", String.join(", ", names)));
      }
    } catch (HopException e) {
      wSplitExample.setText(e.getMessage());
    }
  }

  private String firstIncludedName(String kind) {
    if (wObjects == null || wObjects.table == null) {
      return null;
    }
    for (int i = 0; i < wObjects.table.getItemCount(); i++) {
      TableItem item = wObjects.table.getItem(i);
      if (kind.equals(item.getText(3)) && "Y".equalsIgnoreCase(item.getText(1))) {
        String name = item.getText(4);
        if (!Utils.isEmpty(name)) {
          return name.trim();
        }
      }
    }
    return null;
  }

  private IHopMetadataProvider metadataProvider() {
    return HopGui.getInstance() != null ? HopGui.getInstance().getMetadataProvider() : null;
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
    classification = classifyWithNaming();
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
    refreshSplitExample();
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
    options.setSeedParentHubsFromChildFeeds(wSeedParentHubs.getSelection());
  }

  private void ok() {
    captureEdits();
    readOptionsFromWidgets();
    if (wSplitModel != null && wSplitModel.getSelection()) {
      if (Utils.isEmpty(wSplitFolder.getText())) {
        error(
            BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Error.FolderRequired"));
        return;
      }
      try {
        SourceToVaultSplitSupport.normalizePrefix(wSplitPrefix.getText());
      } catch (HopException e) {
        error(BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Error.Prefix"));
        return;
      }
      destination = Destination.SPLIT_MODELS;
      splitOptions.setOutputFolder(wSplitFolder.getText().trim());
      splitOptions.setNamePrefix(wSplitPrefix.getText());
      splitOptions.setExistingFilePolicy(
          wSplitExisting.getSelectionIndex() == 1
              ? SourceToVaultSplitOptions.ExistingFilePolicy.REPLACE
              : SourceToVaultSplitOptions.ExistingFilePolicy.SKIP);
    } else if (chooseDestination) {
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

  private void error(String message) {
    MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
    box.setText(BaseMessages.getString(PKG, "SourceToVaultReviewDialog.Error.Title"));
    box.setMessage(message);
    box.open();
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
