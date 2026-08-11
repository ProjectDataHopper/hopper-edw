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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.NotePadStyle;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.SvgGc;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.widgets.Canvas;

/**
 * Publishes widget data consumed by Hop Web's {@code canvas-svg.js} / {@code canvas.js} without a
 * compile-time RAP dependency. Desktop builds no-op.
 */
public final class ModelGraphWebCanvasData {

  /** Default model-card size when a painter has not measured the box yet. */
  public static final int DEFAULT_CARD_WIDTH = 140;

  public static final int DEFAULT_CARD_HEIGHT = 70;

  /**
   * Canvas node for client drag/hop previews. Coordinates and sizes are in graph (logical) units.
   *
   * @param width card width (0 = client falls back to iconSize)
   * @param height card height (0 = client falls back to iconSize)
   */
  public record NodePos(int x, int y, boolean selected, int width, int height) {
    public NodePos(int x, int y, boolean selected) {
      this(x, y, selected, DEFAULT_CARD_WIDTH, DEFAULT_CARD_HEIGHT);
    }

    public static NodePos of(int x, int y, boolean selected, int width, int height) {
      return new NodePos(
          x,
          y,
          selected,
          width > 0 ? width : DEFAULT_CARD_WIDTH,
          height > 0 ? height : DEFAULT_CARD_HEIGHT);
    }
  }

  public record NotePos(int x, int y, int width, int height, boolean selected, String note) {}

  private ModelGraphWebCanvasData() {}

  /**
   * Creates an {@link SvgGc} with Hop GUI dark-mode settings (notes + theme colors).
   *
   * <p>When {@link PropsUi} cannot initialize (unit tests / headless without RCP {@code
   * TextSizeUtilFacadeImpl}), falls back to light mode so SVG generation still works.
   */
  public static SvgGc createSvgGc(HopSvgGraphics2D graphics2D, Point canvasSize, int iconSize)
      throws HopException {
    boolean darkMode = false;
    Map<String, String> contrasting = null;
    try {
      PropsUi propsUi = PropsUi.getInstance();
      darkMode = propsUi.isDarkMode();
      if (darkMode) {
        contrasting = propsUi.getContrastingColorStrings();
      }
    } catch (Throwable t) {
      // PropsUi needs TextSizeUtilFacadeImpl (RCP/RAP only). Safe for SVG unit tests.
      LogChannel.GENERAL.logDebug(
          "PropsUi unavailable for SVG canvas; using light mode: " + t.getMessage());
    }
    NotePadStyle.setDarkMode(darkMode);
    return new SvgGc(graphics2D, canvasSize, iconSize, 0, 0, darkMode, contrasting);
  }

  /**
   * Sets canvas {@code nodes} as a RAP {@code JsonObject} map of name → {x,y,selected,width,height}
   * for client multi-select drag previews and hop candidate anchors.
   */
  public static void setNodes(Canvas canvas, Map<String, NodePos> nodes) {
    if (canvas == null
        || canvas.isDisposed()
        || nodes == null
        || !EnvironmentUtils.getInstance().isWeb()) {
      return;
    }
    try {
      Class<?> jsonObjectClass = Class.forName("org.eclipse.rap.json.JsonObject");
      Object root = jsonObjectClass.getDeclaredConstructor().newInstance();
      Method addInt = jsonObjectClass.getMethod("add", String.class, int.class);
      Method addBoolean = jsonObjectClass.getMethod("add", String.class, boolean.class);
      Method addJson = jsonObjectClass.getMethod("add", String.class, jsonObjectClass);

      for (Map.Entry<String, NodePos> entry : nodes.entrySet()) {
        if (entry.getKey() == null || entry.getValue() == null) {
          continue;
        }
        NodePos pos = entry.getValue();
        Object node = jsonObjectClass.getDeclaredConstructor().newInstance();
        addInt.invoke(node, "x", pos.x());
        addInt.invoke(node, "y", pos.y());
        addBoolean.invoke(node, "selected", pos.selected());
        addInt.invoke(node, "width", pos.width() > 0 ? pos.width() : DEFAULT_CARD_WIDTH);
        addInt.invoke(node, "height", pos.height() > 0 ? pos.height() : DEFAULT_CARD_HEIGHT);
        addJson.invoke(root, entry.getKey(), node);
      }
      canvas.setData("nodes", root);
    } catch (ReflectiveOperationException e) {
      LogChannel.UI.logDebug("Unable to publish Hop Web canvas nodes: " + e.getMessage());
    }
  }

