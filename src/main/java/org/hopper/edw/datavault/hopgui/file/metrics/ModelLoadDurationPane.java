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
package org.hopper.edw.datavault.hopgui.file.metrics;

import java.util.List;
import java.util.function.Supplier;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.shared.SwtGc;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.hopper.edw.datavault.metrics.LoadRunDurationMetricsLoader;
import org.hopper.edw.datavault.metrics.LoadRunDurationSnapshot;

/** Right-hand duration overview panel for DV/BV/DM model graphs. */
public class ModelLoadDurationPane extends Composite {

  private static final Class<?> PKG = ModelLoadDurationPane.class;

  private final HopGui hopGui;
  private final IVariables variables;
  private final Supplier<String> modelNameSupplier;
  private final Supplier<String> modelTypeSupplier;
  private final Supplier<List<String>> tableNamesSupplier;

  private final boolean webMode;
  private final ScrolledComposite scrolledComposite;
  private final Canvas chartCanvas;
  private final Label statusLabel;
  private final Composite tableHolder;
  private final Label titleLabel;
  private final Button refreshButton;
  private final Button viewDataButton;

  private Table webTable;
  private LoadRunDurationSnapshot snapshot = LoadRunDurationSnapshot.builder().build();
  private LoadRunDurationOverviewPainter lastPainter;
  private boolean loading;

