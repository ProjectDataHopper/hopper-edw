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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.apache.hop.core.SwtUniversalImageSvg;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.svg.SvgImage;
import org.apache.hop.core.svg.SvgSupport;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
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
import org.hopper.edw.datavault.busmatrix.BusMatrix;
import org.hopper.edw.datavault.busmatrix.BusMatrixHit;
import org.hopper.edw.datavault.busmatrix.BusMatrixLayout;
import org.hopper.edw.datavault.busmatrix.BusMatrixSvgPainter;
import org.hopper.edw.datavault.documentation.model.SvgDocument;
import org.hopper.edw.datavault.documentation.model.SvgHit;

/**
 * Shows a bus matrix as a single SVG (same drawing as export and project documentation). Desktop
 * scrolls the whole picture; Hop Web renders an isolated SVG overlay inside the canvas container.
 */
final class BusMatrixCanvas extends Composite {

  private static final ObjectMapper JSON = new ObjectMapper();

  @FunctionalInterface
  interface HitClickListener {
    void onClick(BusMatrixHit hit, Event event);
  }

  private final boolean web;
  private final Canvas canvas;
  private final ScrolledComposite scroll;
  private BusMatrix matrix = new BusMatrix("", null, null, null);
  private List<SvgHit> hits = List.of();
  private int svgWidth = 320;
  private int svgHeight = 240;
  private float magnification = 1.0f;
  private SwtUniversalImageSvg desktopSvg;
  private Consumer<SvgHit> hitListener;
  private HitClickListener hitClickListener;
  private String lastSvgXml = "";
  private boolean lastDark;

