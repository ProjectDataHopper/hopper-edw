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

import org.apache.hop.core.Const;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.ScrollBar;
import org.hopper.edw.datavault.busmatrix.BusMatrix;
import org.hopper.edw.datavault.busmatrix.BusMatrixCell;
import org.hopper.edw.datavault.busmatrix.BusMatrixColumn;
import org.hopper.edw.datavault.busmatrix.BusMatrixLayout;
import org.hopper.edw.datavault.busmatrix.BusMatrixRow;

/** Scrollable bus-matrix grid with frozen process labels and dimension headers. */
final class BusMatrixCanvas extends Canvas {

  private BusMatrix matrix = new BusMatrix("", null, null, null);
  private BusMatrixLayout layout = BusMatrixLayout.DEFAULT;
  private Color oddRowBackground;
  private Color markBackground;

  BusMatrixCanvas(Composite parent) {
    super(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED);
    PropsUi.setLook(this);
    addDisposeListener(
        e -> {
          disposeColor(oddRowBackground);
          disposeColor(markBackground);
        });
    addListener(SWT.Paint, this::paint);
    addListener(SWT.Resize, e -> updateScrollBars());
    addListener(SWT.MouseMove, this::onMove);
    addListener(SWT.MouseWheel, this::onWheel);
    ScrollBar hBar = getHorizontalBar();
    ScrollBar vBar = getVerticalBar();
    if (hBar != null) {
      hBar.addListener(SWT.Selection, e -> redraw());
    }
    if (vBar != null) {
      vBar.addListener(SWT.Selection, e -> redraw());
    }
  }

  void setMatrix(BusMatrix matrix) {
    this.matrix = matrix != null ? matrix : new BusMatrix("", null, null, null);
    redraw();
  }

  BusMatrix getMatrix() {
    return matrix;
  }

  Hit hitAt(int x, int y) {
    int scrollX = bar(getHorizontalBar());
    int scrollY = bar(getVerticalBar());
    if (y < layout.headerHeight() && x >= layout.frozenWidth()) {
      int col = layout.headerColumnAt(x, y, scrollX, matrix.getColumns().size());
      if (col >= 0) {
        return new Hit(HitKind.COLUMN, -1, col);
      }
    }
    if (x < layout.frozenWidth() && y >= layout.headerHeight()) {
      int row = (y + scrollY - layout.headerHeight()) / Math.max(1, layout.rowHeight());
      if (row >= 0 && row < matrix.getRows().size()) {
        return new Hit(HitKind.ROW, row, -1);
      }
    }
    if (x >= layout.frozenWidth() && y >= layout.headerHeight()) {
      int col = (x + scrollX - layout.frozenWidth()) / Math.max(1, layout.cellWidth());
      int row = (y + scrollY - layout.headerHeight()) / Math.max(1, layout.rowHeight());
      if (row >= 0
          && row < matrix.getRows().size()
          && col >= 0
          && col < matrix.getColumns().size()) {
        return new Hit(HitKind.CELL, row, col);
      }
    }
    return null;
  }

  private void onMove(Event event) {
    setToolTipText(tooltip(hitAt(event.x, event.y)));
  }

  private void onWheel(Event event) {
    ScrollBar bar = event.stateMask == SWT.SHIFT ? getHorizontalBar() : getVerticalBar();
    if (bar == null || !bar.getVisible()) {
      return;
    }
    int unit =
        event.stateMask == SWT.SHIFT
            ? Math.max(1, layout.cellWidth())
            : Math.max(1, layout.rowHeight());
    int step = event.count > 0 ? -unit * 3 : unit * 3;
    bar.setSelection(bar.getSelection() + step);
    redraw();
  }

