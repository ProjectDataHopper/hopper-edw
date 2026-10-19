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
package org.hopper.edw.datavault.hopgui.perspective.journey.poster;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.SwingUniversalImage;
import org.apache.hop.core.SwingUniversalImageSvg;
import org.apache.hop.core.svg.SvgImage;
import org.apache.hop.core.svg.SvgSupport;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.hopper.edw.datavault.hopgui.EdwDocsSupport;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneyPerspective;
import org.hopper.edw.datavault.hopgui.perspective.journey.poster.EdwJourneyPosterHotspots.Hotspot;
import org.hopper.edw.datavault.hopgui.perspective.journey.poster.EdwJourneyPosterStatus.Mark;
import org.hopper.edw.datavault.hopgui.perspective.journey.poster.EdwJourneyPosterStatus.MarkedBlock;

/** Architecture poster with status marks. Clicks report a block id. */
public final class EdwJourneyPoster extends Composite {

  private static final Class<?> PKG = EdwJourneyPerspective.class;
  private static final int BADGE = 16;

  private final Canvas canvas;
  private final Consumer<String> onSelect;
  private final IVariables variables;
  private Map<String, MarkedBlock> marks = Map.of();
  private String selected;
  private String svg;
  private Image image;
  private int imageWidth;
  private int imageHeight;

  public EdwJourneyPoster(Composite parent, IVariables variables, Consumer<String> onSelect) {
    super(parent, SWT.NONE);
    this.variables = variables;
    this.onSelect = onSelect;
    setLayout(new FormLayout());
    PropsUi.setLook(this);

    Composite acts = new Composite(this, SWT.NONE);
    acts.setLayout(new FormLayout());
    PropsUi.setLook(acts);
    FormData fdActs = new FormData();
    fdActs.left = new FormAttachment(0, 0);
    fdActs.right = new FormAttachment(100, 0);
    fdActs.bottom = new FormAttachment(100, 0);
    acts.setLayoutData(fdActs);
    Button harvest = addAct(acts, null, "EdwJourneyPoster.Act.Harvest", "harvester");
    Button gates = addAct(acts, harvest, "EdwJourneyPoster.Act.Gates", "gates");
    Button publish = addAct(acts, gates, "EdwJourneyPoster.Act.Publish", "publish");
    addAct(acts, publish, "EdwJourneyPoster.Act.Run", "hop-engine");

    canvas = new Canvas(this, SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED);
    FormData fdCanvas = new FormData();
    fdCanvas.left = new FormAttachment(0, 0);
    fdCanvas.top = new FormAttachment(0, 0);
    fdCanvas.right = new FormAttachment(100, 0);
    fdCanvas.bottom = new FormAttachment(acts, 0);
    canvas.setLayoutData(fdCanvas);
    canvas.addPaintListener(e -> paint(e.gc));
    canvas.addListener(SWT.MouseUp, e -> click(e.x, e.y));
    canvas.addListener(SWT.Resize, e -> canvas.redraw());
    canvas.addListener(
        SWT.KeyDown,
        e -> {
          if (e.keyCode == SWT.ESC && onSelect != null) {
            selected = null;
            onSelect.accept(null);
            canvas.redraw();
          }
        });
    addDisposeListener(e -> disposeImage());
  }

  public void setMarks(Map<String, MarkedBlock> marks) {
    this.marks = marks == null ? Map.of() : marks;
    if (!canvas.isDisposed()) {
      canvas.redraw();
    }
  }

  public void setSelected(String blockId) {
    selected = blockId;
    if (!canvas.isDisposed()) {
      canvas.redraw();
    }
  }

  private void click(int x, int y) {
    Frame frame = frame(canvas.getClientArea());
    if (frame == null) {
      return;
    }
    int posterX = (int) ((x - frame.offsetX) / frame.scale);
    int posterY = (int) ((y - frame.offsetY) / frame.scale);
    String id = EdwJourneyPosterHotspots.hit(posterX, posterY);
    if (id == null || onSelect == null) {
      return;
    }
    selected = id;
    canvas.redraw();
    onSelect.accept(id);
  }

  private void paint(GC gc) {
    Rectangle area = canvas.getClientArea();
    gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
    gc.fillRectangle(area);
    Frame frame = frame(area);
    if (frame == null) {
      gc.drawText("Architecture poster", 8, 8, true);
      return;
    }
    ensureImage(frame.drawWidth, frame.drawHeight);
    if (image != null && !image.isDisposed()) {
      gc.drawImage(image, frame.offsetX, frame.offsetY);
    }
    for (String id : EdwJourneyPosterHotspots.ids()) {
      if (id.equals(selected)) {
        drawSelection(gc, frame, id);
      }
      MarkedBlock mark = marks.get(id);
      if (mark != null && mark.mark() != Mark.NONE) {
        drawBadge(gc, frame, id, mark.mark());
      }
    }
  }

  private void drawSelection(GC gc, Frame frame, String id) {
    Color color = new Color(gc.getDevice(), 14, 116, 144);
    gc.setForeground(color);
    gc.setLineWidth(3);
    for (Hotspot hotspot : EdwJourneyPosterHotspots.forId(id)) {
      gc.drawRoundRectangle(
          frame.offsetX + scale(hotspot.x(), frame.scale),
          frame.offsetY + scale(hotspot.y(), frame.scale),
          Math.max(4, scale(hotspot.width(), frame.scale)),
          Math.max(4, scale(hotspot.height(), frame.scale)),
          8,
          8);
    }
    color.dispose();
    gc.setLineWidth(1);
  }

