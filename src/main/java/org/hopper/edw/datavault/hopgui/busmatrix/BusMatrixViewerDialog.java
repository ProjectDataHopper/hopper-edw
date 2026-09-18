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
package org.hopper.edw.datavault.hopgui.busmatrix;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiToolbarWidgets;
import org.apache.hop.ui.core.gui.IToolbarContainer;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.ToolbarFacade;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
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
import org.hopper.edw.catalog.hopgui.navigation.RecordOriginNavigationSupport;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.apache.hop.core.gui.Point;
import org.apache.hop.ui.hopgui.context.GuiContextUtil;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.hopper.edw.datavault.busmatrix.BusMatrix;
import org.hopper.edw.datavault.busmatrix.BusMatrixBuilder;
import org.hopper.edw.datavault.busmatrix.BusMatrixColumn;
import org.hopper.edw.datavault.busmatrix.BusMatrixCsvWriter;
import org.hopper.edw.datavault.busmatrix.BusMatrixHit;
import org.hopper.edw.datavault.busmatrix.BusMatrixRow;
import org.hopper.edw.datavault.busmatrix.BusMatrixSvgPainter;
import org.hopper.edw.datavault.documentation.model.SvgHit;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;
import org.hopper.edw.datavault.resourcedefinition.ResourceDefinitionGroupResolver;

/** Navigable Kimball bus matrix for one resource definition group. */
@GuiPlugin(description = "Kimball bus matrix viewer")
public final class BusMatrixViewerDialog {

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "BusMatrixViewerDialog-Toolbar";

  public static final String TOOLBAR_ITEM_ZOOM_IN = "BusMatrixViewerDialog-ToolBar-10010-ZoomIn";
  public static final String TOOLBAR_ITEM_ZOOM_OUT = "BusMatrixViewerDialog-ToolBar-10020-ZoomOut";
  public static final String TOOLBAR_ITEM_ZOOM_100 = "BusMatrixViewerDialog-ToolBar-10030-Zoom100";
  public static final String TOOLBAR_ITEM_ZOOM_FIT = "BusMatrixViewerDialog-ToolBar-10040-ZoomFit";
  public static final String TOOLBAR_ITEM_ZOOM_FIT_WIDTH =
      "BusMatrixViewerDialog-ToolBar-10050-ZoomFitWidth";
  public static final String TOOLBAR_ITEM_REFRESH = "BusMatrixViewerDialog-ToolBar-10060-Refresh";

  private static final Class<?> PKG = BusMatrixLaunchSupport.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;

  private ResourceDefinitionGroupMeta group;
  private Shell shell;
  private GuiToolbarWidgets toolBarWidgets;
  private Combo wGroup;
  private Text wSearch;
  private Combo wBusiness;
  private Combo wLevel1;
  private Combo wLevel2;
  private Button wHideUnused;
  private Label wlStatus;
  private BusMatrixCanvas canvas;
  private BusMatrix fullMatrix;