  private String tooltip(Hit hit) {
    if (hit == null) {
      return null;
    }
    if (hit.kind == HitKind.COLUMN) {
      BusMatrixColumn column = matrix.getColumns().get(hit.column);
      return column.label() + " (" + column.physicalTableName() + ")";
    }
    if (hit.kind == HitKind.ROW) {
      BusMatrixRow row = matrix.getRows().get(hit.row);
      return row.factName() + (row.grain().isEmpty() ? "" : " — " + row.grain());
    }
    BusMatrixRow row = matrix.getRows().get(hit.row);
    BusMatrixColumn column = matrix.getColumns().get(hit.column);
    BusMatrixCell cell = row.cell(column.key());
    if (!cell.used()) {
      return row.factName() + " × " + column.label();
    }
    return row.factName() + " × " + column.label() + ": " + cell.tooltip();
  }

  private void paint(Event event) {
    GC gc = event.gc;
    Rectangle client = getClientArea();
    BusMatrixLayout measured =
        BusMatrixLayout.measure(
            matrix, text -> gc.textExtent(Const.NVL(text, "")).x, gc.textExtent("Mg").y);
    if (!measured.equals(layout)) {
      layout = measured;
      updateScrollBars();
    }

    int scrollX = bar(getHorizontalBar());
    int scrollY = bar(getVerticalBar());
    Color border = getDisplay().getSystemColor(SWT.COLOR_GRAY);
    Color headerBg = getDisplay().getSystemColor(SWT.COLOR_DARK_BLUE);
    Color headerFg = getDisplay().getSystemColor(SWT.COLOR_WHITE);
    Color markBg = markBackground();
    Color even = getDisplay().getSystemColor(SWT.COLOR_WHITE);
    Color odd = oddRowBackground();
    Color fg = getDisplay().getSystemColor(SWT.COLOR_BLACK);

    gc.setBackground(even);
    gc.fillRectangle(client);

    int frozen = layout.frozenWidth();
    int headerH = layout.headerHeight();
    int cellW = layout.cellWidth();
    int rowH = layout.rowHeight();

    int firstCol = Math.max(0, scrollX / Math.max(1, cellW));
    int firstRow = Math.max(0, scrollY / Math.max(1, rowH));
    int lastCol =
        Math.min(matrix.getColumns().size() - 1, firstCol + client.width / Math.max(1, cellW) + 2);
    int lastRow =
        Math.min(matrix.getRows().size() - 1, firstRow + client.height / Math.max(1, rowH) + 2);

    Rectangle dataClip =
        new Rectangle(
            frozen,
            headerH,
            Math.max(0, client.width - frozen),
            Math.max(0, client.height - headerH));
    Rectangle rowClip = new Rectangle(0, headerH, frozen, Math.max(0, client.height - headerH));

    for (int r = firstRow; r <= lastRow && r >= 0; r++) {
      BusMatrixRow row = matrix.getRows().get(r);
      int y = layout.cellY(r) - scrollY;
      Color rowBg = r % 2 == 0 ? even : odd;
      boolean groupStart =
          BusMatrixLayout.startsGroup(r == 0 ? null : matrix.getRows().get(r - 1), row);
      for (int c = firstCol; c <= lastCol && c >= 0; c++) {
        BusMatrixColumn column = matrix.getColumns().get(c);
        BusMatrixCell cell = row.cell(column.key());
        int x = layout.cellX(c) - scrollX;
        fillCell(gc, x, y, cellW, rowH, cell.used() ? markBg : rowBg, border, dataClip, groupStart);
        if (cell.used()) {
          gc.setForeground(fg);
          drawCentered(gc, cell.mark(), x, y, cellW, rowH, dataClip);
        }
      }
    }

    for (int r = firstRow; r <= lastRow && r >= 0; r++) {
      BusMatrixRow row = matrix.getRows().get(r);
      int y = layout.cellY(r) - scrollY;
      Color rowBg = r % 2 == 0 ? even : odd;
      boolean groupStart =
          BusMatrixLayout.startsGroup(r == 0 ? null : matrix.getRows().get(r - 1), row);
      for (int i = 0; i < BusMatrixLayout.FROZEN_COLUMNS; i++) {
        int x = layout.labelX(i);
        int w = layout.labelWidth(i);
        fillCell(gc, x, y, w, rowH, rowBg, border, rowClip, groupStart);
        gc.setForeground(fg);
        drawClipped(
            gc,
            BusMatrixLayout.ellipsize(
                BusMatrixLayout.frozenValue(row, i),
                w - BusMatrixLayout.FROZEN_COLUMN_MARGIN,
                text -> gc.textExtent(text).x),
            x + 6,
            y,
            w - 8,
            rowH,
            rowClip);
      }
    }

    Rectangle headerBand = new Rectangle(0, 0, client.width, headerH);
    gc.setClipping(headerBand);
    gc.setBackground(even);
    gc.fillRectangle(frozen, 0, Math.max(0, client.width - frozen), headerH);

    gc.setBackground(headerBg);
    gc.fillRectangle(0, 0, frozen, headerH);
    for (int i = 0; i < BusMatrixLayout.FROZEN_COLUMNS; i++) {
      int x = layout.labelX(i);
      int w = layout.labelWidth(i);
      gc.setForeground(border);
      gc.drawRectangle(x, 0, w, headerH);
      gc.setForeground(headerFg);
      drawClipped(
          gc,
          BusMatrixLayout.FROZEN_HEADERS[i],
          x + 6,
          headerH - BusMatrixLayout.FROZEN_HEADER_BAND - BusMatrixLayout.FROZEN_HEADER_LIFT,
          w - 8,
          BusMatrixLayout.FROZEN_HEADER_BAND,
          null);
    }

    if (!EnvironmentUtils.getInstance().isWeb()) {
      gc.setAntialias(SWT.ON);
      gc.setTextAntialias(SWT.ON);
    }
    int headerFirstCol = Math.max(0, (scrollX - headerH) / Math.max(1, cellW));
    int headerLastCol =
        Math.min(
            matrix.getColumns().size() - 1,
            (scrollX + Math.max(0, client.width - frozen) + headerH) / Math.max(1, cellW) + 1);
    for (int c = headerFirstCol; c <= headerLastCol && c >= 0; c++) {
      int[] pts = layout.headerTrapezium(c, -scrollX, 0);
      gc.setBackground(headerBg);
      gc.fillPolygon(pts);
      gc.setForeground(border);
      gc.drawPolygon(pts);
    }
    gc.setForeground(headerFg);
    int maxLabelPx = BusMatrixLayout.maxLabelPxForHeader(headerH, gc.textExtent("Mg").y);
    for (int c = headerFirstCol; c <= headerLastCol && c >= 0; c++) {
      BusMatrixColumn column = matrix.getColumns().get(c);
      int x = layout.cellX(c) - scrollX;
      drawRotatedHeader(
          gc,
          BusMatrixLayout.ellipsize(
              column.label(), maxLabelPx, text -> gc.textExtent(Const.NVL(text, "")).x),
          x,
          0,
          cellW,
          headerH);
    }
    gc.setClipping(client);
  }

