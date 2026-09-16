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
package org.hopper.edw.datavault.busmatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.hop.core.Const;
import org.hopper.edw.datavault.documentation.model.SvgDocument;
import org.hopper.edw.datavault.documentation.model.SvgHit;
import org.hopper.edw.datavault.documentation.render.HtmlEscaper;

/** Headless SVG painter for a {@link BusMatrix}. */
public final class BusMatrixSvgPainter {

  private BusMatrixSvgPainter() {}

  public static String paint(BusMatrix matrix, boolean dark) {
    SvgDocument document = paintDocument(matrix, dark, null);
    return dark ? document.getDarkSvg() : document.getLightSvg();
  }

  /** Geometry used by {@link #paintDocument}; shared with the viewer host. */
  public static BusMatrixLayout layoutOf(BusMatrix matrix) {
    BusMatrix safe = matrix != null ? matrix : new BusMatrix("", null, null, null);
    return BusMatrixLayout.measure(safe, s -> Math.max(1, Const.NVL(s, "").length()) * 7, 12);
  }

  public static SvgDocument paintDocument(
      BusMatrix matrix, boolean dark, Function<String, String> tableHref) {
    BusMatrix safe = matrix != null ? matrix : new BusMatrix("", null, null, null);
    BusMatrixLayout layout = layoutOf(safe);
    int width = Math.max(320, layout.width(safe));
    int height = Math.max(240, layout.height(safe));
    Palette palette = dark ? Palette.DARK : Palette.LIGHT;
    StringBuilder svg = new StringBuilder();
    svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
        .append(width)
        .append("\" height=\"")
        .append(height)
        .append("\" viewBox=\"0 0 ")
        .append(width)
        .append(' ')
        .append(height)
        .append("\" preserveAspectRatio=\"xMinYMin meet\">\n");
    // RAP dark-mode.css uses `* { color; background-color }` which bleeds into inline SVG.
    svg.append("<style type=\"text/css\"><![CDATA[");
    svg.append(
        "svg,svg *{background:none!important;background-color:transparent!important;color:unset!important;}");
    svg.append("]]></style>\n");
    rect(svg, 0, 0, width, height, palette.background, null);
    List<SvgHit> hits = new ArrayList<>();

    for (int i = 0; i < BusMatrixLayout.FROZEN_HEADERS.length; i++) {
      int x = layout.labelX(i);
      int w = layout.labelWidth(i);
      rect(svg, x, 0, w, layout.headerHeight(), palette.header, palette.border);
      text(
          svg,
          x + 6,
          layout.headerHeight() - BusMatrixLayout.FROZEN_HEADER_LIFT - 8,
          BusMatrixLayout.FROZEN_HEADERS[i],
          palette.headerFg,
          11,
          false,
          w - 10);
    }
    for (int c = 0; c < safe.getColumns().size(); c++) {
      BusMatrixColumn column = safe.getColumns().get(c);
      int x = layout.cellX(c);
      trapezium(svg, layout.headerTrapezium(c, 0, 0), palette.header, palette.border);
      rotatedHeader(
          svg, x, 0, layout.cellWidth(), layout.headerHeight(), column.label(), palette.headerFg);
      String href = tableHref != null ? tableHref.apply(column.dimensionName()) : null;
      hits.add(
          new SvgHit(
              "dimension",
              column.dimensionName(),
              x,
              0,
              layout.cellWidth() + layout.headerShear(),
              layout.headerHeight(),
              Const.NVL(href, "#")));
    }

    for (int r = 0; r < safe.getRows().size(); r++) {
      BusMatrixRow row = safe.getRows().get(r);
      int y = layout.cellY(r);
      String rowBg = r % 2 == 0 ? palette.rowEven : palette.rowOdd;
      boolean groupStart =
          BusMatrixLayout.startsGroup(r == 0 ? null : safe.getRows().get(r - 1), row);
      for (int i = 0; i < BusMatrixLayout.FROZEN_COLUMNS; i++) {
        int x = layout.labelX(i);
        int w = layout.labelWidth(i);
        rect(svg, x, y, w, layout.rowHeight(), rowBg, palette.border);
        text(
            svg,
            x + 6,
            y + layout.rowHeight() - 8,
            BusMatrixLayout.frozenValue(row, i),
            palette.fg,
            11,
            false,
            w - BusMatrixLayout.FROZEN_COLUMN_MARGIN);
      }
      String rowHref = tableHref != null ? tableHref.apply(row.factName()) : null;
      hits.add(
          new SvgHit(
              "fact",
              row.factName(),
              0,
              y,
              layout.frozenWidth(),
              layout.rowHeight(),
              Const.NVL(rowHref, "#")));
      for (int c = 0; c < safe.getColumns().size(); c++) {
        BusMatrixColumn column = safe.getColumns().get(c);
        BusMatrixCell cell = row.cell(column.key());
        int x = layout.cellX(c);
        String fill = cell.used() ? palette.mark : rowBg;
        rect(svg, x, y, layout.cellWidth(), layout.rowHeight(), fill, palette.border);
        if (cell.used()) {
          text(
              svg,
              x + layout.cellWidth() / 2,
              y + layout.rowHeight() - 8,
              cell.mark(),
              palette.markFg,
              12,
              true,
              layout.cellWidth() - 4);
        }
      }
      if (groupStart) {
        svg.append("<line x1=\"0\" y1=\"")
            .append(y)
            .append("\" x2=\"")
            .append(width)
            .append("\" y2=\"")
            .append(y)
            .append("\" stroke=\"")
            .append(palette.border)
            .append("\" stroke-width=\"2\"/>\n");
      }
    }
    svg.append("</svg>\n");

    SvgDocument document = new SvgDocument();
    if (dark) {
      document.setDarkSvg(svg.toString());
    } else {
      document.setLightSvg(svg.toString());
    }
    document.getHits().addAll(hits);
    return document;
  }