  BusMatrixCanvas(Composite parent) {
    super(parent, SWT.NONE);
    setLayout(new FillLayout());
    web = EnvironmentUtils.getInstance().isWeb();
    scroll = new ScrolledComposite(this, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
    scroll.setExpandHorizontal(true);
    scroll.setExpandVertical(true);
    canvas = new Canvas(scroll, web ? SWT.NO_BACKGROUND : SWT.NONE);
    scroll.setContent(canvas);
    if (web) {
      canvas.addListener(SWT.Paint, this::paintWebBackground);
    } else {
      canvas.addListener(SWT.Paint, this::paintDesktop);
    }
    PropsUi.setLook(this);
    PropsUi.setLook(canvas);
    canvas.addListener(SWT.MouseMove, this::onMove);
    canvas.addListener(SWT.MouseDown, this::onMouseDown);
    canvas.addListener(SWT.MouseDoubleClick, this::onDoubleClick);
    canvas.addListener(SWT.Resize, e -> redrawSurface());
    addDisposeListener(e -> disposeDesktopSvg());
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

  void setHitClickListener(HitClickListener listener) {
    this.hitClickListener = listener;
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
    applyZoom();
  }

  private Rectangle visibleClient() {
    return scroll != null ? scroll.getClientArea() : canvas.getClientArea();
  }

  private void applyZoom() {
    int w = Math.max(1, Math.round(svgWidth * magnification));
    int h = Math.max(1, Math.round(svgHeight * magnification));
    canvas.setSize(w, h);
    if (scroll != null) {
      scroll.setMinSize(w, h);
    }
    if (web) {
      renderWebSvg(lastSvgXml);
    }
    canvas.redraw();
  }

  private boolean showGranularity = false;

  boolean isShowGranularity() {
    return showGranularity;
  }

  void setShowGranularity(boolean showGranularity) {
    if (this.showGranularity != showGranularity) {
      this.showGranularity = showGranularity;
      rebuildSvg();
    }
  }

  BusMatrixHit hitAt(int x, int y) {
    int gx = Math.round(x / magnification);
    int gy = Math.round(y / magnification);
    BusMatrixLayout layout = BusMatrixSvgPainter.layoutOf(matrix, showGranularity);
    return BusMatrixHit.hitAt(gx, gy, matrix, layout);
  }

  private void rebuildSvg() {
    boolean dark = isDarkMode();
    lastDark = dark;
    SvgDocument document = BusMatrixSvgPainter.paintDocument(matrix, dark, null, showGranularity);
    String svg = dark ? document.getDarkSvg() : document.getLightSvg();
    lastSvgXml = svg != null ? svg : "";
    hits = List.copyOf(document.getHits());
    BusMatrixLayout layout = BusMatrixSvgPainter.layoutOf(matrix, showGranularity);
    svgWidth = layout.width(matrix);
    svgHeight = layout.height(matrix);
    if (web) {
      renderWebSvg(lastSvgXml);
    } else {
      rebuildDesktopImage(lastSvgXml);
    }
    applyZoom();
  }

  static boolean isDarkMode() {
    try {
      return PropsUi.getInstance().isDarkMode();
    } catch (Throwable ignored) {
      return false;
    }
  }

  private void renderWebSvg(String svg) {
    if (canvas == null || canvas.isDisposed() || svg == null || svg.isBlank()) {
      return;
    }
    try {
      Class<?> widgetUtilClass = Class.forName("org.eclipse.rap.rwt.widgets.WidgetUtil");
      String canvasId =
          (String)
              widgetUtilClass
                  .getMethod("getId", org.eclipse.swt.widgets.Widget.class)
                  .invoke(null, canvas);
      if (canvasId == null || canvasId.isBlank()) {
        return;
      }
      Class<?> rwtClass = Class.forName("org.eclipse.rap.rwt.RWT");
      Object client = rwtClass.getMethod("getClient").invoke(null);
      Class<?> jsExecClass =
          Class.forName("org.eclipse.rap.rwt.client.service.JavaScriptExecutor");
      Object executor =
          client.getClass().getMethod("getService", Class.class).invoke(client, jsExecClass);

      String escapedSvg = JSON.writeValueAsString(svg);
      String escapedId = JSON.writeValueAsString(canvasId);

      String script =
          "(function() {"
              + "var id = "
              + escapedId
              + ";"
              + "var svgXml = "
              + escapedSvg
              + ";"
              + "function getEl(widgetId) {"
              + "  try {"
              + "    if (typeof rap !== 'undefined' && rap.getObject) {"
              + "      var p = rap.getObject(widgetId);"
              + "      if (p && p.$el) {"
              + "        var q = p.$el.get ? p.$el.get(0) : (p.$el[0] || p.$el);"
              + "        if (q && q.tagName) return q;"
              + "      }"
              + "    }"
              + "    if (typeof rwt !== 'undefined' && rwt.remote && rwt.remote.ObjectRegistry) {"
              + "      var nw = rwt.remote.ObjectRegistry.getObject(widgetId);"
              + "      if (nw) {"
              + "        if (typeof nw.getElement === 'function') {"
              + "          var el = nw.getElement();"
              + "          if (el) return el;"
              + "        }"
              + "        if (nw._element) return nw._element;"
              + "      }"
              + "    }"
              + "  } catch (e) {}"
              + "  return document.getElementById(widgetId);"
              + "}"
              + "var canvasEl = getEl(id);"
              + "if (!canvasEl) return;"
              + "var container = canvasEl.tagName === 'CANVAS' ? canvasEl.parentElement : canvasEl;"
              + "if (!container) return;"
              + "if (window.getComputedStyle(container).position === 'static') {"
              + "  container.style.position = 'relative';"
              + "}"
              + "var overlay = container.querySelector('[data-hop-bus-matrix-overlay]');"
              + "if (!overlay) {"
              + "  overlay = document.createElement('div');"
              + "  overlay.setAttribute('data-hop-bus-matrix-overlay', 'true');"
              + "  overlay.style.position = 'absolute';"
              + "  overlay.style.left = '0';"
              + "  overlay.style.top = '0';"
              + "  overlay.style.width = '100%';"
              + "  overlay.style.height = '100%';"
              + "  overlay.style.pointerEvents = 'auto';"
              + "  overlay.style.overflow = 'hidden';"
              + "  overlay.style.zIndex = '10';"
              + "  ['mousedown', 'mouseup', 'click', 'dblclick'].forEach(function(eventType) {"
              + "    overlay.addEventListener(eventType, function(e) {"
              + "      overlay.style.pointerEvents = 'none';"
              + "      var target = document.elementFromPoint(e.clientX, e.clientY);"
              + "      if (target && target !== overlay) {"
              + "        var evt = new MouseEvent(eventType, {"
              + "          bubbles: true,"
              + "          cancelable: true,"
              + "          view: window,"
              + "          clientX: e.clientX,"
              + "          clientY: e.clientY,"
              + "          screenX: e.screenX,"
              + "          screenY: e.screenY,"
              + "          button: e.button,"
              + "          buttons: e.buttons"
              + "        });"
              + "        target.dispatchEvent(evt);"
              + "      }"
              + "      overlay.style.pointerEvents = 'auto';"
              + "    });"
              + "  });"
              + "  container.appendChild(overlay);"
              + "}"
              + "overlay.innerHTML = svgXml;"
              + "var svg = overlay.querySelector('svg');"
              + "if (svg) {"
              + "  svg.style.width = '100%';"
              + "  svg.style.height = '100%';"
              + "  svg.style.display = 'block';"
              + "  svg.style.pointerEvents = 'auto';"
              + "}"
              + "})();";

      jsExecClass.getMethod("execute", String.class).invoke(executor, script);
    } catch (Exception e) {
      LogChannel.UI.logError("Failed to render bus matrix web SVG", e);
    }
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
    if (isDarkMode() != lastDark) {
      rebuildSvg();
    } else {
      renderWebSvg(lastSvgXml);
    }
    event.gc.setBackground(GuiResource.getInstance().getColorBackground());
    event.gc.fillRectangle(client);
  }

  private void onMove(Event event) {
    BusMatrixHit hit = hitAt(event.x, event.y);
    canvas.setToolTipText(hit != null && hit.hasTooltip() ? hit.tooltip() : null);
  }

  private void onMouseDown(Event event) {
    if (hitClickListener == null) {
      return;
    }
    BusMatrixHit hit = hitAt(event.x, event.y);
    if (hit != null && hit.hasAction()) {
      hitClickListener.onClick(hit, event);
    }
  }

  private void onDoubleClick(Event event) {
    BusMatrixHit hit = hitAt(event.x, event.y);
    if (hit != null && hit.hasAction()) {
      if (hitClickListener != null) {
        hitClickListener.onClick(hit, event);
      } else if (hitListener != null) {
        String name = hit.targetName();
        String type = hit.isDimension() ? "dimension" : "fact";
        hitListener.accept(new SvgHit(type, name, event.x, event.y, 10, 10, "#"));
      }
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