  private void fillCell(GC gc, int x, int y, int w, int h, Color bg, Color border, Rectangle clip) {
    fillCell(gc, x, y, w, h, bg, border, clip, false);
  }

  private void fillCell(
      GC gc,
      int x,
      int y,
      int w,
      int h,
      Color bg,
      Color border,
      Rectangle clip,
      boolean groupStart) {
    Rectangle old = gc.getClipping();
    gc.setClipping(intersect(old, clip, new Rectangle(x, y, w, h)));
    gc.setBackground(bg);
    gc.fillRectangle(x, y, w, h);
    gc.setForeground(border);
    gc.drawRectangle(x, y, w, h);
    if (groupStart) {
      gc.setLineWidth(2);
      gc.drawLine(x, y, x + w, y);
      gc.setLineWidth(1);
    }
    gc.setClipping(old);
  }

  private void drawClipped(GC gc, String text, int x, int y, int w, int h, Rectangle extraClip) {
    if (text == null || text.isEmpty() || w <= 0 || h <= 0) {
      return;
    }
    Rectangle old = gc.getClipping();
    gc.setClipping(intersect(old, extraClip, new Rectangle(x, y, w, h)));
    Point extent = gc.textExtent(text);
    int ty = y + Math.max(0, (h - extent.y) / 2);
    gc.drawText(text, x, ty, true);
    gc.setClipping(old);
  }

