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

import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
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
import org.eclipse.swt.widgets.Text;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;

/** Dialog to configure ELK layout options before applying layout to a graph. */
public class ElkLayoutDialog {

  private static final Class<?> PKG = ElkLayoutDialog.class;

  private final Shell parent;
  private final ElkLayout initial;
  private Shell shell;
  private ElkLayout result;
  private boolean ok;
  private boolean saveAsDefault;

  private Combo wAlgorithm;
  private Text wTargetWidth;
  private Combo wDirection;
  private Text wSpacingWithinLayer;
  private Text wSpacingBetweenLayers;
  private Text wSpacingEdgeNode;
  private Combo wCrossingMinimization;
  private Combo wNodePlacement;
  private Combo wLayeringStrategy;
  private Combo wCycleBreaking;
  private Text wOriginX;
  private Text wOriginY;
  private Text wGridSize;
  private Text wMinNodeWidth;
  private Text wNodeHeight;
  private Button wSaveAsDefault;

  public ElkLayoutDialog(Shell parent, ElkLayout initial) {
    this.parent = parent;
    this.initial = initial != null ? new ElkLayout(initial) : ElkLayout.createDefault();
  }

  public boolean open() {
    shell = new Shell(parent, BaseDialog.getDefaultDialogStyle());
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "ElkLayoutDialog.Title"));
    shell.setImage(parent.getImage());

    FormLayout shellLayout = new FormLayout();
    shellLayout.marginWidth = PropsUi.getFormMargin();
    shellLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(shellLayout);

    int margin = PropsUi.getMargin();
    int middle = propsMiddle();

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.ELK_LAYOUT);

    BaseTransformDialog.positionBottomButtons(shell, new Button[] {wOk, wCancel}, margin, null);

    wSaveAsDefault = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wSaveAsDefault);
    wSaveAsDefault.setText(BaseMessages.getString(PKG, "ElkLayoutDialog.SaveAsDefault.Label"));
    wSaveAsDefault.setToolTipText(
        BaseMessages.getString(PKG, "ElkLayoutDialog.SaveAsDefault.Tooltip"));
    FormData fdSave = new FormData();
    fdSave.left = new FormAttachment(0, 0);
    fdSave.right = new FormAttachment(100, 0);
    fdSave.bottom = new FormAttachment(wOk, -margin);
    wSaveAsDefault.setLayoutData(fdSave);

    ScrolledComposite scrolled =
        new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
    PropsUi.setLook(scrolled);
    scrolled.setExpandHorizontal(true);
    scrolled.setExpandVertical(true);
    FormData fdScrolled = new FormData();
    fdScrolled.left = new FormAttachment(0, 0);
    fdScrolled.top = new FormAttachment(0, 0);
    fdScrolled.right = new FormAttachment(100, 0);
    fdScrolled.bottom = new FormAttachment(wSaveAsDefault, -margin);
    scrolled.setLayoutData(fdScrolled);

    Composite content = new Composite(scrolled, SWT.NONE);
    PropsUi.setLook(content);
    FormLayout contentLayout = new FormLayout();
    contentLayout.marginWidth = margin;
    contentLayout.marginHeight = margin;
    content.setLayout(contentLayout);
    scrolled.setContent(content);

    Control previous = null;

    previous =
        addComboField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.Algorithm.Label"),
            wAlgorithm = createEnumCombo(content, ElkLayoutAlgorithm.class));

    previous =
        addTextField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.TargetWidth.Label"),
            wTargetWidth = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER));

    previous =
        addComboField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.Direction.Label"),
            wDirection = createEnumCombo(content, ElkLayoutDirection.class));

    previous =
        addTextField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.SpacingWithinLayer.Label"),
            wSpacingWithinLayer = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER));

    previous =
        addTextField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.SpacingBetweenLayers.Label"),
            wSpacingBetweenLayers = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER));

    previous =
        addTextField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.SpacingEdgeNode.Label"),
            wSpacingEdgeNode = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER));

    previous =
        addComboField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.CrossingMinimization.Label"),
            wCrossingMinimization = createEnumCombo(content, ElkCrossingMinimization.class));

    previous =
        addComboField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.NodePlacement.Label"),
            wNodePlacement = createEnumCombo(content, ElkNodePlacement.class));

    previous =
        addComboField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.LayeringStrategy.Label"),
            wLayeringStrategy = createEnumCombo(content, ElkLayeringStrategy.class));

    previous =
        addComboField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.CycleBreaking.Label"),
            wCycleBreaking = createEnumCombo(content, ElkCycleBreaking.class));

    previous =
        addTextField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.OriginX.Label"),
            wOriginX = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER));

    previous =
        addTextField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.OriginY.Label"),
            wOriginY = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER));

    previous =
        addTextField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.GridSize.Label"),
            wGridSize = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER));

    previous =
        addTextField(
            content,
            previous,
            middle,
            margin,
            BaseMessages.getString(PKG, "ElkLayoutDialog.MinNodeWidth.Label"),
            wMinNodeWidth = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER));

    addTextField(
        content,
        previous,
        middle,
        margin,
        BaseMessages.getString(PKG, "ElkLayoutDialog.NodeHeight.Label"),
        wNodeHeight = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER));

    wAlgorithm.addListener(SWT.Selection, e -> updateAlgorithmWidgets());
    populateWidgets();
    updateAlgorithmWidgets();
    content.pack();
    scrolled.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));

    shell.setSize(520, 620);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok;
  }

  public ElkLayout getLayout() {
    return result;
  }

  public boolean isSaveAsDefault() {
    return saveAsDefault;
  }

  private void populateWidgets() {
    ElkLayoutValues.selectEnumCombo(wAlgorithm, initial.getAlgorithm());
    wTargetWidth.setText(Integer.toString(initial.getTargetWidth()));
    ElkLayoutValues.selectEnumCombo(wDirection, initial.getDirection());
    wSpacingWithinLayer.setText(Integer.toString(initial.getSpacingWithinLayer()));
    wSpacingBetweenLayers.setText(Integer.toString(initial.getSpacingBetweenLayers()));
    wSpacingEdgeNode.setText(Integer.toString(initial.getSpacingEdgeNode()));
    ElkLayoutValues.selectEnumCombo(wCrossingMinimization, initial.getCrossingMinimization());
    ElkLayoutValues.selectEnumCombo(wNodePlacement, initial.getNodePlacement());
    ElkLayoutValues.selectEnumCombo(wLayeringStrategy, initial.getLayeringStrategy());
    ElkLayoutValues.selectEnumCombo(wCycleBreaking, initial.getCycleBreaking());
    wOriginX.setText(Integer.toString(initial.getOriginX()));
    wOriginY.setText(Integer.toString(initial.getOriginY()));
    wGridSize.setText(Integer.toString(initial.getGridSize()));
    wMinNodeWidth.setText(Integer.toString(initial.getMinNodeWidth()));
    wNodeHeight.setText(Integer.toString(initial.getNodeHeight()));
  }

  private void ok() {
    result = readLayout();
    result.setEnabled(true);
    saveAsDefault = wSaveAsDefault.getSelection();
    ok = true;
    shell.dispose();
  }

  private void cancel() {
    result = null;
    ok = false;
    shell.dispose();
  }

  private ElkLayout readLayout() {
    ElkLayout layout = new ElkLayout();
    layout.setAlgorithm(ElkLayoutValues.getSelectedEnum(wAlgorithm, ElkLayoutAlgorithm.class));
    layout.setTargetWidth(
        ElkLayoutValues.parsePositiveInt(wTargetWidth.getText(), ElkLayout.DEFAULT_TARGET_WIDTH));
    layout.setDirection(ElkLayoutValues.parseEnum(wDirection.getText(), ElkLayoutDirection.class));
    layout.setSpacingWithinLayer(
        ElkLayoutValues.parseNonNegativeInt(
            wSpacingWithinLayer.getText(), ElkLayout.DEFAULT_SPACING_WITHIN_LAYER));
    layout.setSpacingBetweenLayers(
        ElkLayoutValues.parseNonNegativeInt(
            wSpacingBetweenLayers.getText(), ElkLayout.DEFAULT_SPACING_BETWEEN_LAYERS));
    layout.setSpacingEdgeNode(
        ElkLayoutValues.parseNonNegativeInt(
            wSpacingEdgeNode.getText(), ElkLayout.DEFAULT_SPACING_EDGE_NODE));
    layout.setCrossingMinimization(
        ElkLayoutValues.parseEnum(wCrossingMinimization.getText(), ElkCrossingMinimization.class));
    layout.setNodePlacement(
        ElkLayoutValues.parseEnum(wNodePlacement.getText(), ElkNodePlacement.class));
    layout.setLayeringStrategy(
        ElkLayoutValues.parseEnum(wLayeringStrategy.getText(), ElkLayeringStrategy.class));
    layout.setCycleBreaking(
        ElkLayoutValues.parseEnum(wCycleBreaking.getText(), ElkCycleBreaking.class));
    layout.setOriginX(
        ElkLayoutValues.parseNonNegativeInt(wOriginX.getText(), ElkLayout.DEFAULT_ORIGIN_X));
    layout.setOriginY(
        ElkLayoutValues.parseNonNegativeInt(wOriginY.getText(), ElkLayout.DEFAULT_ORIGIN_Y));
    layout.setGridSize(
        ElkLayoutValues.parsePositiveInt(wGridSize.getText(), ElkLayout.DEFAULT_GRID_SIZE));
    layout.setMinNodeWidth(
        ElkLayoutValues.parsePositiveInt(
            wMinNodeWidth.getText(), ElkLayout.DEFAULT_MIN_NODE_WIDTH));
    layout.setNodeHeight(
        ElkLayoutValues.parsePositiveInt(wNodeHeight.getText(), ElkLayout.DEFAULT_NODE_HEIGHT));
    return layout;
  }

  private static int propsMiddle() {
    return PropsUi.getInstance().getMiddlePct();
  }

  private static <E extends Enum<E> & org.apache.hop.metadata.api.IEnumHasCodeAndDescription>
      Combo createEnumCombo(Composite parent, Class<E> enumClass) {
    Combo combo = new Combo(parent, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(combo);
    ElkLayoutValues.populateEnumCombo(combo, enumClass);
    return combo;
  }

  private static Control addTextField(
      Composite parent, Control previous, int middle, int margin, String labelText, Text text) {
    PropsUi.setLook(text);
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(labelText);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, 0);
    fdl.top = topAttachment(previous, margin);
    label.setLayoutData(fdl);

    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, margin);
    fd.top = topAttachment(previous, margin);
    fd.right = new FormAttachment(100, 0);
    text.setLayoutData(fd);
    return text;
  }

  private static Control addComboField(
      Composite parent, Control previous, int middle, int margin, String labelText, Combo combo) {
    Label label = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(labelText);
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, 0);
    fdl.top = topAttachment(previous, margin);
    label.setLayoutData(fdl);

    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, margin);
    fd.top = topAttachment(previous, margin);
    fd.right = new FormAttachment(100, 0);
    combo.setLayoutData(fd);
    return combo;
  }

  private static FormAttachment topAttachment(Control previous, int margin) {
    return previous == null ? new FormAttachment(0, margin) : new FormAttachment(previous, margin);
  }

  private void updateAlgorithmWidgets() {
    boolean rectPacking =
        ElkLayoutValues.getSelectedEnum(wAlgorithm, ElkLayoutAlgorithm.class)
            == ElkLayoutAlgorithm.RECT_PACKING;
    wTargetWidth.setEnabled(rectPacking);
    wDirection.setEnabled(!rectPacking);
    wSpacingBetweenLayers.setEnabled(!rectPacking);
    wSpacingEdgeNode.setEnabled(!rectPacking);
    wCrossingMinimization.setEnabled(!rectPacking);
    wNodePlacement.setEnabled(!rectPacking);
    wLayeringStrategy.setEnabled(!rectPacking);
    wCycleBreaking.setEnabled(!rectPacking);
  }
}
