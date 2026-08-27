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
package org.hopper.edw.datavault.hopgui.file.vault;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.NotePadType;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.IGc;
import org.apache.hop.core.gui.IGc.EColor;
import org.apache.hop.core.gui.IGc.EFont;
import org.apache.hop.core.gui.IGc.EImage;
import org.apache.hop.core.gui.IGc.ELineStyle;
import org.apache.hop.core.gui.NotePadStyle;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.Rectangle;
import org.apache.hop.core.gui.markdown.MarkdownNoteRenderer;
import org.apache.hop.core.gui.markdown.MarkdownNoteRenderer.PositionedLink;
import org.apache.hop.core.gui.markdown.NoteLinkHit;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.hopper.edw.datavault.metadata.DvNote;

@Getter
@Setter
public abstract class BasePainter {
  private static final Class<?> PKG = org.apache.hop.core.gui.BasePainter.class;

  public final double theta = Math.toRadians(11); // arrowhead sharpness

  public static final int MINI_ICON_MARGIN = 5;
  public static final int CORNER_RADIUS_5 = 10;
  protected static final int NOTE_ICON_SIZE = 16;
  protected static final int NOTE_ICON_TEXT_GAP = 4;

  protected double zoomFactor;

  protected Point area;

  protected List<AreaOwner> areaOwners;

  protected DPoint offset;
  protected int iconSize;
  protected int miniIconSize;
  protected int gridSize;
  protected Rectangle selectionRegion;
  protected float magnification;

  protected Object subject;
  protected IVariables variables;

  protected IGc gc;

  protected Point maximum;
  protected boolean showingNavigationView;
  protected float screenMagnification;
  protected Rectangle graphPort;
  protected Rectangle viewPort;
  protected String mouseOverTableName;

  /**
   * Hovered Markdown link (Hop {@link NoteLinkHit}); identity of note pad matches canvas adapter.
   */
  protected NoteLinkHit mouseOverNoteLink;

  /**
   * Host model filename for resolving relative Markdown image paths (same role as pipeline filename
   * in Hop painters).
   */
  protected String noteImageBaseFilename;

  /**
   * Navigation minimap mapping info captured during last drawNavigationView (for panning support).
   */
  protected double navigationScale;

  protected double navigationGraphOriginX;
  protected double navigationGraphOriginY;

  /**
   * When true, draw the origin boundary (dashed lines, hatching, and label). Should be set from the
   * UI based on "Enable infinite move" (only show when infinite move is enabled).
   */
  protected boolean showOriginBoundary;

  /** When false, notes are omitted from the canvas (e.g. {@code hop svg --no-notes}). */
  protected boolean drawNotes = true;

  /** In case we want to use metadata objects to help with drawing on the pipeline or workflow */
  protected IHopMetadataProvider metadataProvider;

  protected BasePainter(IGc gc, IVariables variables, Object subject, Point area) {
    this.gc = gc;
    this.variables = variables;
    this.subject = subject;
    this.area = area;

    gc.setAntialias(true);
  }

  protected Point real2screen(int x, int y) {
    return new Point((int) (x + offset.x), (int) (y + offset.y));
  }

  /**
   * Returns the canvas coordinates used when painting a note. Adds the graph offset manually so
   * notes align with tables drawn via {@link #real2screen(int, int)} and with {@code screen2real}
   * from {@link org.apache.hop.ui.hopgui.perspective.execution.DragViewZoomBase}.
   */
  protected Point noteDrawLocation(int logicalX, int logicalY) {
    return real2screen(logicalX, logicalY);
  }

  protected Point magnifyPoint(Point p) {
    return new Point(Math.round(p.x * magnification), Math.round(p.y * magnification));
  }

