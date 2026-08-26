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
package org.hopper.edw.datavault.hopgui.file.lineageview;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.lineage.LineageLayer;
import org.hopper.edw.datavault.lineageview.HopLineageViewDocument;
import org.hopper.edw.datavault.lineageview.LineageBackendSelectionSupport;
import org.hopper.edw.datavault.lineageview.backend.LineageDirection;
import org.hopper.edw.datavault.lineageview.backend.LineageGraphLayer;
import org.hopper.edw.datavault.lineageview.backend.LineageSeedKind;

/** New-wizard and settings dialog for a lineage view definition. */
public class LineageViewSettingsDialog {

  private static final Class<?> PKG = HopGuiLineageViewGraph.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final HopLineageViewDocument document;
  private final boolean createMode;

  private Shell shell;
  private Combo wBackend;
  private Combo wSeedKind;
  private Combo wModelLayer;
  private Text wModelName;
  private Text wLogicalTable;
  private Text wModelFilename;
  private Text wDatasetNamespace;
  private Text wDatasetName;
  private Text wJobNamespace;
  private Text wJobName;
  private Text wResourceGroup;
  private Combo wDirection;
  private Spinner wDepth;
  private Button wIncludeJobs;
  private Button wIncludeOps;
  private Button wLayerSource;
  private Button wLayerDv;
  private Button wLayerBv;
  private Button wLayerDm;
  private boolean ok;

