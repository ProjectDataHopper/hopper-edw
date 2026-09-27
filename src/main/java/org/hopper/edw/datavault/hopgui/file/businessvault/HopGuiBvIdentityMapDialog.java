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
package org.hopper.edw.datavault.hopgui.file.businessvault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelDialogValidationSupport;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvIdentityMap;
import org.hopper.edw.datavault.metadata.businessvault.BvIdentityMapMode;
import org.hopper.edw.datavault.metadata.businessvault.BvIdentityUnmappedPolicy;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;
import org.hopper.edw.datavault.naming.EdwNamingWidgetSupport;

/** Dialog for a Business Vault identity map. Buttons stay under the field list. */
public class HopGuiBvIdentityMapDialog {

  private static final Class<?> PKG = HopGuiBvIdentityMapDialog.class;

  private final Shell parent;
  private final BvIdentityMap input;
  private final BusinessVaultModel businessVaultModel;
  private final DataVaultModel dataVaultModel;
  private final IVariables variables;
  private final int originalTableIndex;

  private Shell shell;
  private Text wName;
  private Text wTableName;
  private Text wDescription;
  private Text wParentHub;
  private Text wLink;
  private Text wMaster;
  private Text wDuplicate;
  private Text wPreferred;
  private Text wMdm;
  private Text wEffectivitySatellite;
  private Text wEffectivityFrom;
  private Text wEffectivityTo;
  private Combo wUnmapped;
  private Combo wMode;
  private boolean ok;