  /** Converts a screen-space point to logical draw coordinates under the active graph transform. */
  protected Point screenDragEndToLogical(Point screenEnd) {
    if (screenEnd == null) {
      return null;
    }
    if (magnification <= 0) {
      return new Point((int) (screenEnd.x - offset.x), (int) (screenEnd.y - offset.y));
    }
    return new Point(
        (int) ((screenEnd.x - offset.x) / magnification),
        (int) ((screenEnd.y - offset.y) / magnification));
  }

  protected void drawRect(Rectangle rect) {
    if (rect == null) {
      return;
    }
    gc.setLineStyle(ELineStyle.DASHDOT);
    gc.setForeground(EColor.BLACK);
    // SWT on Windows doesn't cater for negative rect.width/height so handle here.
    Point s = new Point(rect.x, rect.y);
    if (rect.width < 0) {
      s.x = s.x + rect.width;
    }
    if (rect.height < 0) {
      s.y = s.y + rect.height;
    }
    gc.drawRectangle(s.x, s.y, Math.abs(rect.width), Math.abs(rect.height));
    gc.setLineStyle(ELineStyle.SOLID);
  }

  /**
   * Maximum grid points to draw; avoids severe slowdown on large/zoomed-out canvases (e.g.
   * Windows).
   */
  private static final int MAX_GRID_POINTS = 50_000;

  protected void drawGrid() {
    if (area == null || area.x <= 0 || area.y <= 0) {
      return;
    }
    // Grid is drawn in the same coordinate system as drawOriginBoundary: the origin (0,0) of the
    // pipeline is at (offset.x, offset.y) here. The hatched "outside workable" area is x < offset.x
    // or y < offset.y. So we only draw grid where x >= offset.x and y >= offset.y.
    //
    float mag = Math.max(0.1f, magnification);
    int originX = (int) Math.round(offset.x);
    int originY = (int) Math.round(offset.y);
    int workableMinX = Math.max(0, originX);
    int workableMinY = Math.max(0, originY);
    // Visible extent in this coordinate system is (0,0) to (area.x/mag, area.y/mag); workable part
    // is from (originX, originY) to that right/bottom edge.
    int workableMaxX = (int) Math.ceil(area.x / mag);
    int workableMaxY = (int) Math.ceil(area.y / mag);
    if (workableMaxX <= workableMinX || workableMaxY <= workableMinY) {
      return;
    }

    int baseStep = (mag < 1.0f) ? Math.max(gridSize, (int) (gridSize / mag)) : gridSize;
    int rangeX = workableMaxX - workableMinX;
    int rangeY = workableMaxY - workableMinY;
    long totalPoints = (long) (rangeX / baseStep + 1) * (rangeY / baseStep + 1);
    int step = baseStep;
    if (totalPoints > MAX_GRID_POINTS) {
      int minStep =
          Math.max(
              gridSize, (int) Math.ceil(Math.sqrt((double) rangeX * rangeY / MAX_GRID_POINTS)));
      step = Math.max(baseStep, minStep);
    }
    int offsetX = (int) (offset.x % step);
    int offsetY = (int) (offset.y % step);
    if (offsetX < 0) {
      offsetX += step;
    }
    if (offsetY < 0) {
      offsetY += step;
    }
    // First grid position at or after workable visible origin (never in hatched area)
    int startX =
        Math.max(
            workableMinX,
            offsetX + step * (int) Math.ceil((double) (workableMinX - offsetX) / step));
    int startY =
        Math.max(
            workableMinY,
            offsetY + step * (int) Math.ceil((double) (workableMinY - offsetY) / step));
    for (int x = startX; x < workableMaxX; x += step) {
      for (int y = startY; y < workableMaxY; y += step) {
        if (x >= 0 && y >= 0) {
          gc.drawPoint(x, y);
        }
      }
    }
  }

  /**
   * Base spacing between hatching lines at 100% zoom; scaled by zoom so we draw fewer when zoomed
   * out.
   */
  private static final int HATCH_STEP_BASE = 24;

  /** Fixed width of the navigation viewport (minimap) in pixels. */
  private static final int VIEWPORT_WIDTH = 200;

