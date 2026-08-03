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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hop.core.SwtUniversalImage;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.svg.SvgFile;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.hopgui.shared.SwtGc;
import org.apache.hop.ui.util.SwtSvgImageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

/**
 * Process-wide cache of model-graph SVG icons drawn on the SWT canvas.
 *
 * <p>Hop's {@code SwtGc.drawImage(SvgFile, ...)} historically created a new {@link
 * SwtUniversalImage} (and SWT {@link Image} bitmaps) on every call without disposing them. Model
 * graphs with many tables (e.g. one hub + dozens of satellites) redraw those icons on every mouse
 * move while dragging, which exhausts native handles ({@code SWTError: No More handles}).
 *
 * <p>This cache loads each SVG once via {@link SwtSvgImageUtil} and paints the shared bitmap
 * through the underlying SWT {@link GC} when the graphics context is {@link SwtGc}. SVG export
 * ({@code SvgGc}) still uses {@link IGc#drawImage(SvgFile, int, int, int, int, float, double)}.
 */
public final class ModelGraphSvgIconCache {

  private static final Map<String, SwtUniversalImage> CACHE = new ConcurrentHashMap<>();
  private static final Object DISPOSE_HOOK_LOCK = new Object();
  private static volatile boolean disposeHookRegistered;

  private ModelGraphSvgIconCache() {}

  /**
   * Draw a model-graph SVG icon at the given logical coordinates.
   *
   * @return {@code true} if the icon was drawn (or a non-SWT fallback path was used successfully)
   */
  public static boolean drawIcon(
      IGc gc,
      ClassLoader classLoader,
      String imagePath,
      int x,
      int y,
      int width,
      int height,
      float magnification) {
    if (gc == null || imagePath == null || imagePath.isEmpty() || width <= 0 || height <= 0) {
      return false;
    }

    try {
      if (gc instanceof SwtGc swtGc
          && tryDrawCachedSwt(swtGc, classLoader, imagePath, x, y, width, height, magnification)) {
        return true;
      }
      // SvgGc (export) and any other IGc: embed/draw via the portable SvgFile path.
      gc.drawImage(new SvgFile(imagePath, classLoader), x, y, width, height, magnification, 0);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean tryDrawCachedSwt(
      SwtGc swtGc,
      ClassLoader classLoader,
      String imagePath,
      int x,
      int y,
      int width,
      int height,
      float magnification) {
    Display display = Display.getCurrent();
    if (display == null || display.isDisposed()) {
      return false;
    }
    GC nativeGc;
    try {
      nativeGc = swtGc.getNativeGc();
    } catch (LinkageError e) {
      // Older hop-ui without getNativeGc(); fall back to IGc.drawImage(SvgFile).
      return false;
    }
    if (nativeGc == null || nativeGc.isDisposed()) {
      return false;
    }

    boolean darkMode = PropsUi.getInstance().isDarkMode();
    String cacheKey = imagePath + (darkMode ? "|dark" : "|light");
    ensureDisposeHook(display);

    SwtUniversalImage universal =
        CACHE.computeIfAbsent(
            cacheKey, key -> SwtSvgImageUtil.getUniversalImage(display, classLoader, imagePath));
    if (universal == null) {
      return false;
    }

    int magnifiedWidth = Math.max(1, Math.round(width * magnification));
    int magnifiedHeight = Math.max(1, Math.round(height * magnification));
    Image bitmap = universal.getAsBitmapForSize(display, magnifiedWidth, magnifiedHeight);
    if (bitmap == null || bitmap.isDisposed()) {
      return false;
    }

    Rectangle bounds = bitmap.getBounds();
    nativeGc.drawImage(bitmap, 0, 0, bounds.width, bounds.height, x, y, width, height);
    return true;
  }

  private static void ensureDisposeHook(Display display) {
    if (disposeHookRegistered) {
      return;
    }
    synchronized (DISPOSE_HOOK_LOCK) {
      if (disposeHookRegistered) {
        return;
      }
      display.addListener(
          SWT.Dispose,
          event -> {
            for (SwtUniversalImage image : CACHE.values()) {
              try {
                image.dispose();
              } catch (Exception ignored) {
                // best-effort at display shutdown
              }
            }
            CACHE.clear();
          });
      disposeHookRegistered = true;
    }
  }
}