  private void drawCentered(GC gc, String text, int x, int y, int w, int h, Rectangle clip) {
    Rectangle old = gc.getClipping();
    gc.setClipping(intersect(old, clip, new Rectangle(x, y, w, h)));
    Point extent = gc.textExtent(text);
    gc.drawText(
        text, x + Math.max(0, (w - extent.x) / 2), y + Math.max(0, (h - extent.y) / 2), true);
    gc.setClipping(old);
  }

  private void drawRotatedHeader(GC gc, String text, int x, int y, int w, int h) {
    if (text == null || text.isEmpty() || w <= 0 || h <= 0) {
      return;
    }
    Point extent = gc.textExtent(text);
    int cx = Math.round(BusMatrixLayout.headerLabelCenterX(x, w, h));
    int cy = Math.round(BusMatrixLayout.headerLabelCenterY(y, h));
    if (EnvironmentUtils.getInstance().isWeb()) {
      gc.drawText(text, cx - extent.x / 2, cy - extent.y / 2, true);
      return;
    }
    gc.setAdvanced(true);
    Transform previous = new Transform(gc.getDevice());
    Transform rotated = new Transform(gc.getDevice());
    try {
      gc.getTransform(previous);
      gc.getTransform(rotated);
      rotated.translate(cx, cy);
      rotated.rotate(BusMatrixLayout.HEADER_TILT_DEGREES);
      gc.setTransform(rotated);
      gc.drawText(text, -extent.x / 2, -extent.y / 2, true);
    } finally {
      gc.setTransform(previous);
      previous.dispose();
      rotated.dispose();
    }
  }

  private static Rectangle intersect(Rectangle a, Rectangle b, Rectangle c) {
    Rectangle result =
        a != null ? new Rectangle(a.x, a.y, a.width, a.height) : new Rectangle(0, 0, 0, 0);
    if (b != null) {
      result.intersect(b);
    }
    if (c != null) {
      result.intersect(c);
    }
    return result;
  }

  private void updateScrollBars() {
    Rectangle client = getClientArea();
    if (client.width <= 0 || client.height <= 0) {
      return;
    }
    int contentW = layout.width(matrix);
    int contentH = layout.height(matrix);
    configure(
        getHorizontalBar(),
        Math.max(0, contentW - layout.frozenWidth()),
        Math.max(1, client.width - layout.frozenWidth()));
    configure(
        getVerticalBar(),
        Math.max(0, contentH - layout.headerHeight()),
        Math.max(1, client.height - layout.headerHeight()));
  }

  private static void configure(ScrollBar bar, int content, int visible) {
    if (bar == null) {
      return;
    }
    int thumb = Math.max(1, visible);
    int max = Math.max(thumb + 1, content);
    bar.setMinimum(0);
    bar.setThumb(Math.min(thumb, max));
    bar.setMaximum(max);
    bar.setVisible(content > visible);
  }

  private Color oddRowBackground() {
    if (oddRowBackground == null || oddRowBackground.isDisposed()) {
      oddRowBackground = new Color(getDisplay(), 242, 244, 247);
    }
    return oddRowBackground;
  }

  private Color markBackground() {
    if (markBackground == null || markBackground.isDisposed()) {
      // Half the previous #d9ecf5 contrast against white.
      markBackground = new Color(getDisplay(), 236, 245, 250);
    }
    return markBackground;
  }

  private static void disposeColor(Color color) {
    if (color != null && !color.isDisposed()) {
      color.dispose();
    }
  }

  private static int bar(ScrollBar bar) {
    return bar != null && bar.getVisible() ? bar.getSelection() : 0;
  }

  enum HitKind {
    ROW,
    COLUMN,
    CELL
  }

  record Hit(HitKind kind, int row, int column) {}
}