  public static SvgDocument paintBoth(BusMatrix matrix, Function<String, String> tableHref) {
    SvgDocument light = paintDocument(matrix, false, tableHref);
    SvgDocument dark = paintDocument(matrix, true, tableHref);
    light.setDarkSvg(dark.getDarkSvg());
    return light;
  }

  private static void trapezium(StringBuilder svg, int[] pts, String fill, String stroke) {
    svg.append("<polygon points=\"");
    for (int i = 0; i < pts.length; i += 2) {
      if (i > 0) {
        svg.append(' ');
      }
      svg.append(pts[i]).append(',').append(pts[i + 1]);
    }
    svg.append("\" fill=\"").append(fill).append('"');
    if (stroke != null) {
      svg.append(" stroke=\"").append(stroke).append("\" stroke-width=\"1\"");
    }
    svg.append("/>\n");
  }

  private static void rect(
      StringBuilder svg, int x, int y, int w, int h, String fill, String stroke) {
    svg.append("<rect x=\"")
        .append(x)
        .append("\" y=\"")
        .append(y)
        .append("\" width=\"")
        .append(w)
        .append("\" height=\"")
        .append(h)
        .append("\" fill=\"")
        .append(fill)
        .append('"');
    if (stroke != null) {
      svg.append(" stroke=\"").append(stroke).append("\" stroke-width=\"1\"");
    }
    svg.append("/>\n");
  }

  private static void text(
      StringBuilder svg,
      int x,
      int y,
      String value,
      String fill,
      int size,
      boolean center,
      int maxWidth) {
    if (value == null || value.isEmpty()) {
      return;
    }
    String shown = BusMatrixLayout.ellipsize(value, maxWidth, s -> s.length() * 7);
    svg.append("<text x=\"")
        .append(x)
        .append("\" y=\"")
        .append(y)
        .append("\" fill=\"")
        .append(fill)
        .append("\" style=\"fill:")
        .append(fill)
        .append("\" font-size=\"")
        .append(size)
        .append("\" font-family=\"Segoe UI, sans-serif\"");
    if (center) {
      svg.append(" text-anchor=\"middle\"");
    }
    svg.append('>').append(HtmlEscaper.escape(shown)).append("</text>\n");
  }

  private static void rotatedHeader(
      StringBuilder svg, int x, int y, int w, int h, String value, String fill) {
    int maxPx = BusMatrixLayout.maxLabelPxForHeader(h, 12);
    String shown = BusMatrixLayout.ellipsize(value, maxPx, s -> s.length() * 7);
    if (shown.isEmpty()) {
      return;
    }
    int cx = Math.round(BusMatrixLayout.headerLabelCenterX(x, w, h));
    int cy = Math.round(BusMatrixLayout.headerLabelCenterY(y, h));
    svg.append("<text fill=\"")
        .append(fill)
        .append("\" style=\"fill:")
        .append(fill)
        .append("\" font-size=\"11\" font-family=\"Segoe UI, sans-serif\"")
        .append(" text-anchor=\"middle\" dominant-baseline=\"middle\"")
        .append(" transform=\"translate(")
        .append(cx)
        .append(',')
        .append(cy)
        .append(") rotate(")
        .append((int) BusMatrixLayout.HEADER_TILT_DEGREES)
        .append(")\">")
        .append(HtmlEscaper.escape(shown))
        .append("</text>\n");
  }

  private record Palette(
      String background,
      String header,
      String headerFg,
      String border,
      String fg,
      String rowEven,
      String rowOdd,
      String mark,
      String markFg) {
    static final Palette LIGHT =
        new Palette(
            "#ffffff", "#17324d", "#f4f6f8", "#d5dde5", "#1b2430", "#ffffff", "#f4f6f8", "#ecf5fa",
            "#17324d");
    static final Palette DARK =
        new Palette(
            "#12151a", "#0d1b27", "#e8edf2", "#2c3640", "#e8edf2", "#1a1f26", "#12151a", "#1b2c39",
            "#d5eaf3");
  }
}