  /** Maximum height of the minimap; height follows content aspect ratio up to this cap. */
  private static final int VIEWPORT_HEIGHT_MAX = 200;

  /**
   * Draw a small rectangle at the bottom right of the screen which depicts the viewport as a part
   * of the total size of the metadata graph. Width is fixed; height follows content aspect ratio
   * (capped) so the minimap represents the graph without spurious empty space. Content is top-left
   * aligned so "at the top" of the canvas matches the top of the minimap.
   */
  protected void drawNavigationView() {
    if (!showingNavigationView || maximum == null) {
      return;
    }
    double contentMaxX = Math.max(1, maximum.x);
    double contentMaxY = Math.max(1, maximum.y);

    // 1) Visible rectangle in graph coordinates (unclamped so we can show panned-outside view).
    //
    double mag = Math.max(0.01, magnification);
    double visibleLeft = -offset.x;
    double visibleTop = -offset.y;
    double visibleWidthGraph = area.x / mag;
    double visibleHeightGraph = area.y / mag;
    double visibleRightGraph = visibleLeft + visibleWidthGraph;
    double visibleBottomGraph = visibleTop + visibleHeightGraph;

    // 2) Minimap bounds = union of content and visible view so the overlay always fits inside.
    //
    double minGraphX = Math.min(0, visibleLeft);
    double minGraphY = Math.min(0, visibleTop);
    double maxGraphX = Math.max(contentMaxX, visibleRightGraph);
    double maxGraphY = Math.max(contentMaxY, visibleBottomGraph);
    double graphRangeX = Math.max(1, maxGraphX - minGraphX);
    double graphRangeY = Math.max(1, maxGraphY - minGraphY);

    // 3) Fixed width; height follows content aspect ratio (capped) so the minimap fits the graph.
    //
    int viewportHeight =
        Math.clamp(
            (int) Math.round(VIEWPORT_WIDTH * graphRangeY / graphRangeX), 20, VIEWPORT_HEIGHT_MAX);
    double scale = Math.min(VIEWPORT_WIDTH / graphRangeX, viewportHeight / graphRangeY);
    double contentWidth = graphRangeX * scale;
    double contentHeight = graphRangeY * scale;
    double contentLeft = area.x - VIEWPORT_WIDTH - 10.0;
    double contentTop = area.y - viewportHeight - 10.0;
    // Top-left align so "at the top" of the canvas matches the top of the minimap

    int alpha = gc.getAlpha();
    gc.setAlpha(75);

    gc.setForeground(EColor.DARKGRAY);
    gc.setBackground(EColor.LIGHTBLUE);
    gc.drawRectangle((int) contentLeft, (int) contentTop, VIEWPORT_WIDTH, viewportHeight);
    gc.fillRectangle((int) contentLeft, (int) contentTop, VIEWPORT_WIDTH, viewportHeight);

    // 3) Origin for graph (0,0) in minimap pixels; content and overlay use same scale.
    //
    double graphOriginX = contentLeft - minGraphX * scale;
    double graphOriginY = contentTop - minGraphY * scale;
    drawNavigationViewContent(graphOriginX, graphOriginY, scale, scale);

    this.navigationScale = scale;
    this.navigationGraphOriginX = graphOriginX;
    this.navigationGraphOriginY = graphOriginY;

    double viewX = graphOriginX + visibleLeft * scale;
    double viewY = graphOriginY + visibleTop * scale;
    double viewWidth = visibleWidthGraph * scale;
    double viewHeight = visibleHeightGraph * scale;

    // Clamp overlay to the drawn content area (avoid drawing outside the light blue)
    viewX = Math.clamp(viewX, contentLeft, contentLeft + contentWidth - 1);
    viewY = Math.clamp(viewY, contentTop, contentTop + contentHeight - 1);
    viewWidth = Math.min(viewWidth, contentLeft + contentWidth - viewX);
    viewHeight = Math.min(viewHeight, contentTop + contentHeight - viewY);
    viewWidth = Math.max(0, viewWidth);
    viewHeight = Math.max(0, viewHeight);

    gc.setForeground(EColor.BLACK);
    gc.setBackground(EColor.BLUE);
    gc.drawRectangle((int) viewX, (int) viewY, (int) viewWidth, (int) viewHeight);
    gc.fillRectangle((int) viewX, (int) viewY, (int) viewWidth, (int) viewHeight);

    graphPort = new Rectangle((int) contentLeft, (int) contentTop, VIEWPORT_WIDTH, viewportHeight);
    viewPort = new Rectangle((int) viewX, (int) viewY, (int) viewWidth, (int) viewHeight);

    gc.setAlpha(alpha);
  }

