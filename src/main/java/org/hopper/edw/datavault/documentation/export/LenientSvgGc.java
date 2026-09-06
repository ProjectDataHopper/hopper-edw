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
package org.hopper.edw.datavault.documentation.export;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.SvgGc;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.core.svg.SvgFile;
import org.apache.hop.core.util.Utils;

/**
 * {@link SvgGc} that skips missing plugin icons instead of aborting the whole canvas. Headless
 * {@code hop} / {@code hop-run} loads some transform images from the plugin classloader; if that
 * misses, Hop falls back to VFS against the working directory and throws.
 */
public final class LenientSvgGc extends SvgGc {

  private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

  public LenientSvgGc(
      HopSvgGraphics2D graphics2D,
      Point area,
      int iconSize,
      int xOffset,
      int yOffset,
      boolean darkMode)
      throws HopException {
    super(graphics2D, area, iconSize, xOffset, yOffset, darkMode);
  }

  @Override
  public void drawImage(
      SvgFile svgFile,
      int x,
      int y,
      int desiredWidth,
      int desiredHeight,
      float magnification,
      double angle)
      throws HopException {
    try {
      super.drawImage(svgFile, x, y, desiredWidth, desiredHeight, magnification, angle);
    } catch (Exception e) {
      warnOnce(svgFile, e);
      drawPlaceholder(x, y, desiredWidth, desiredHeight);
    }
  }

  private void drawPlaceholder(int x, int y, int width, int height) {
    try {
      setLineWidth(1);
      setForeground(EColor.GRAY);
      setBackground(EColor.LIGHTGRAY);
      fillRectangle(x, y, Math.max(1, width), Math.max(1, height));
      drawRectangle(x, y, Math.max(1, width), Math.max(1, height));
    } catch (Exception ignored) {
      // Placeholder is best-effort; the rest of the canvas should still render.
    }
  }

  private static void warnOnce(SvgFile svgFile, Exception e) {
    String name = svgFile != null ? svgFile.getFilename() : "(null)";
    if (Utils.isEmpty(name) || !WARNED.add(name)) {
      return;
    }
    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    reason = reason.replace('\n', ' ').replace('\r', ' ').trim();
    if (reason.length() > 180) {
      reason = reason.substring(0, 177) + "...";
    }
    LogChannel.GENERAL.logBasic(
        "Documentation SVG: missing icon '" + name + "', drawing a placeholder (" + reason + ")");
  }
}