  /**
   * Sets canvas {@code notes} as a RAP {@code JsonArray} for client note outlines and resize
   * handles ({@code canvas.js}).
   */
  public static void setNotes(Canvas canvas, List<NotePos> notes) {
    if (canvas == null
        || canvas.isDisposed()
        || notes == null
        || !EnvironmentUtils.getInstance().isWeb()) {
      return;
    }
    try {
      Class<?> jsonObjectClass = Class.forName("org.eclipse.rap.json.JsonObject");
      Class<?> jsonArrayClass = Class.forName("org.eclipse.rap.json.JsonArray");
      Object array = jsonArrayClass.getDeclaredConstructor().newInstance();
      Method addInt = jsonObjectClass.getMethod("add", String.class, int.class);
      Method addBoolean = jsonObjectClass.getMethod("add", String.class, boolean.class);
      Method addString = jsonObjectClass.getMethod("add", String.class, String.class);
      Method arrayAdd = jsonArrayClass.getMethod("add", jsonObjectClass);

      for (NotePos note : notes) {
        if (note == null) {
          continue;
        }
        Object jsonNote = jsonObjectClass.getDeclaredConstructor().newInstance();
        addInt.invoke(jsonNote, "x", note.x());
        addInt.invoke(jsonNote, "y", note.y());
        addInt.invoke(jsonNote, "width", note.width());
        addInt.invoke(jsonNote, "height", note.height());
        addBoolean.invoke(jsonNote, "selected", note.selected());
        addString.invoke(jsonNote, "note", note.note() != null ? note.note() : "");
        arrayAdd.invoke(array, jsonNote);
      }
      canvas.setData("notes", array);
    } catch (ReflectiveOperationException e) {
      LogChannel.UI.logDebug("Unable to publish Hop Web canvas notes: " + e.getMessage());
    }
  }

  public static void clearMode(Canvas canvas) {
    if (canvas != null && !canvas.isDisposed() && EnvironmentUtils.getInstance().isWeb()) {
      canvas.setData("mode", "null");
      canvas.setData("startHopNode", null);
      canvas.setData("resizeDirection", null);
    }
  }

  /**
   * Ensure nodes/hops/notes widget data exist so Hop Web {@code canvas.js} never sees null (Paint
   * calls {@code hops.forEach} / {@code notes.forEach}).
   */
  public static void ensureEmptyCollections(Canvas canvas) {
    if (canvas == null || canvas.isDisposed() || !EnvironmentUtils.getInstance().isWeb()) {
      return;
    }
    if (canvas.getData("nodes") == null) {
      setNodes(canvas, Map.of());
    }
    if (canvas.getData("notes") == null) {
      setNotes(canvas, List.of());
    }
    if (canvas.getData("hops") == null) {
      setHops(canvas, List.of());
    }
  }

  /** Empty hop list for model graphs (relationships are painted in SVG, not as hop JSON). */
  public static void setHops(Canvas canvas, List<?> hops) {
    if (canvas == null || canvas.isDisposed() || !EnvironmentUtils.getInstance().isWeb()) {
      return;
    }
    try {
      Class<?> jsonArrayClass = Class.forName("org.eclipse.rap.json.JsonArray");
      Object array = jsonArrayClass.getDeclaredConstructor().newInstance();
      // Model graphs do not use pipeline-style hop JSON; always publish empty for canvas.js.
      canvas.setData("hops", array);
    } catch (ReflectiveOperationException e) {
      LogChannel.UI.logDebug("Unable to publish Hop Web canvas hops: " + e.getMessage());
    }
  }
}