  public LineageViewSettingsDialog(
      Shell parent, HopGui hopGui, HopLineageViewDocument document, boolean createMode) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.document = document;
    this.createMode = createMode;
  }

  public boolean open() {
    shell =
        new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX | SWT.APPLICATION_MODAL);
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(
            PKG,
            createMode
                ? "LineageViewSettingsDialog.New.Title"
                : "LineageViewSettingsDialog.Edit.Title"));
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int middle = PropsUi.getInstance().getMiddlePct();
    int margin = PropsUi.getMargin();

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(BaseDialog.class, "System.Button.OK"));
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(BaseDialog.class, "System.Button.Cancel"));
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.installLocalHelpButton(shell, HelpTopics.LINEAGE_VIEW_SETTINGS);

    ScrolledComposite scrolled = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL);
    PropsUi.setLook(scrolled);
    scrolled.setExpandHorizontal(true);
    scrolled.setExpandVertical(true);
    FormData fdScrolled = new FormData();
    fdScrolled.left = new FormAttachment(0, 0);
    fdScrolled.top = new FormAttachment(0, 0);
    fdScrolled.right = new FormAttachment(100, 0);
    fdScrolled.bottom = new FormAttachment(wOk, -margin);
    scrolled.setLayoutData(fdScrolled);

    Composite content = new Composite(scrolled, SWT.NONE);
    PropsUi.setLook(content);
    FormLayout contentLayout = new FormLayout();
    contentLayout.marginWidth = margin;
    contentLayout.marginHeight = margin;
    content.setLayout(contentLayout);
    scrolled.setContent(content);

    wBackend = addCombo(content, "LineageViewSettingsDialog.Backend.Label", null, middle, margin);
    wSeedKind =
        addCombo(content, "LineageViewSettingsDialog.SeedKind.Label", wBackend, middle, margin);
    wModelLayer =
        addCombo(content, "LineageViewSettingsDialog.ModelLayer.Label", wSeedKind, middle, margin);
    wModelName =
        addText(content, "LineageViewSettingsDialog.ModelName.Label", wModelLayer, middle, margin);
    wLogicalTable =
        addText(
            content, "LineageViewSettingsDialog.LogicalTable.Label", wModelName, middle, margin);
    wModelFilename =
        addText(
            content,
            "LineageViewSettingsDialog.ModelFilename.Label",
            wLogicalTable,
            middle,
            margin);
    wDatasetNamespace =
        addText(
            content,
            "LineageViewSettingsDialog.DatasetNamespace.Label",
            wModelFilename,
            middle,
            margin);
    wDatasetName =
        addText(
            content,
            "LineageViewSettingsDialog.DatasetName.Label",
            wDatasetNamespace,
            middle,
            margin);
    wJobNamespace =
        addText(
            content, "LineageViewSettingsDialog.JobNamespace.Label", wDatasetName, middle, margin);
    wJobName =
        addText(content, "LineageViewSettingsDialog.JobName.Label", wJobNamespace, middle, margin);
    wResourceGroup =
        addText(content, "LineageViewSettingsDialog.ResourceGroup.Label", wJobName, middle, margin);
    wDirection =
        addCombo(
            content, "LineageViewSettingsDialog.Direction.Label", wResourceGroup, middle, margin);

    Label wlDepth = label(content, "LineageViewSettingsDialog.Depth.Label");
    FormData fdlDepth = new FormData();
    fdlDepth.left = new FormAttachment(0, 0);
    fdlDepth.right = new FormAttachment(middle, -margin);
    fdlDepth.top = new FormAttachment(wDirection, margin);
    wlDepth.setLayoutData(fdlDepth);
    wDepth = new Spinner(content, SWT.BORDER);
    PropsUi.setLook(wDepth);
    wDepth.setMinimum(1);
    wDepth.setMaximum(20);
    FormData fdDepth = new FormData();
    fdDepth.left = new FormAttachment(middle, 0);
    fdDepth.top = new FormAttachment(wlDepth, 0, SWT.CENTER);
    wDepth.setLayoutData(fdDepth);

    wIncludeJobs =
        check(content, "LineageViewSettingsDialog.IncludeJobs.Label", wDepth, middle, margin);
    wIncludeOps =
        check(content, "LineageViewSettingsDialog.IncludeOps.Label", wIncludeJobs, middle, margin);

    Label wlLayers = label(content, "LineageViewSettingsDialog.Layers.Label");
    FormData fdlLayers = new FormData();
    fdlLayers.left = new FormAttachment(0, 0);
    fdlLayers.right = new FormAttachment(middle, -margin);
    fdlLayers.top = new FormAttachment(wIncludeOps, margin);
    wlLayers.setLayoutData(fdlLayers);
    wLayerSource = new Button(content, SWT.CHECK);
    wLayerDv = new Button(content, SWT.CHECK);
    wLayerBv = new Button(content, SWT.CHECK);
    wLayerDm = new Button(content, SWT.CHECK);
    wLayerSource.setText(BaseMessages.getString(PKG, "LineageViewSettingsDialog.Layer.SOURCE"));
    wLayerDv.setText(BaseMessages.getString(PKG, "LineageViewSettingsDialog.Layer.DV"));
    wLayerBv.setText(BaseMessages.getString(PKG, "LineageViewSettingsDialog.Layer.BV"));
    wLayerDm.setText(BaseMessages.getString(PKG, "LineageViewSettingsDialog.Layer.DM"));
    PropsUi.setLook(wLayerSource);
    PropsUi.setLook(wLayerDv);
    PropsUi.setLook(wLayerBv);
    PropsUi.setLook(wLayerDm);
    FormData fdLs = new FormData();
    fdLs.left = new FormAttachment(middle, 0);
    fdLs.top = new FormAttachment(wlLayers, 0, SWT.CENTER);
    wLayerSource.setLayoutData(fdLs);
    FormData fdLd = new FormData();
    fdLd.left = new FormAttachment(wLayerSource, margin);
    fdLd.top = new FormAttachment(wlLayers, 0, SWT.CENTER);
    wLayerDv.setLayoutData(fdLd);
    FormData fdLb = new FormData();
    fdLb.left = new FormAttachment(wLayerDv, margin);
    fdLb.top = new FormAttachment(wlLayers, 0, SWT.CENTER);
    wLayerBv.setLayoutData(fdLb);
    FormData fdLm = new FormData();
    fdLm.left = new FormAttachment(wLayerBv, margin);
    fdLm.top = new FormAttachment(wlLayers, 0, SWT.CENTER);
    wLayerDm.setLayoutData(fdLm);

    populateCombos();
    setWidgets();
    wSeedKind.addListener(SWT.Selection, e -> updateSeedFields());
    updateSeedFields();

    content.layout(true, true);
    scrolled.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    int zoom = Math.max(1, (int) Math.round(PropsUi.getNativeZoomFactor()));
    shell.setSize(720 * zoom, 560 * zoom);
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return ok;
  }

  private void populateCombos() {
    wSeedKind.setItems(
        new String[] {
          LineageSeedKind.MODEL_TABLE.getCode(),
          LineageSeedKind.DATASET.getCode(),
          LineageSeedKind.JOB.getCode()
        });
    wDirection.setItems(
        new String[] {
          LineageDirection.UPSTREAM.getCode(),
          LineageDirection.DOWNSTREAM.getCode(),
          LineageDirection.BOTH.getCode()
        });
    wModelLayer.setItems(new String[] {"DV", "BV", "DM"});
    List<String> backends = new ArrayList<>();
    try {
      backends.addAll(LineageBackendSelectionSupport.listNames(hopGui.getMetadataProvider()));
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "LineageViewSettingsDialog.Backend.Error.Title"),
          BaseMessages.getString(PKG, "LineageViewSettingsDialog.Backend.Error.Message"),
          e);
    }
    wBackend.setItems(backends.toArray(String[]::new));
  }

  private void setWidgets() {
    String backendName = Const.NVL(document.getBackendName(), "");
    if (Utils.isEmpty(backendName)) {
      try {
        backendName =
            Const.NVL(
                LineageBackendSelectionSupport.defaultBackendName(
                    hopGui.getMetadataProvider(), null),
                "");
      } catch (Exception ignored) {
        backendName = "";
      }
    }
    wBackend.setText(backendName);
    wSeedKind.setText(
        document.getSeedKind() != null
            ? document.getSeedKind().getCode()
            : LineageSeedKind.MODEL_TABLE.getCode());
    wModelLayer.setText(document.getModelLayer() != null ? document.getModelLayer().name() : "DM");
    wModelName.setText(Const.NVL(document.getModelName(), ""));
    wLogicalTable.setText(Const.NVL(document.getLogicalTable(), ""));
    wModelFilename.setText(Const.NVL(document.getModelFilename(), ""));
    wDatasetNamespace.setText(Const.NVL(document.getDatasetNamespace(), ""));
    wDatasetName.setText(Const.NVL(document.getDatasetName(), ""));
    wJobNamespace.setText(Const.NVL(document.getJobNamespace(), ""));
    wJobName.setText(Const.NVL(document.getJobName(), ""));
    wResourceGroup.setText(Const.NVL(document.getResourceGroup(), ""));
    wDirection.setText(
        document.getDirection() != null
            ? document.getDirection().getCode()
            : LineageDirection.UPSTREAM.getCode());
    wDepth.setSelection(document.getDepth() > 0 ? document.getDepth() : 6);
    wIncludeJobs.setSelection(document.isIncludeJobs());
    wIncludeOps.setSelection(document.isIncludeOpsOverlay());
    List<LineageGraphLayer> layers = document.getLayerFiltersOrEmpty();
    boolean all = layers.isEmpty();
    wLayerSource.setSelection(all || layers.contains(LineageGraphLayer.SOURCE));
    wLayerDv.setSelection(all || layers.contains(LineageGraphLayer.DV));
    wLayerBv.setSelection(all || layers.contains(LineageGraphLayer.BV));
    wLayerDm.setSelection(all || layers.contains(LineageGraphLayer.DM));
  }

  private void updateSeedFields() {
    LineageSeedKind kind = LineageSeedKind.valueOf(safeEnum(wSeedKind.getText(), "MODEL_TABLE"));
    boolean model = kind == LineageSeedKind.MODEL_TABLE;
    boolean dataset = kind == LineageSeedKind.DATASET;
    boolean job = kind == LineageSeedKind.JOB;
    setEnabled(wModelLayer, model);
    setEnabled(wModelName, model);
    setEnabled(wLogicalTable, model);
    setEnabled(wModelFilename, model);
    setEnabled(wDatasetNamespace, dataset || model);
    setEnabled(wDatasetName, dataset || model);
    setEnabled(wJobNamespace, job || model);
    setEnabled(wJobName, job || model);
  }

  private void ok() {
    if (Utils.isEmpty(wBackend.getText())) {
      warn("LineageViewSettingsDialog.Validate.Backend");
      return;
    }
    LineageSeedKind kind = LineageSeedKind.valueOf(safeEnum(wSeedKind.getText(), "MODEL_TABLE"));
    if (kind == LineageSeedKind.MODEL_TABLE && Utils.isEmpty(wLogicalTable.getText())) {
      warn("LineageViewSettingsDialog.Validate.LogicalTable");
      return;
    }
    if (kind == LineageSeedKind.DATASET && Utils.isEmpty(wDatasetName.getText())) {
      warn("LineageViewSettingsDialog.Validate.Dataset");
      return;
    }
    if (kind == LineageSeedKind.JOB && Utils.isEmpty(wJobName.getText())) {
      warn("LineageViewSettingsDialog.Validate.Job");
      return;
    }
    document.setBackendName(wBackend.getText());
    document.setSeedKind(LineageSeedKind.valueOf(safeEnum(wSeedKind.getText(), "MODEL_TABLE")));
    try {
      document.setModelLayer(LineageLayer.valueOf(safeEnum(wModelLayer.getText(), "DM")));
    } catch (IllegalArgumentException e) {
      document.setModelLayer(LineageLayer.DM);
    }
    document.setModelName(wModelName.getText());
    document.setLogicalTable(wLogicalTable.getText());
    document.setModelFilename(wModelFilename.getText());
    document.setDatasetNamespace(wDatasetNamespace.getText());
    document.setDatasetName(wDatasetName.getText());
    document.setJobNamespace(wJobNamespace.getText());
    document.setJobName(wJobName.getText());
    document.setResourceGroup(wResourceGroup.getText());
    document.setDirection(LineageDirection.valueOf(safeEnum(wDirection.getText(), "UPSTREAM")));
    document.setDepth(wDepth.getSelection());
    document.setIncludeJobs(wIncludeJobs.getSelection());
    document.setIncludeOpsOverlay(wIncludeOps.getSelection());
    List<LineageGraphLayer> selected = new ArrayList<>();
    if (wLayerSource.getSelection()) {
      selected.add(LineageGraphLayer.SOURCE);
    }
    if (wLayerDv.getSelection()) {
      selected.add(LineageGraphLayer.DV);
    }
    if (wLayerBv.getSelection()) {
      selected.add(LineageGraphLayer.BV);
    }
    if (wLayerDm.getSelection()) {
      selected.add(LineageGraphLayer.DM);
    }
    document.getLayerFiltersOrEmpty().clear();
    if (!selected.isEmpty() && selected.size() != 4) {
      document.getLayerFiltersOrEmpty().addAll(selected);
    }
    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      shell.dispose();
    }
  }

  private void warn(String key) {
    MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
    box.setText(BaseMessages.getString(PKG, "LineageViewSettingsDialog.Validate.Title"));
    box.setMessage(BaseMessages.getString(PKG, key));
    box.open();
  }

  private static String safeEnum(String value, String fallback) {
    return Utils.isEmpty(value) ? fallback : value.trim();
  }

  private static void setEnabled(Control control, boolean enabled) {
    if (control != null && !control.isDisposed()) {
      control.setEnabled(enabled);
    }
  }

  private Text addText(
      Composite parent, String labelKey, Control previous, int middle, int margin) {
    Label label = label(parent, labelKey);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top =
        previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
    label.setLayoutData(fdl);
    Text text = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(label, 0, SWT.CENTER);
    text.setLayoutData(fd);
    return text;
  }

  private Combo addCombo(
      Composite parent, String labelKey, Control previous, int middle, int margin) {
    Label label = label(parent, labelKey);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top =
        previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
    label.setLayoutData(fdl);
    Combo combo = new Combo(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(combo);
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(label, 0, SWT.CENTER);
    combo.setLayoutData(fd);
    return combo;
  }

  private Button check(
      Composite parent, String labelKey, Control previous, int middle, int margin) {
    Button button = new Button(parent, SWT.CHECK);
    PropsUi.setLook(button);
    button.setText(BaseMessages.getString(PKG, labelKey));
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.top = new FormAttachment(previous, margin);
    button.setLayoutData(fd);
    return button;
  }

  private Label label(Composite parent, String key) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, key));
    return label;
  }
}