  private void drawBadge(GC gc, Frame frame, String id, Mark mark) {
    int[] anchor = EdwJourneyPosterHotspots.badgeAnchor(id);
    if (anchor == null) {
      return;
    }
    int x = frame.offsetX + scale(anchor[0], frame.scale) - BADGE;
    int y = frame.offsetY + scale(anchor[1], frame.scale);
    Color color =
        switch (mark) {
          case ERROR -> new Color(gc.getDevice(), 198, 40, 40);
          case WARNING -> new Color(gc.getDevice(), 249, 168, 37);
          case OK -> new Color(gc.getDevice(), 46, 125, 50);
          case NONE -> null;
        };
    if (color == null) {
      return;
    }
    gc.setBackground(color);
    gc.fillOval(x, y, BADGE, BADGE);
    gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
    String glyph = mark == Mark.OK ? "+" : mark == Mark.ERROR ? "x" : "!";
    gc.drawText(glyph, x + 4, y + 1, true);
    color.dispose();
  }

  private void ensureImage(int width, int height) {
    if (width < 2 || height < 2) {
      return;
    }
    if (image != null && !image.isDisposed() && width == imageWidth && height == imageHeight) {
      return;
    }
    disposeImage();
    String xml = svg();
    if (xml == null) {
      return;
    }
    try {
      SvgImage svgImage =
          SvgSupport.loadSvgImage(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      SwingUniversalImageSvg universal = new SwingUniversalImageSvg(svgImage);
      BufferedImage bitmap = SwingUniversalImage.createBitmap(width, height);
      Graphics2D graphics = SwingUniversalImage.createGraphics(bitmap);
      try {
        universal.drawToGraphics(graphics, width / 2, height / 2, width, height, 0);
      } finally {
        graphics.dispose();
      }
      image = new Image(getDisplay(), toImageData(bitmap));
      imageWidth = width;
      imageHeight = height;
    } catch (Exception e) {
      image = null;
    }
  }

  private String svg() {
    if (svg != null) {
      return svg;
    }
    for (Path candidate : candidates()) {
      if (candidate == null) {
        continue;
      }
      try {
        FileObject file = HopVfs.getFileObject(candidate.toAbsolutePath().toString());
        if (file == null || !file.exists() || !file.isFile()) {
          continue;
        }
        try (InputStream in = HopVfs.getInputStream(file)) {
          svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
          return svg;
        }
      } catch (Exception ignored) {
        // Try the next location.
      }
    }
    return null;
  }

  private java.util.List<Path> candidates() {
    java.util.List<Path> paths = new java.util.ArrayList<>();
    Path index = EdwDocsSupport.findIndexHtml();
    if (index != null && index.getParent() != null) {
      paths.add(index.getParent().resolve("images/diagrams/edw-overview.svg"));
    }
    if (variables != null) {
      String home = variables.getVariable("PROJECT_HOME");
      if (home != null && !home.isBlank()) {
        paths.add(Path.of(home, "docs", "images", "diagrams", "edw-overview.svg"));
      }
    }
    paths.add(Path.of("docs", "images", "diagrams", "edw-overview.svg"));
    return paths;
  }

  private Frame frame(Rectangle area) {
    if (area.width < 8 || area.height < 8) {
      return null;
    }
    double scale =
        Math.min(
            area.width / (double) EdwJourneyPosterHotspots.POSTER_WIDTH,
            area.height / (double) EdwJourneyPosterHotspots.POSTER_HEIGHT);
    int drawWidth = Math.max(1, (int) (EdwJourneyPosterHotspots.POSTER_WIDTH * scale));
    int drawHeight = Math.max(1, (int) (EdwJourneyPosterHotspots.POSTER_HEIGHT * scale));
    int offsetX = (area.width - drawWidth) / 2;
    int offsetY = (area.height - drawHeight) / 2;
    return new Frame(scale, offsetX, offsetY, drawWidth, drawHeight);
  }

  private static int scale(int value, double factor) {
    return (int) Math.round(value * factor);
  }

  private void disposeImage() {
    if (image != null && !image.isDisposed()) {
      image.dispose();
    }
    image = null;
  }

  private Button addAct(Composite parent, Button previous, String key, String blockId) {
    Button button = new Button(parent, SWT.PUSH);
    button.setText(BaseMessages.getString(PKG, key));
    place(button, previous);
    button.addListener(
        SWT.Selection,
        e -> {
          selected = blockId;
          canvas.redraw();
          if (onSelect != null) {
            onSelect.accept(blockId);
          }
        });
    return button;
  }

  private static void place(Button button, Button previous) {
    FormData data = new FormData();
    data.top = new FormAttachment(0, 4);
    data.bottom = new FormAttachment(100, -4);
    if (previous == null) {
      data.left = new FormAttachment(0, 4);
    } else {
      data.left = new FormAttachment(previous, 4);
    }
    button.setLayoutData(data);
  }

  private static ImageData toImageData(BufferedImage bitmap) {
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    ImageData data = new ImageData(width, height, 32, new PaletteData(0xFF0000, 0xFF00, 0xFF));
    byte[] alpha = new byte[width * height];
    data.alphaData = alpha;
    int[] pixels = new int[width];
    byte[] rowAlpha = new byte[width];
    for (int y = 0; y < height; y++) {
      bitmap.getRGB(0, y, width, 1, pixels, 0, width);
      for (int x = 0; x < width; x++) {
        int rgba = pixels[x];
        rowAlpha[x] = (byte) ((rgba >>> 24) & 0xFF);
        pixels[x] = rgba & 0x00FFFFFF;
      }
      data.setPixels(0, y, width, pixels, 0);
      System.arraycopy(rowAlpha, 0, alpha, y * width, width);
    }
    return data;
  }

  private record Frame(double scale, int offsetX, int offsetY, int drawWidth, int drawHeight) {}
}
