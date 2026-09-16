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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.hop.core.SwtUniversalImageSvg;
import org.apache.hop.core.gui.CanvasSvgRenderResult;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.svg.SvgImage;
import org.apache.hop.core.svg.SvgSupport;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.hopgui.CanvasFacade;
import org.apache.hop.ui.hopgui.CanvasListener;
import org.apache.hop.ui.hopgui.CanvasSvgFacade;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.hopper.edw.datavault.busmatrix.BusMatrix;
import org.hopper.edw.datavault.busmatrix.BusMatrixLayout;
import org.hopper.edw.datavault.busmatrix.BusMatrixSvgPainter;
import org.hopper.edw.datavault.documentation.model.SvgDocument;
import org.hopper.edw.datavault.documentation.model.SvgHit;
import org.hopper.edw.datavault.hopgui.file.modelgraph.ModelGraphWebCanvasData;

/**
 * Shows a bus matrix as a single SVG (same drawing as export and project documentation). Desktop
 * scrolls the whole picture; Hop Web uses the model-graph SVG overlay.
 */
final class BusMatrixCanvas extends Composite {

  private final boolean web;
  private final Canvas canvas;
  private final ScrolledComposite scroll;
  private BusMatrix matrix = new BusMatrix("", null, null, null);
  private List<SvgHit> hits = List.of();
  private int svgWidth = 320;
  private int svgHeight = 240;
  private float magnification = 1.0f;
  private DPoint offset = new DPoint(0, 0);
  private SwtUniversalImageSvg desktopSvg;
  private Consumer<SvgHit> hitListener;
  private String lastSvgXml = "";

  BusMatrixCanvas(Composite parent) {
    super(parent, SWT.NONE);
    setLayout(new FillLayout());
    web = EnvironmentUtils.getInstance().isWeb();
    if (web) {
      scroll = null;
      canvas = new Canvas(this, SWT.NO_BACKGROUND);
      setupWebCanvas();
    } else {
      scroll = new ScrolledComposite(this, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
      scroll.setExpandHorizontal(true);
      scroll.setExpandVertical(true);
      canvas = new Canvas(scroll, SWT.NONE);
      scroll.setContent(canvas);
      canvas.addListener(SWT.Paint, this::paintDesktop);
    }
    PropsUi.setLook(this);
    PropsUi.setLook(canvas);
    canvas.addListener(SWT.MouseMove, this::onMove);
    canvas.addListener(SWT.MouseDoubleClick, this::onDoubleClick);
    canvas.addListener(SWT.Resize, e -> redrawSurface());
    addDisposeListener(
        e -> {
          if (web) {
            CanvasSvgFacade.unregisterCanvas(canvas);
          }
          disposeDesktopSvg();
        });
  }

  void setMatrix(BusMatrix matrix) {
    this.matrix = matrix != null ? matrix : new BusMatrix("", null, null, null);
    rebuildSvg();
  }

  BusMatrix getMatrix() {
    return matrix;
  }

  void setHitListener(Consumer<SvgHit> hitListener) {
    this.hitListener = hitListener;
  }

  void zoomIn() {
    setMagnification(magnification + 0.1f);
  }

  void zoomOut() {
    setMagnification(magnification - 0.1f);
  }

  void zoom100Percent() {
    setMagnification(1.0f);
  }

  void zoomFitSize() {
    Rectangle client = visibleClient();
    if (svgWidth <= 0 || svgHeight <= 0 || client.width <= 0 || client.height <= 0) {
      return;
    }
    float mx = client.width / (float) svgWidth;
    float my = client.height / (float) svgHeight;
    setMagnification(Math.min(mx, my) * 0.95f);
  }

  void zoomFitWidth() {
    Rectangle client = visibleClient();
    if (svgWidth <= 0 || client.width <= 0) {
      return;
    }
    setMagnification(client.width / (float) svgWidth);
  }

  private void setMagnification(float value) {
    magnification = Math.max(0.1f, Math.min(10f, value));
    offset = new DPoint(0, 0);
    applyZoom();
  }

  private Rectangle visibleClient() {
    if (web) {
      return canvas.getClientArea();
    }
    return scroll != null ? scroll.getClientArea() : canvas.getClientArea();
  }

  private void applyZoom() {
    if (web) {
      publishWebSvg(lastSvgXml);
      canvas.redraw();
      return;
    }
    int w = Math.max(1, Math.round(svgWidth * magnification));
    int h = Math.max(1, Math.round(svgHeight * magnification));
    canvas.setSize(w, h);
    if (scroll != null) {
      scroll.setMinSize(w, h);
    }
    canvas.redraw();
  }

  SvgHit hitAt(int x, int y) {
    int gx = Math.round(x / magnification - (float) offset.x);
    int gy = Math.round(y / magnification - (float) offset.y);
    for (int i = hits.size() - 1; i >= 0; i--) {
      SvgHit hit = hits.get(i);
      if (hit != null
          && gx >= hit.x()
          && gx < hit.x() + hit.w()
          && gy >= hit.y()
          && gy < hit.y() + hit.h()) {
        return hit;
      }
    }
    return null;
  }

  private void setupWebCanvas() {
    Listener canvasListener = CanvasListener.getInstance();
    canvas.addListener(SWT.MouseDown, canvasListener);
    canvas.addListener(SWT.MouseMove, canvasListener);
    canvas.addListener(SWT.MouseUp, canvasListener);
    canvas.addListener(SWT.Paint, canvasListener);
    canvas.addListener(SWT.MouseWheel, canvasListener);
    canvas.addListener(SWT.MouseVerticalWheel, canvasListener);
    canvas.addListener(SWT.Paint, this::paintWebBackground);
    CanvasSvgFacade.registerCanvas(canvas, this);
    CanvasSvgFacade.ensureInteractionHandler(this, canvas);
    ModelGraphWebCanvasData.ensureEmptyCollections(canvas);
  }

  private void rebuildSvg() {
    // Always paint the light SVG. Desktop Batik and Hop Web both apply Hop's contrast map for
    // dark mode (same as model graphs). Painting a pre-darkened SVG on Web is restyled again by
    // RAP dark-mode.css (`* { color; background-color }`).
    SvgDocument document = BusMatrixSvgPainter.paintDocument(matrix, false, null);
    String svg = contrastIfDark(document.getLightSvg());
    lastSvgXml = svg != null ? svg : "";
    hits = List.copyOf(document.getHits());
    BusMatrixLayout layout = BusMatrixSvgPainter.layoutOf(matrix);
    svgWidth = layout.width(matrix);
    svgHeight = layout.height(matrix);
    if (web) {
      publishWebSvg(lastSvgXml);
    } else {
      rebuildDesktopImage(lastSvgXml);
    }
    applyZoom();
  }

  static String contrastIfDark(String svg) {
    if (svg == null || svg.isBlank()) {
      return svg;
    }
    try {
      if (!PropsUi.getInstance().isDarkMode()) {
        return svg;
      }
      Map<String, String> map = PropsUi.getInstance().getContrastingColorStrings();
      if (map == null || map.isEmpty()) {
        return svg;
      }
      String out = svg;
      for (Map.Entry<String, String> entry : map.entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null) {
          out = out.replace(entry.getKey(), entry.getValue());
        }
      }
      return out;
    } catch (Throwable ignored) {
      return svg;
    }
  }