  public ModelLoadDurationPane(
      Composite parent,
      HopGui hopGui,
      IVariables variables,
      Supplier<String> modelNameSupplier,
      Supplier<String> modelTypeSupplier,
      Supplier<List<String>> tableNamesSupplier) {
    super(parent, SWT.BORDER);
    this.hopGui = hopGui;
    this.variables = variables;
    this.modelNameSupplier = modelNameSupplier;
    this.modelTypeSupplier = modelTypeSupplier;
    this.tableNamesSupplier = tableNamesSupplier;
    this.webMode = EnvironmentUtils.getInstance().isWeb();

    setLayout(new FormLayout());
    PropsUi.setLook(this);
    applyStandardBackground(this);

    Composite header = new Composite(this, SWT.NONE);
    header.setLayout(new FormLayout());
    PropsUi.setLook(header);
    applyStandardBackground(header);
    FormData fdHeader = new FormData();
    fdHeader.left = new FormAttachment(0, 0);
    fdHeader.top = new FormAttachment(0, 0);
    fdHeader.right = new FormAttachment(100, 0);
    header.setLayoutData(fdHeader);

    titleLabel = new Label(header, SWT.LEFT);
    PropsUi.setLook(titleLabel);
    applyStandardBackground(titleLabel);
    titleLabel.setText(BaseMessages.getString(PKG, "ModelLoadDurationPane.Title"));
    FormData fdTitle = new FormData();
    fdTitle.left = new FormAttachment(0, PropsUi.getMargin());
    fdTitle.top = new FormAttachment(0, PropsUi.getMargin());
    fdTitle.bottom = new FormAttachment(100, -PropsUi.getMargin());
    titleLabel.setLayoutData(fdTitle);

    viewDataButton = new Button(header, SWT.PUSH);
    PropsUi.setLook(viewDataButton);
    applyStandardBackground(viewDataButton);
    viewDataButton.setText(BaseMessages.getString(PKG, "ModelLoadDurationPane.ViewData"));
    viewDataButton.setToolTipText(
        BaseMessages.getString(PKG, "ModelLoadDurationPane.ViewData.Tooltip"));
    viewDataButton.addListener(SWT.Selection, event -> openDurationPreview());
    FormData fdViewData = new FormData();
    fdViewData.top = new FormAttachment(0, PropsUi.getMargin());
    fdViewData.right = new FormAttachment(100, -PropsUi.getMargin());
    fdViewData.bottom = new FormAttachment(100, -PropsUi.getMargin());
    viewDataButton.setLayoutData(fdViewData);

    refreshButton = new Button(header, SWT.PUSH);
    PropsUi.setLook(refreshButton);
    applyStandardBackground(refreshButton);
    refreshButton.setText(BaseMessages.getString(PKG, "ModelLoadDurationPane.Refresh"));
    refreshButton.setToolTipText(
        BaseMessages.getString(PKG, "ModelLoadDurationPane.Refresh.Tooltip"));
    refreshButton.addListener(SWT.Selection, event -> refresh());
    FormData fdRefresh = new FormData();
    fdRefresh.top = new FormAttachment(0, PropsUi.getMargin());
    fdRefresh.right = new FormAttachment(viewDataButton, -PropsUi.getMargin());
    fdRefresh.bottom = new FormAttachment(100, -PropsUi.getMargin());
    refreshButton.setLayoutData(fdRefresh);
    fdTitle.right = new FormAttachment(refreshButton, -PropsUi.getMargin());

    if (webMode) {
      scrolledComposite = null;
      chartCanvas = null;
      statusLabel = new Label(this, SWT.WRAP | SWT.LEFT);
      PropsUi.setLook(statusLabel);
      applyStandardBackground(statusLabel);
      FormData fdStatus = new FormData();
      fdStatus.left = new FormAttachment(0, PropsUi.getMargin());
      fdStatus.top = new FormAttachment(header, PropsUi.getMargin());
      fdStatus.right = new FormAttachment(100, -PropsUi.getMargin());
      statusLabel.setLayoutData(fdStatus);

      tableHolder = new Composite(this, SWT.NONE);
      tableHolder.setLayout(new FillLayout());
      PropsUi.setLook(tableHolder);
      applyStandardBackground(tableHolder);
      FormData fdTable = new FormData();
      fdTable.left = new FormAttachment(0, 0);
      fdTable.top = new FormAttachment(header, PropsUi.getMargin());
      fdTable.right = new FormAttachment(100, 0);
      fdTable.bottom = new FormAttachment(100, 0);
      tableHolder.setLayoutData(fdTable);
    } else {
      statusLabel = null;
      tableHolder = null;
      scrolledComposite = new ScrolledComposite(this, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
      PropsUi.setLook(scrolledComposite);
      applyStandardBackground(scrolledComposite);
      scrolledComposite.setExpandHorizontal(false);
      scrolledComposite.setExpandVertical(false);
      // Use Widget.addListener — RAP ScrolledComposite does not implement addPaintListener
      // (NoSuchMethodError under Hop Web).
      scrolledComposite.addListener(SWT.Paint, this::paintScrollBackgroundEvent);
      FormData fdScroll = new FormData();
      fdScroll.left = new FormAttachment(0, 0);
      fdScroll.top = new FormAttachment(header, PropsUi.getMargin());
      fdScroll.right = new FormAttachment(100, 0);
      fdScroll.bottom = new FormAttachment(100, 0);
      scrolledComposite.setLayoutData(fdScroll);

      Composite canvasHolder = new Composite(scrolledComposite, SWT.NONE);
      canvasHolder.setLayout(new FillLayout());
      PropsUi.setLook(canvasHolder);
      applyStandardBackground(canvasHolder);

      chartCanvas = new Canvas(canvasHolder, SWT.DOUBLE_BUFFERED);
      applyStandardBackground(chartCanvas);
      chartCanvas.addPaintListener(this::paintChart);
      chartCanvas.addListener(SWT.MouseMove, this::onMouseMove);

      scrolledComposite.setContent(canvasHolder);
    }
    updateHeaderButtonState();
    refresh();
  }

  private void openDurationPreview() {
    LoadRunDurationPreviewSupport.openPreviewDialog(
        getShell(), variables, modelNameSupplier.get(), snapshot);
  }

  private void updateHeaderButtonState() {
    if (viewDataButton == null || viewDataButton.isDisposed()) {
      return;
    }
    boolean canInteract = !loading;
    if (refreshButton != null && !refreshButton.isDisposed()) {
      refreshButton.setEnabled(canInteract);
    }
    viewDataButton.setEnabled(
        canInteract && LoadRunDurationPreviewSupport.hasPreviewRows(snapshot));
  }

  public void refresh() {
    if (isDisposed()) {
      return;
    }
    loading = true;
    snapshot =
        LoadRunDurationSnapshot.builder()
            .status(LoadRunDurationSnapshot.Status.LOADED)
            .message(BaseMessages.getString(PKG, "ModelLoadDurationPane.Loading"))
            .build();
    updateHeaderButtonState();
    showSnapshot();

    Display display = getDisplay();
    String modelName = modelNameSupplier.get();
    String modelType = modelTypeSupplier.get();
    List<String> tableNames = tableNamesSupplier.get();
    IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();

    Thread loaderThread =
        new Thread(
            () -> {
              LoadRunDurationSnapshot loaded =
                  LoadRunDurationMetricsLoader.load(
                      modelName, modelType, tableNames, metadataProvider, variables);
              if (display.isDisposed()) {
                return;
              }
              display.asyncExec(
                  () -> {
                    if (isDisposed()) {
                      return;
                    }
                    loading = false;
                    snapshot = loaded;
                    updateHeaderButtonState();
                    showSnapshot();
                  });
            },
            "LoadRunDurationMetricsLoader");
    loaderThread.setDaemon(true);
    loaderThread.start();
  }

  private void showSnapshot() {
    if (webMode) {
      updateWebBody();
    } else {
      resizeChart();
      redrawChart();
    }
  }

  private void updateWebBody() {
    if (statusLabel == null || statusLabel.isDisposed() || tableHolder == null) {
      return;
    }
    if (loading) {
      showWebStatus(BaseMessages.getString(PKG, "ModelLoadDurationPane.Loading"));
      return;
    }
    LoadRunDurationTableSupport.Model model = LoadRunDurationTableSupport.build(snapshot);
    if (!model.hasRows()) {
      showWebStatus(Const.NVL(model.statusMessage(), ""));
      return;
    }
    statusLabel.setVisible(false);
    tableHolder.setVisible(true);
    rebuildWebTable(model);
    layout(true, true);
  }

  private void showWebStatus(String message) {
    disposeWebTable();
    statusLabel.setText(Const.NVL(message, ""));
    statusLabel.setVisible(true);
    tableHolder.setVisible(false);
    layout(true, true);
  }

  private void rebuildWebTable(LoadRunDurationTableSupport.Model model) {
    disposeWebTable();
    webTable =
        new Table(
            tableHolder, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    PropsUi.setLook(webTable);
    applyStandardBackground(webTable);
    webTable.setHeaderVisible(true);
    webTable.setLinesVisible(true);

    TableColumn tableColumn = new TableColumn(webTable, SWT.LEFT);
    tableColumn.setText(BaseMessages.getString(PKG, "LoadRunDurationTableSupport.Column.Table"));
    for (String header : model.runHeaders()) {
      TableColumn runColumn = new TableColumn(webTable, SWT.RIGHT);
      runColumn.setText(Const.NVL(header, ""));
    }

    for (LoadRunDurationTableSupport.Row row : model.rows()) {
      TableItem item = new TableItem(webTable, SWT.NONE);
      item.setText(0, Const.NVL(row.tableName(), ""));
      List<LoadRunDurationTableSupport.Cell> cells = row.cells();
      for (int i = 0; i < cells.size(); i++) {
        item.setText(i + 1, Const.NVL(cells.get(i).text(), ""));
      }
    }
    for (TableColumn column : webTable.getColumns()) {
      column.pack();
    }
    tableHolder.layout(true, true);
  }

  private void disposeWebTable() {
    if (webTable != null && !webTable.isDisposed()) {
      webTable.dispose();
    }
    webTable = null;
  }

  private void paintChart(PaintEvent event) {
    Point preferred = computeChartPreferredSize();
    int width = preferred.x;
    int height = preferred.y;

    boolean needsDoubleBuffering =
        Const.isWindows() && "GUI".equalsIgnoreCase(Const.getHopPlatformRuntime());
    Image image = null;
    GC swtGc = event.gc;
    if (needsDoubleBuffering) {
      image = new Image(getDisplay(), width, height);
      swtGc = new GC(image);
    }

    PropsUi propsUi = PropsUi.getInstance();
    IGc gc = new SwtGc(swtGc, width, height, propsUi.getIconSize());
    try {
      LoadRunDurationOverviewPainter painter =
          new LoadRunDurationOverviewPainter(snapshot, gc, variables, width, height);
      if (loading) {
        painter.drawLoadingMessage(BaseMessages.getString(PKG, "ModelLoadDurationPane.Loading"));
      } else {
        painter.drawOverview();
      }
      lastPainter = painter;
    } finally {
      gc.dispose();
    }

    if (needsDoubleBuffering) {
      event.gc.drawImage(image, 0, 0);
      swtGc.dispose();
      image.dispose();
    }
  }

  private Point computeChartPreferredSize() {
    if (chartCanvas == null || chartCanvas.isDisposed()) {
      return LoadRunDurationOverviewPainter.computePreferredSize(snapshot);
    }
    GC sizingGc = new GC(chartCanvas);
    try {
      PropsUi propsUi = PropsUi.getInstance();
      IGc gc = new SwtGc(sizingGc, 1, 1, propsUi.getIconSize());
      try {
        return LoadRunDurationOverviewPainter.computePreferredSize(
            snapshot, gc, PropsUi.getNativeZoomFactor());
      } finally {
        gc.dispose();
      }
    } finally {
      sizingGc.dispose();
    }
  }

  private void paintScrollBackgroundEvent(Event event) {
    if (event == null || event.gc == null) {
      return;
    }
    Color background = GuiResource.getInstance().getColorBackground();
    event.gc.setBackground(background);
    event.gc.fillRectangle(event.x, event.y, event.width, event.height);
  }

  private void applyStandardBackground(Control control) {
    if (control == null || control.isDisposed()) {
      return;
    }
    control.setBackground(GuiResource.getInstance().getColorBackground());
  }

  private void resizeChart() {
    if (chartCanvas == null || chartCanvas.isDisposed() || scrolledComposite == null) {
      return;
    }
    Point preferred = computeChartPreferredSize();
    chartCanvas.setSize(preferred.x, preferred.y);
    if (scrolledComposite.getContent() instanceof Composite content && !content.isDisposed()) {
      content.setSize(preferred.x, preferred.y);
      content.layout(true, true);
    }
    scrolledComposite.setMinSize(preferred.x, preferred.y);
    scrolledComposite.layout(true, true);
    layout(true, true);
  }

  private void redrawChart() {
    if (chartCanvas != null && !chartCanvas.isDisposed()) {
      chartCanvas.redraw();
    }
  }

  private void onMouseMove(Event event) {
    if (lastPainter == null) {
      chartCanvas.setToolTipText(null);
      return;
    }
    LoadRunDurationOverviewPainter.DurationBarHit hit = lastPainter.findBarHit(event.x, event.y);
    if (hit == null) {
      chartCanvas.setToolTipText(null);
      return;
    }
    String status =
        hit.run().isSuccess()
            ? BaseMessages.getString(PKG, "ModelLoadDurationPane.Tooltip.Success")
            : BaseMessages.getString(PKG, "ModelLoadDurationPane.Tooltip.Failed");
    chartCanvas.setToolTipText(
        BaseMessages.getString(
            PKG,
            "ModelLoadDurationPane.Tooltip",
            hit.tableName(),
            LoadRunDurationOverviewPainter.formatRunLabel(hit.run().getFinishedAt()),
            LoadRunDurationOverviewPainter.formatDuration(hit.durationMs()),
            status));
  }
}