  public HopGuiBvIdentityMapDialog(
      Shell parent,
      BvIdentityMap identityMap,
      BusinessVaultModel businessVaultModel,
      DataVaultModel dataVaultModel,
      IVariables variables) {
    this.parent = parent;
    this.input = identityMap;
    this.businessVaultModel = businessVaultModel;
    this.dataVaultModel = dataVaultModel;
    this.variables = variables;
    this.originalTableIndex =
        businessVaultModel != null ? businessVaultModel.getTables().indexOf(identityMap) : -1;
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG, "HopGuiBvIdentityMapDialog.Title", Const.NVL(input.getName(), "")));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);
    int margin = PropsUi.getMargin();
    int middle = 35;

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(
        BaseMessages.getString(
            ModelDialogValidationSupport.class, "ModelTableDialog.Validate.Label"));
    wValidate.addListener(SWT.Selection, e -> validate());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.BV_IDENTITY_MAP);
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel}, margin, null);

    ScrolledComposite scrolled = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL);
    scrolled.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right().bottom(wOk, -margin).result());
    Composite fields = new Composite(scrolled, SWT.NONE);
    PropsUi.setLook(fields);
    fields.setLayout(new FormLayout());

    Control previous = null;
    wName = text(fields, previous, "HopGuiBvIdentityMapDialog.Name.Label", middle, margin);
    EdwNamingWidgetSupport.enableAndLayout(
        wName, variables, EdwNamingSchemeTypes.BV_IDENTITY_MAP, (org.eclipse.swt.layout.FormData)
            wName.getLayoutData());
    previous = wName;
    wTableName = text(fields, previous, "HopGuiBvIdentityMapDialog.TableName.Label", middle, margin);
    previous = wTableName;
    wDescription =
        text(fields, previous, "HopGuiBvIdentityMapDialog.Description.Label", middle, margin);
    previous = wDescription;
    wParentHub = text(fields, previous, "HopGuiBvIdentityMapDialog.ParentHub.Label", middle, margin);
    previous = wParentHub;
    wLink = text(fields, previous, "HopGuiBvIdentityMapDialog.Link.Label", middle, margin);
    previous = wLink;
    wMaster = text(fields, previous, "HopGuiBvIdentityMapDialog.Master.Label", middle, margin);
    previous = wMaster;
    wDuplicate = text(fields, previous, "HopGuiBvIdentityMapDialog.Duplicate.Label", middle, margin);
    previous = wDuplicate;
    wPreferred = text(fields, previous, "HopGuiBvIdentityMapDialog.Preferred.Label", middle, margin);
    previous = wPreferred;
    wMdm = text(fields, previous, "HopGuiBvIdentityMapDialog.Mdm.Label", middle, margin);
    previous = wMdm;
    wEffectivitySatellite =
        text(fields, previous, "HopGuiBvIdentityMapDialog.EffectivitySatellite.Label", middle, margin);
    previous = wEffectivitySatellite;
    wEffectivityFrom =
        text(fields, previous, "HopGuiBvIdentityMapDialog.EffectivityFrom.Label", middle, margin);
    previous = wEffectivityFrom;
    wEffectivityTo =
        text(fields, previous, "HopGuiBvIdentityMapDialog.EffectivityTo.Label", middle, margin);
    previous = wEffectivityTo;
    wUnmapped = combo(fields, previous, "HopGuiBvIdentityMapDialog.Unmapped.Label", middle, margin);
    wUnmapped.setItems(new String[] {"self", "quarantine", "drop"});
    previous = wUnmapped;
    wMode = combo(fields, previous, "HopGuiBvIdentityMapDialog.Mode.Label", middle, margin);
    wMode.setItems(new String[] {"generated", "external"});

    fields.pack();
    scrolled.setContent(fields);
    scrolled.setExpandHorizontal(true);
    scrolled.setExpandVertical(true);
    scrolled.setMinSize(fields.computeSize(SWT.DEFAULT, SWT.DEFAULT));

    getData();
    BaseTransformDialog.setSize(shell, 720, 640);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  private Text text(Composite parent, Control above, String labelKey, int middle, int margin) {
    Label label = new Label(parent, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(label);
    label.setLayoutData(attached(above, middle, margin, true));
    Text text = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    text.setLayoutData(attached(above, middle, margin, false));
    return text;
  }

  private Combo combo(Composite parent, Control above, String labelKey, int middle, int margin) {
    Label label = new Label(parent, SWT.RIGHT);
    label.setText(BaseMessages.getString(PKG, labelKey));
    PropsUi.setLook(label);
    label.setLayoutData(attached(above, middle, margin, true));
    Combo combo = new Combo(parent, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(combo);
    combo.setLayoutData(attached(above, middle, margin, false));
    return combo;
  }

  private static org.eclipse.swt.layout.FormData attached(
      Control above, int middle, int margin, boolean label) {
    FormDataBuilder builder = new FormDataBuilder();
    if (label) {
      builder.left().right(middle, -margin);
    } else {
      builder.left(middle, 0).right();
    }
    if (above == null) {
      builder.top(0, margin);
    } else {
      builder.top(above, margin);
    }
    return builder.result();
  }

  private void getData() {
    set(wName, input.getName());
    set(wTableName, input.getTableName());
    set(wDescription, input.getDescription());
    set(wParentHub, input.getParentHubName());
    set(wLink, input.getSameAsLinkName());
    set(wMaster, input.getMasterHashField());
    set(wDuplicate, input.getDuplicateHashField());
    set(wPreferred, input.getPreferredBusinessKeyField());
    set(wMdm, input.getMdmIdField());
    set(wEffectivitySatellite, input.getEffectivitySatelliteName());
    set(wEffectivityFrom, input.getEffectivityFromField());
    set(wEffectivityTo, input.getEffectivityToField());
    wUnmapped.setText(input.getUnmappedPolicyOrDefault().getCode());
    wMode.setText(input.getMapModeOrDefault().getCode());
  }

  private static void set(Text text, String value) {
    if (!Utils.isEmpty(value)) {
      text.setText(value);
    }
  }

  private void ok() {
    String newName = Const.trim(wName.getText());
    if (Utils.isEmpty(newName)) {
      showWarning(
          "HopGuiBvIdentityMapDialog.NameRequired.Title",
          BaseMessages.getString(PKG, "HopGuiBvIdentityMapDialog.NameRequired.Message"));
      return;
    }
    if (businessVaultModel != null && businessVaultModel.hasOtherTableNamed(input, newName)) {
      showWarning(
          "HopGuiBvIdentityMapDialog.DuplicateName.Title",
          BaseMessages.getString(PKG, "HopGuiBvIdentityMapDialog.DuplicateName.Message", newName));
      return;
    }
    apply(input);
    ok = true;
    dispose();
  }

  private void apply(IBvTable target) {
    target.setName(wName.getText());
    target.setTableName(wTableName.getText());
    target.setDescription(wDescription.getText());
    if (!(target instanceof BvIdentityMap identityMap)) {
      return;
    }
    identityMap.setParentHubName(Const.trim(wParentHub.getText()));
    identityMap.setSameAsLinkName(Const.trim(wLink.getText()));
    identityMap.setMasterHashField(Const.trim(wMaster.getText()));
    identityMap.setDuplicateHashField(Const.trim(wDuplicate.getText()));
    identityMap.setPreferredBusinessKeyField(Const.trim(wPreferred.getText()));
    identityMap.setMdmIdField(Const.trim(wMdm.getText()));
    identityMap.setEffectivitySatelliteName(Const.trim(wEffectivitySatellite.getText()));
    identityMap.setEffectivityFromField(Const.trim(wEffectivityFrom.getText()));
    identityMap.setEffectivityToField(Const.trim(wEffectivityTo.getText()));
    identityMap.setUnmappedPolicy(BvIdentityUnmappedPolicy.lookupCode(wUnmapped.getText()));
    identityMap.setMapMode(BvIdentityMapMode.lookupCode(wMode.getText()));
  }

  private void validate() {
    try {
      List<ICheckResult> remarks =
          ModelDialogValidationSupport.runChecksWithBusyCursor(
              shell,
              () -> {
                BusinessVaultModel draft =
                    ModelDialogValidationSupport.cloneBusinessVaultModel(
                        businessVaultModel, HopGui.getInstance().getMetadataProvider());
                IBvTable draftTable = locateDraftTable(draft);
                apply(draftTable);
                List<ICheckResult> tableRemarks = new ArrayList<>();
                draftTable.check(
                    tableRemarks,
                    HopGui.getInstance().getMetadataProvider(),
                    variables,
                    draft,
                    dataVaultModel);
                return tableRemarks;
              });
      ModelDialogValidationSupport.showCheckResults(shell, remarks);
    } catch (Exception ex) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(
              ModelDialogValidationSupport.class, "ModelTableDialog.Validate.Label"),
          BaseMessages.getString(
              ModelDialogValidationSupport.class,
              "ModelTableDialog.Validate.Error",
              ex.getMessage()),
          ex);
    }
  }

  private IBvTable locateDraftTable(BusinessVaultModel draft) throws HopException {
    if (draft == null || originalTableIndex < 0 || originalTableIndex >= draft.getTables().size()) {
      throw new HopException("Unable to locate table in validation model");
    }
    return draft.getTables().get(originalTableIndex);
  }

  private void showWarning(String titleKey, String message) {
    MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
    box.setText(BaseMessages.getString(PKG, titleKey));
    box.setMessage(Const.NVL(message, ""));
    box.open();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    shell.dispose();
  }
}