  /**
   * Override to draw rectangles and lines inside the navigation viewport representing the graph
   * elements (e.g. transforms/actions and hops). Coordinates are in graph space; convert to
   * viewport pixels with: screenX = graphX + graphCoordX * scaleX, screenY = graphY + graphCoordY *
   * scaleY.
   *
   * @param graphX left of the viewport rectangle in screen pixels
   * @param graphY top of the viewport rectangle in screen pixels
   * @param scaleX scale from graph X to viewport width
   * @param scaleY scale from graph Y to viewport height
   */
  protected void drawNavigationViewContent(
      double graphX, double graphY, double scaleX, double scaleY) {
    // Default: nothing. PipelinePainter and WorkflowPainter draw transforms/actions and hops.
  }

  /** Draw notes behind tables (like pipeline/workflow graphs). */
  protected void drawNotes(List<DvNote> notes) {
    if (!drawNotes || notes == null || notes.isEmpty()) {
      return;
    }
    syncNotePadDarkMode();
    for (DvNote note : notes) {
      drawNote(note);
    }
  }

  private void syncNotePadDarkMode() {
    try {
      NotePadStyle.setDarkMode(PropsUi.getInstance().isDarkMode());
    } catch (Throwable ignored) {
      // Headless SVG / unit tests without SWT PropsUi.
    }
  }

  /**
   * Base font size for Markdown notes: graph font height so body text matches table labels under
   * the same canvas magnification (mirrors Hop BasePainter.markdownBaseFontSize).
   */
  private int markdownBaseFontSize() {
    gc.setFont(EFont.GRAPH);
    int graphHeight = gc.getFontHeight();
    if (graphHeight > 0) {
      return graphHeight;
    }
    return 10;
  }