  private void publishWebSvg(String svg) {
    Rectangle client = canvas.getClientArea();
    int viewW = Math.max(1, client.width);
    int viewH = Math.max(1, client.height);
    CanvasSvgRenderResult result =
        new CanvasSvgRenderResult(
            svg,
            List.of(),
            new org.apache.hop.core.gui.Rectangle(0, 0, viewW, viewH),
            new org.apache.hop.core.gui.Rectangle(0, 0, svgWidth, svgHeight));
    CanvasSvgFacade.publishSnapshot(
        canvas, result, magnification, offset, new org.apache.hop.core.gui.Point(viewW, viewH));
    CanvasFacade.setData(canvas, magnification, offset, matrix);
    ModelGraphWebCanvasData.ensureEmptyCollections(canvas);
  }

  private void rebuildDesktopImage(String svg) {
    disposeDesktopSvg();
    if (svg == null || svg.isBlank()) {
      return;
    }
    try {
      SvgImage loaded =
          SvgSupport.loadSvgImage(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
      desktopSvg = new SwtUniversalImageSvg(loaded, true);
      // Size is applied in applyZoom().
    } catch (Exception e) {
      LogChannel.UI.logError("Failed to rasterize bus matrix SVG", e);
    }
  }

  private void paintDesktop(Event event) {
    GC gc = event.gc;
    Rectangle client = canvas.getClientArea();
    gc.setBackground(GuiResource.getInstance().getColorBackground());
    gc.fillRectangle(client);
    if (desktopSvg == null) {
      return;
    }
    int w = Math.max(1, Math.round(svgWidth * magnification));
    int h = Math.max(1, Math.round(svgHeight * magnification));
    Image bitmap = desktopSvg.getAsBitmapForSize(getDisplay(), w, h);
    gc.drawImage(bitmap, 0, 0);
  }

  private void paintWebBackground(Event event) {
    Rectangle client = canvas.getClientArea();
    if (client.width <= 0 || client.height <= 0) {
      return;
    }
    publishWebSvg(lastSvgXml);
    event.gc.setBackground(GuiResource.getInstance().getColorBackground());
    event.gc.fillRectangle(client);
  }

  private void onMove(Event event) {
    SvgHit hit = hitAt(event.x, event.y);
    canvas.setToolTipText(hit == null ? null : hit.name());
  }

  private void onDoubleClick(Event event) {
    if (hitListener == null) {
      return;
    }
    SvgHit hit = hitAt(event.x, event.y);
    if (hit != null) {
      hitListener.accept(hit);
    }
  }

  private void redrawSurface() {
    if (canvas != null && !canvas.isDisposed()) {
      canvas.redraw();
    }
  }

  private void disposeDesktopSvg() {
    if (desktopSvg != null) {
      desktopSvg.dispose();
      desktopSvg = null;
    }
  }
}