  public BusMatrixViewerDialog(
      Shell parent,
      HopGui hopGui,
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.group = group;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public void open() {
    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    PropsUi.setLook(shell);
    shell.setText(
        BaseMessages.getString(PKG, "BusMatrixViewerDialog.Title", Const.NVL(group.getName(), "")));

    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();

    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> shell.dispose());
    Button wExportCsv = new Button(shell, SWT.PUSH);
    wExportCsv.setText(BaseMessages.getString(PKG, "BusMatrixViewerDialog.ExportCsv.Label"));
    wExportCsv.addListener(SWT.Selection, e -> export("csv"));
    Button wExportSvg = new Button(shell, SWT.PUSH);
    wExportSvg.setText(BaseMessages.getString(PKG, "BusMatrixViewerDialog.ExportSvg.Label"));
    wExportSvg.addListener(SWT.Selection, e -> export("svg"));
    Button wRefresh = new Button(shell, SWT.PUSH);
    wRefresh.setText(BaseMessages.getString(PKG, "BusMatrixViewerDialog.Refresh.Label"));
    wRefresh.addListener(SWT.Selection, e -> rebuild());
    Button wHelp = new Button(shell, SWT.PUSH);
    wHelp.setText(BaseMessages.getString(PKG, "System.Button.Help"));
    wHelp.addListener(SWT.Selection, e -> DialogHelpSupport.openHelp(shell, HelpTopics.BUS_MATRIX));
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wExportCsv, wExportSvg, wRefresh, wHelp, wClose}, margin, null);

    Label wlGroup = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlGroup);
    wlGroup.setText(BaseMessages.getString(PKG, "BusMatrixViewerDialog.Group.Label"));
    FormData fdlGroup = new FormData();
    fdlGroup.left = new FormAttachment(0, 0);
    fdlGroup.top = new FormAttachment(0, margin);
    wlGroup.setLayoutData(fdlGroup);

    wGroup = new Combo(shell, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wGroup);
    FormData fdGroup = new FormData();
    fdGroup.left = new FormAttachment(wlGroup, margin);
    fdGroup.top = new FormAttachment(0, margin);
    fdGroup.width = 180;
    wGroup.setLayoutData(fdGroup);
    fillGroupCombo();
    wGroup.addListener(SWT.Selection, e -> switchGroup());

    Label wlSearch = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(wlSearch);
    wlSearch.setText(BaseMessages.getString(PKG, "BusMatrixViewerDialog.Search.Label"));
    FormData fdlSearch = new FormData();
    fdlSearch.left = new FormAttachment(wGroup, margin);
    fdlSearch.top = new FormAttachment(wGroup, 0, SWT.CENTER);
    wlSearch.setLayoutData(fdlSearch);

    wSearch = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.SEARCH | SWT.ICON_CANCEL);
    PropsUi.setLook(wSearch);
    FormData fdSearch = new FormData();
    fdSearch.left = new FormAttachment(wlSearch, margin);
    fdSearch.top = new FormAttachment(wGroup, 0, SWT.CENTER);
    fdSearch.width = 160;
    wSearch.setLayoutData(fdSearch);
    wSearch.addListener(SWT.Modify, e -> applyFilter());

    wBusiness = addFilterCombo(wSearch, "BusMatrixViewerDialog.Business.Label");
    wLevel1 = addFilterCombo(wBusiness, "BusMatrixViewerDialog.Level1.Label");
    wLevel2 = addFilterCombo(wLevel1, "BusMatrixViewerDialog.Level2.Label");

    wHideUnused = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wHideUnused);
    wHideUnused.setText(BaseMessages.getString(PKG, "BusMatrixViewerDialog.HideUnused.Label"));
    FormData fdHide = new FormData();
    fdHide.left = new FormAttachment(wLevel2, margin * 2);
    fdHide.top = new FormAttachment(wLevel2, 0, SWT.CENTER);
    wHideUnused.setLayoutData(fdHide);
    wHideUnused.addListener(SWT.Selection, e -> applyFilter());

    IToolbarContainer toolbarContainer =
        ToolbarFacade.createToolbarContainer(shell, SWT.WRAP | SWT.LEFT | SWT.HORIZONTAL);
    toolBarWidgets = new GuiToolbarWidgets();
    toolBarWidgets.registerGuiPluginObject(this);
    toolBarWidgets.createToolbarWidgets(toolbarContainer, GUI_PLUGIN_TOOLBAR_PARENT_ID);
    Control toolBar = toolbarContainer.getControl();
    PropsUi.setLook(toolBar, Props.WIDGET_STYLE_TOOLBAR);

    FormData fdToolBar = new FormData();
    fdToolBar.left = new FormAttachment(0, 0);
    fdToolBar.top = new FormAttachment(wSearch, margin);
    fdToolBar.right = new FormAttachment(100, 0);
    toolBar.setLayoutData(fdToolBar);
    toolBar.pack();

    wlStatus = new Label(shell, SWT.LEFT);
    PropsUi.setLook(wlStatus);
    FormData fdStatus = new FormData();
    fdStatus.left = new FormAttachment(0, 0);
    fdStatus.right = new FormAttachment(100, 0);
    fdStatus.bottom = new FormAttachment(wClose, -margin);
    wlStatus.setLayoutData(fdStatus);

    canvas = new BusMatrixCanvas(shell);
    FormData fdCanvas = new FormData();
    fdCanvas.left = new FormAttachment(0, 0);
    fdCanvas.top = new FormAttachment(toolBar, margin);
    fdCanvas.right = new FormAttachment(100, 0);
    fdCanvas.bottom = new FormAttachment(wlStatus, -margin);
    canvas.setLayoutData(fdCanvas);
    canvas.setHitClickListener(this::openContextMenu);
    shell.getDisplay().asyncExec(() -> canvas.zoomFitWidth());

    rebuild();
    updateTitle();
    shell.setSize(1100, 720);
    BaseDialog.defaultShellHandling(shell, c -> shell.dispose(), c -> shell.dispose());
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_IN,
      toolTip = "i18n::BusMatrixViewerDialog.ZoomIn.Tooltip",
      image = "ui/images/zoom-in.svg")
  public void zoomIn() {
    if (canvas != null) {
      canvas.zoomIn();
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_OUT,
      toolTip = "i18n::BusMatrixViewerDialog.ZoomOut.Tooltip",
      image = "ui/images/zoom-out.svg")
  public void zoomOut() {
    if (canvas != null) {
      canvas.zoomOut();
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_100,
      toolTip = "i18n::BusMatrixViewerDialog.Zoom100.Tooltip",
      image = "ui/images/zoom-100.svg")
  public void zoom100Percent() {
    if (canvas != null) {
      canvas.zoom100Percent();
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_FIT,
      toolTip = "i18n::BusMatrixViewerDialog.ZoomFitSize.Tooltip",
      image = "ui/images/zoom-fit.svg")
  public void zoomFitSize() {
    if (canvas != null) {
      canvas.zoomFitSize();
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_ZOOM_FIT_WIDTH,
      toolTip = "i18n::BusMatrixViewerDialog.ZoomFitWidth.Tooltip",
      image = "ui/images/show-all.svg")
  public void zoomFitWidth() {
    if (canvas != null) {
      canvas.zoomFitWidth();
    }
  }

  @GuiToolbarElement(
      root = GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_REFRESH,
      toolTip = "i18n::BusMatrixViewerDialog.Refresh.Label",
      image = "ui/images/refresh.svg",
      separator = true)
  public void refreshToolbar() {
    rebuild();
  }

  private Combo addFilterCombo(org.eclipse.swt.widgets.Control left, String labelKey) {
    int margin = PropsUi.getMargin();
    Label label = new Label(shell, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(BaseMessages.getString(PKG, labelKey));
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(left, margin);
    fdl.top = new FormAttachment(left, 0, SWT.CENTER);
    label.setLayoutData(fdl);
    Combo combo = new Combo(shell, SWT.SINGLE | SWT.BORDER);
    PropsUi.setLook(combo);
    FormData fd = new FormData();
    fd.left = new FormAttachment(label, margin);
    fd.top = new FormAttachment(left, 0, SWT.CENTER);
    fd.width = 140;
    combo.setLayoutData(fd);
    combo.addListener(SWT.Modify, e -> applyFilter());
    return combo;
  }

  private void fillGroupCombo() {
    String current = group != null ? Const.NVL(group.getName(), "") : "";
    wGroup.removeAll();
    try {
      if (metadataProvider != null) {
        List<String> names =
            metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class).listObjectNames();
        if (names != null) {
          names.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(wGroup::add);
        }
      }
    } catch (Exception e) {
      // Keep the current group even if the metadata list cannot be loaded.
    }
    if (!Utils.isEmpty(current) && wGroup.indexOf(current) < 0) {
      wGroup.add(current);
    }
    wGroup.setText(current);
  }

  private void switchGroup() {
    String name = wGroup.getText();
    if (Utils.isEmpty(name) || (group != null && name.equals(group.getName()))) {
      return;
    }
    try {
      group = ResourceDefinitionGroupResolver.loadGroup(name, metadataProvider);
      updateTitle();
      rebuild();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Build"),
          e);
    }
  }

  private void updateTitle() {
    if (shell == null || shell.isDisposed()) {
      return;
    }
    shell.setText(
        BaseMessages.getString(
            PKG,
            "BusMatrixViewerDialog.Title",
            group != null ? Const.NVL(group.getName(), "") : ""));
  }

  private void rebuild() {
    try {
      fullMatrix = BusMatrixBuilder.build(group, variables, metadataProvider);
      fillFilter(wBusiness, fullMatrix.businesses());
      fillFilter(wLevel1, fullMatrix.level1Values());
      fillFilter(wLevel2, fullMatrix.level2Values());
      applyFilter();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Build"),
          e);
    }
  }

  private static void fillFilter(Combo combo, java.util.List<String> values) {
    String current = combo.getText();
    combo.removeAll();
    combo.add("");
    for (String value : values) {
      combo.add(value);
    }
    combo.setText(Const.NVL(current, ""));
  }

  private void applyFilter() {
    if (fullMatrix == null) {
      return;
    }
    BusMatrix filtered =
        fullMatrix.filtered(
            wSearch.getText(),
            wBusiness.getText(),
            wLevel1.getText(),
            wLevel2.getText(),
            wHideUnused.getSelection());
    canvas.setMatrix(filtered);
    String status =
        BaseMessages.getString(
            PKG,
            "BusMatrixViewerDialog.Status",
            Integer.toString(filtered.getRows().size()),
            Integer.toString(filtered.getColumns().size()),
            Integer.toString(fullMatrix.getWarnings().size()));
    wlStatus.setText(status);
  }

  private void openContextMenu(BusMatrixHit hit, org.eclipse.swt.widgets.Event event) {
    if (hit == null || !hit.hasAction() || hopGui == null) {
      return;
    }
    try {
      org.eclipse.swt.graphics.Point screenPoint;
      if (event != null && canvas != null && !canvas.isDisposed()) {
        screenPoint = shell.getDisplay().map(canvas, null, event.x, event.y);
      } else {
        screenPoint = shell.getDisplay().getCursorLocation();
      }
      String targetDesc = hit.targetName();
      String message =
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Context.Message", targetDesc);
      IGuiContextHandler contextHandler =
          new BusMatrixContextHandler(hopGui, shell, variables, metadataProvider, hit);
      GuiContextUtil.getInstance()
          .handleActionSelection(
              shell,
              message,
              new Point(screenPoint.x, screenPoint.y),
              contextHandler);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Open"),
          e);
    }
  }

  private void navigate(String filename, String element) throws HopException {
    if (Utils.isEmpty(filename)) {
      return;
    }
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType(RecordOriginNavigationSupport.MODEL_TYPE_DIMENSIONAL);
    origin.setModelFilename(filename);
    origin.setModelElementName(element);
    RecordOriginNavigationSupport.navigateToOrigin(hopGui, origin, variables);
  }

  private void export(String format) {
    try {
      String ext = "svg".equals(format) ? "svg" : "csv";
      String[] filters = new String[] {"*." + ext, "*"};
      String[] names =
          new String[] {
            ext.toUpperCase(Locale.ROOT), BaseMessages.getString(PKG, "System.FileType.AllFiles")
          };
      IVariables vars = exportVariables();
      FileObject start = suggestedStartFile(ext, vars);
      String filename =
          BaseDialog.presentFileDialog(true, shell, null, vars, start, filters, names, true);
      if (Utils.isEmpty(filename)) {
        return;
      }
      String resolved = withLowercaseExtension(resolveExportFilename(filename, vars), ext);
      BusMatrix shown = canvas.getMatrix();
      String content =
          "svg".equals(ext)
              ? BusMatrixSvgPainter.paint(shown, false)
              : BusMatrixCsvWriter.write(shown);
      try (FileObject file = HopVfs.getFileObject(resolved)) {
        if (file.getParent() != null && !file.getParent().exists()) {
          file.getParent().createFolder();
        }
        try (java.io.OutputStream out = HopVfs.getOutputStream(file, false)) {
          out.write(content.getBytes(StandardCharsets.UTF_8));
        }
      }
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixViewerDialog.Error.Export"),
          e);
    }
  }

  private IVariables exportVariables() {
    if (hopGui != null && hopGui.getVariables() != null) {
      return hopGui.getVariables();
    }
    return variables;
  }

  private FileObject suggestedStartFile(String ext, IVariables vars) {
    try {
      String resolved = resolveExportFilename(suggestedExportPath(ext), vars);
      if (Utils.isEmpty(resolved) || resolved.contains("${")) {
        return null;
      }
      return HopVfs.getFileObject(resolved);
    } catch (Exception e) {
      return null;
    }
  }

  private String suggestedExportPath(String ext) {
    String groupName = group != null ? Const.NVL(group.getName(), "bus-matrix") : "bus-matrix";
    String slug = groupName.replaceAll("[^A-Za-z0-9._-]+", "-");
    return "${PROJECT_HOME}/work/bus-matrix-" + slug + "." + ext;
  }

  /**
   * Hop VFS can turn an unresolved {@code ${PROJECT_HOME}/...} save path into a {@code file:} URL
   * under the Hop install. Strip that prefix, then resolve variables.
   */
  static String resolveExportFilename(String filename, IVariables vars) {
    String path = Const.NVL(filename, "");
    int marker = path.indexOf("${");
    if (marker > 0) {
      path = path.substring(marker);
    }
    if (vars != null) {
      path = vars.resolve(path);
    }
    return path;
  }

  static String withLowercaseExtension(String filename, String ext) {
    if (Utils.isEmpty(filename) || Utils.isEmpty(ext)) {
      return filename;
    }
    int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
    int dot = filename.lastIndexOf('.');
    if (dot > slash) {
      if (filename.substring(dot + 1).equalsIgnoreCase(ext)) {
        return filename.substring(0, dot + 1) + ext;
      }
    }
    return filename + "." + ext;
  }
}