  protected void drawNote(DvNote note) {
    if (note == null || note.getLocation() == null) {
      return;
    }

    NotePadMeta noteMeta = DvNotePadSupport.forCanvas(note);
    NotePadType type =
        noteMeta.getNoteType() != null ? noteMeta.getNoteType() : NotePadType.GENERAL;
    NotePadStyle.RgbColor bg = NotePadStyle.backgroundColor(type);
    NotePadStyle.RgbColor border = NotePadStyle.borderColor(type);

    Point minimumSize = calculateNoteMinimumSize(note);
    note.setMinimumWidth(minimumSize.x);
    note.setMinimumHeight(minimumSize.y);
    noteMeta.setMinimumWidth(minimumSize.x);
    noteMeta.setMinimumHeight(minimumSize.y);

    Point notePos = noteDrawLocation(note.getLocation().x, note.getLocation().y);
    int width = note.getWidth();
    int height = note.getHeight();
    if (width < minimumSize.x) {
      width = minimumSize.x;
    }
    if (height < minimumSize.y) {
      height = minimumSize.y;
    }

    Rectangle noteShape = new Rectangle(notePos.x, notePos.y, width, height);
    int radius = CORNER_RADIUS_5;

    gc.setBackground(bg.red(), bg.green(), bg.blue());
    gc.setForeground(border.red(), border.green(), border.blue());
    gc.setLineWidth(NotePadStyle.borderWidth(type, note.isSelected()));
    gc.fillRoundRectangle(
        noteShape.x, noteShape.y, noteShape.width, noteShape.height, radius, radius);
    gc.drawRoundRectangle(
        noteShape.x, noteShape.y, noteShape.width, noteShape.height, radius, radius);
    gc.setLineWidth(1);

    if (areaOwners != null) {
      areaOwners.add(
          new AreaOwner(
              AreaType.NOTE,
              noteShape.x,
              noteShape.y,
              noteShape.width,
              noteShape.height,
              offset,
              subject,
              note));
    }

    int contentX = noteShape.x + Const.NOTE_MARGIN;
    int contentY = noteShape.y + Const.NOTE_MARGIN;
    int textX = contentX;
    int textY = contentY;

    EImage icon = NotePadStyle.icon(type);
    if (icon != null) {
      try {
        gc.drawImage(icon, contentX, contentY, 1.0f);
      } catch (Exception ignored) {
        // optional decoration
      }
      textX = contentX + NOTE_ICON_SIZE + NOTE_ICON_TEXT_GAP;
    }

    if (Utils.isEmpty(noteMeta.getNote())) {
      return;
    }

    int contentWidth = Math.max(20, noteShape.width - Const.NOTE_MARGIN - (textX - noteShape.x));
    List<PositionedLink> positionedLinks = new ArrayList<>();
    MarkdownNoteRenderer.paintWithLinkBounds(
        gc,
        noteMeta,
        "Sans",
        markdownBaseFontSize(),
        textX,
        textY,
        contentWidth,
        mouseOverNoteLink,
        positionedLinks,
        noteImageBaseFilename,
        variables);

    if (areaOwners != null) {
      for (PositionedLink pl : positionedLinks) {
        int pad = 1;
        areaOwners.add(
            new AreaOwner(
                AreaType.NOTE_LINK,
                pl.x() - pad,
                pl.y() - pad,
                pl.width() + 2 * pad,
                pl.height() + 2 * pad,
                offset,
                note,
                pl.hit()));
      }
    }
  }

  /**
   * Measure note content in model/logical units. Uses Hop Markdown layout so resize reflows text
   * instead of locking to unwrapped line widths.
   */
  protected Point calculateNoteMinimumSize(DvNote note) {
    int margin = Const.NOTE_MARGIN;
    NotePadMeta noteMeta = DvNotePadSupport.forCanvas(note);
    NotePadType type =
        noteMeta.getNoteType() != null ? noteMeta.getNoteType() : NotePadType.GENERAL;
    int textIndent = NotePadStyle.icon(type) != null ? NOTE_ICON_SIZE + NOTE_ICON_TEXT_GAP : 0;

    // Allow narrowing well below default note width; content reflows (wraps) instead.
    final int minContentWidth = 40;
    int minWidth = 2 * margin + textIndent + minContentWidth;

    float savedMag = magnification;
    gc.setTransform(0.0f, 0.0f, 1.0f);
    try {
      if (Utils.isEmpty(noteMeta.getNote())) {
        return new Point(minWidth, 2 * margin + Math.max(NOTE_ICON_SIZE, 16));
      }
      int availableContent =
          note.getWidth() > 0
              ? Math.max(minContentWidth, note.getWidth() - 2 * margin - textIndent)
              : minContentWidth;
      MarkdownNoteRenderer.LayoutResult layout =
          MarkdownNoteRenderer.measure(
              gc,
              noteMeta,
              "Sans",
              markdownBaseFontSize(),
              availableContent,
              noteImageBaseFilename,
              variables);
      int contentH =
          Math.max(NotePadStyle.icon(type) != null ? NOTE_ICON_SIZE : 0, layout.height());
      return new Point(minWidth, 2 * margin + contentH);
    } finally {
      gc.setTransform((float) offset.x, (float) offset.y, savedMag);
    }
  }
}
