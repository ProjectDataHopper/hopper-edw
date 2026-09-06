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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.CanvasSvgRenderResult;
import org.apache.hop.core.gui.DPoint;
import org.apache.hop.core.gui.NotePadStyle;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.SvgGc;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.PipelinePainter;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.WorkflowPainter;
import org.hopper.edw.datavault.command.svg.ModelBoundsSupport;
import org.hopper.edw.datavault.command.svg.SvgRenderOptions;
import org.hopper.edw.datavault.documentation.model.SvgDocument;
import org.hopper.edw.datavault.executionmap.ExecutionMapFocusContext;
import org.hopper.edw.datavault.hopgui.file.businessvault.BusinessVaultModelPainter;
import org.hopper.edw.datavault.hopgui.file.dimensional.DimensionalModelPainter;
import org.hopper.edw.datavault.hopgui.file.executionmap.ExecutionMapPainter;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.SourceModelPainter;
import org.hopper.edw.datavault.hopgui.file.vault.DataVaultModelPainter;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;

/** Headless SVG + hit-map export for documentation pages. */
public final class DocumentSvgExporter {

  private static final int ICON_SIZE = 32;
  private static final int EXTRA = 80;

  private DocumentSvgExporter() {}

  public static SvgDocument pipeline(PipelineMeta meta, IVariables variables, boolean darkSvg)
      throws HopException {
    CanvasSvgRenderResult light = renderPipeline(meta, variables, false);
    SvgDocument document = from(light, null);
    if (darkSvg) {
      document.setDarkSvg(renderPipeline(meta, variables, true).getSvg());
    }
    return document;
  }

  public static SvgDocument workflow(WorkflowMeta meta, IVariables variables, boolean darkSvg)
      throws HopException {
    CanvasSvgRenderResult light = renderWorkflow(meta, variables, false);
    SvgDocument document = from(light, null);
    if (darkSvg) {
      document.setDarkSvg(renderWorkflow(meta, variables, true).getSvg());
    }
    return document;
  }

  public static SvgDocument sourceModel(
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean includeNotes,
      boolean darkSvg,
      Function<String, String> tableHref)
      throws HopException {
    PaintResult light = paintSource(model, variables, metadataProvider, includeNotes, false);
    SvgDocument document = from(light.svg, light.hits, tableHref);
    if (darkSvg) {
      document.setDarkSvg(paintSource(model, variables, metadataProvider, includeNotes, true).svg);
    }
    return document;
  }

  public static SvgDocument dataVault(
      DataVaultModel model,
      IVariables variables,
      boolean includeNotes,
      boolean darkSvg,
      Function<String, String> tableHref)
      throws HopException {
    PaintResult light = paintDv(model, variables, includeNotes, false);
    SvgDocument document = from(light.svg, light.hits, tableHref);
    if (darkSvg) {
      document.setDarkSvg(paintDv(model, variables, includeNotes, true).svg);
    }
    return document;
  }

  public static SvgDocument businessVault(
      BusinessVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean includeNotes,
      boolean darkSvg,
      Function<String, String> tableHref)
      throws HopException {
    PaintResult light = paintBv(model, variables, metadataProvider, includeNotes, false);
    SvgDocument document = from(light.svg, light.hits, tableHref);
    if (darkSvg) {
      document.setDarkSvg(paintBv(model, variables, metadataProvider, includeNotes, true).svg);
    }
    return document;
  }

  public static SvgDocument dimensional(
      DimensionalModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean includeNotes,
      boolean darkSvg,
      Function<String, String> tableHref)
      throws HopException {
    PaintResult light = paintDm(model, variables, metadataProvider, includeNotes, false);
    SvgDocument document = from(light.svg, light.hits, tableHref);
    if (darkSvg) {
      document.setDarkSvg(paintDm(model, variables, metadataProvider, includeNotes, true).svg);
    }
    return document;
  }

  public static SvgDocument executionMap(
      ExecutionMapDocument document,
      IVariables variables,
      boolean darkSvg,
      Function<String, String> tableHref)
      throws HopException {
    PaintResult light = paintHem(document, variables, false);
    SvgDocument svg = from(light.svg, light.hits, tableHref);
    if (darkSvg) {
      svg.setDarkSvg(paintHem(document, variables, true).svg);
    }
    return svg;
  }

  private static CanvasSvgRenderResult renderPipeline(
      PipelineMeta meta, IVariables variables, boolean dark) throws HopException {
    Point maximum = meta.getMaximum();
    Point canvas = new Point(Math.max(200, maximum.x + EXTRA), Math.max(200, maximum.y + EXTRA));
    try {
      List<AreaOwner> areaOwners = new ArrayList<>();
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      SvgGc gc = svgGc(graphics2D, canvas, dark);
      PipelinePainter painter =
          new PipelinePainter(
              gc,
              variables,
              meta,
              canvas,
              new DPoint(0, 0),
              null,
              null,
              areaOwners,
              ICON_SIZE,
              1,
              0,
              "Arial",
              10,
              1.0d,
              false,
              null,
              new HashMap<>());
      painter.setMagnification(1.0f);
      painter.setMaximum(maximum);
      painter.drawPipelineImage();
      return new CanvasSvgRenderResult(graphics2D.toXml(), areaOwners);
    } catch (Exception e) {
      throw new HopException("Unable to generate SVG for pipeline " + meta.getName(), e);
    }
  }

  private static CanvasSvgRenderResult renderWorkflow(
      WorkflowMeta meta, IVariables variables, boolean dark) throws HopException {
    Point maximum = meta.getMaximum();
    Point canvas = new Point(Math.max(200, maximum.x + EXTRA), Math.max(200, maximum.y + EXTRA));
    try {
      List<AreaOwner> areaOwners = new ArrayList<>();
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      SvgGc gc = svgGc(graphics2D, canvas, dark);
      WorkflowPainter painter =
          new WorkflowPainter(
              gc,
              variables,
              meta,
              canvas,
              new DPoint(0, 0),
              null,
              null,
              areaOwners,
              ICON_SIZE,
              1,
              0,
              "Arial",
              10,
              1.0d,
              false,
              null);
      painter.setMagnification(1.0f);
      painter.setMaximum(maximum);
      painter.drawWorkflow();
      return new CanvasSvgRenderResult(graphics2D.toXml(), areaOwners);
    } catch (Exception e) {
      throw new HopException("Unable to generate SVG for workflow " + meta.getName(), e);
    }
  }

  private static PaintResult paintSource(
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean includeNotes,
      boolean dark)
      throws HopException {
    Point maximum = model.getMaximum();
    Point svgSize = new Point(Math.max(200, maximum.x + EXTRA), Math.max(200, maximum.y + EXTRA));
    try {
      List<AreaOwner> areaOwners = new ArrayList<>();
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      SvgGc gc = svgGc(graphics2D, svgSize, dark);
      SourceModelPainter painter =
          new SourceModelPainter(model, gc, variables, svgSize.x, svgSize.y);
      painter.setMagnification(1.0f);
      painter.setAreaOwners(areaOwners);
      painter.setZoomFactor(1.0f);
      painter.setOffset(new DPoint(0, 0));
      painter.setIconSize(ICON_SIZE);
      painter.setGridSize(1);
      painter.setShowingNavigationView(false);
      painter.setShowEmptyModelHint(false);
      painter.setDrawNotes(includeNotes);
      painter.setMetadataProvider(metadataProvider);
      painter.setMaximum(maximum);
      painter.drawSourceModel(metadataProvider);
      return new PaintResult(graphics2D.toXml(), areaOwners);
    } catch (Exception e) {
      throw new HopException("Unable to generate source model SVG", e);
    }
  }

  private static PaintResult paintDv(
      DataVaultModel model, IVariables variables, boolean includeNotes, boolean dark)
      throws HopException {
    Point maximum = ModelBoundsSupport.getMaximum(model, includeNotes);
    Point svgSize = new Point(Math.max(200, maximum.x + EXTRA), Math.max(200, maximum.y + EXTRA));
    try {
      List<AreaOwner> areaOwners = new ArrayList<>();
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      SvgGc gc = svgGc(graphics2D, svgSize, dark);
      DataVaultModelPainter painter =
          new DataVaultModelPainter(model, gc, variables, svgSize.x, svgSize.y);
      painter.setMagnification(1.0f);
      painter.setAreaOwners(areaOwners);
      painter.setZoomFactor(1.0f);
      painter.setOffset(new DPoint(0, 0));
      painter.setIconSize(ICON_SIZE);
      painter.setGridSize(1);
      painter.setShowingNavigationView(false);
      painter.setDrawNotes(includeNotes);
      painter.setShowEmptyModelHint(false);
      painter.setMaximum(maximum);
      painter.drawDataVaultModel();
      return new PaintResult(graphics2D.toXml(), areaOwners);
    } catch (Exception e) {
      throw new HopException("Unable to generate Data Vault SVG", e);
    }
  }

  private static PaintResult paintBv(
      BusinessVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean includeNotes,
      boolean dark)
      throws HopException {
    Point maximum = ModelBoundsSupport.getMaximum(model, includeNotes);
    Point svgSize = new Point(Math.max(200, maximum.x + EXTRA), Math.max(200, maximum.y + EXTRA));
    try {
      List<AreaOwner> areaOwners = new ArrayList<>();
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      SvgGc gc = svgGc(graphics2D, svgSize, dark);
      BusinessVaultModelPainter painter =
          new BusinessVaultModelPainter(model, gc, variables, svgSize.x, svgSize.y);
      painter.setMagnification(1.0f);
      painter.setAreaOwners(areaOwners);
      painter.setZoomFactor(1.0f);
      painter.setOffset(new DPoint(0, 0));
      painter.setIconSize(ICON_SIZE);
      painter.setGridSize(1);
      painter.setShowingNavigationView(false);
      painter.setDrawNotes(includeNotes);
      painter.setMaximum(maximum);
      try {
        painter.setDataVaultModel(
            BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
                model, variables, metadataProvider));
      } catch (HopException ignored) {
        // Hash-key labels are optional in documentation diagrams.
      }
      painter.drawBusinessVaultModel(metadataProvider);
      return new PaintResult(graphics2D.toXml(), areaOwners);
    } catch (Exception e) {
      throw new HopException("Unable to generate Business Vault SVG", e);
    }
  }

  private static PaintResult paintDm(
      DimensionalModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean includeNotes,
      boolean dark)
      throws HopException {
    Point maximum = ModelBoundsSupport.getMaximum(model, includeNotes);
    Point svgSize = new Point(Math.max(200, maximum.x + EXTRA), Math.max(200, maximum.y + EXTRA));
    try {
      List<AreaOwner> areaOwners = new ArrayList<>();
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      SvgGc gc = svgGc(graphics2D, svgSize, dark);
      DimensionalModelPainter painter =
          new DimensionalModelPainter(model, gc, variables, svgSize.x, svgSize.y);
      painter.setMagnification(1.0f);
      painter.setAreaOwners(areaOwners);
      painter.setZoomFactor(1.0f);
      painter.setOffset(new DPoint(0, 0));
      painter.setIconSize(ICON_SIZE);
      painter.setGridSize(1);
      painter.setShowingNavigationView(false);
      painter.setShowEmptyModelHint(false);
      painter.setDrawNotes(includeNotes);
      painter.setMaximum(maximum);
      painter.drawDimensionalModel(metadataProvider);
      return new PaintResult(graphics2D.toXml(), areaOwners);
    } catch (Exception e) {
      throw new HopException("Unable to generate dimensional model SVG", e);
    }
  }

  private static PaintResult paintHem(
      ExecutionMapDocument document, IVariables variables, boolean dark) throws HopException {
    SvgRenderOptions options = SvgRenderOptions.defaults();
    Point graphMaximum = document.getMaximum();
    Point svgSize =
        new Point(
            Math.max(200, (int) (graphMaximum.x * options.getMagnification()) + EXTRA),
            Math.max(200, (int) (graphMaximum.y * options.getMagnification()) + EXTRA));
    try {
      List<AreaOwner> areaOwners = new ArrayList<>();
      HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
      SvgGc gc = svgGc(graphics2D, svgSize, dark);
      ExecutionMapPainter painter =
          new ExecutionMapPainter(
              document,
              gc,
              variables,
              svgSize.x,
              svgSize.y,
              null,
              new ExecutionMapFocusContext(),
              options.getExecutionMapExportScope());
      painter.setMagnification(options.getMagnification());
      painter.setAreaOwners(areaOwners);
      painter.setZoomFactor(1.0f);
      painter.setOffset(new DPoint(0, 0));
      painter.setIconSize(ICON_SIZE);
      painter.setGridSize(1);
      painter.setShowingNavigationView(false);
      painter.setMaximum(graphMaximum);
      painter.drawExecutionMap();
      return new PaintResult(graphics2D.toXml(), areaOwners);
    } catch (Exception e) {
      throw new HopException("Unable to generate execution map SVG", e);
    }
  }

  private static SvgGc svgGc(HopSvgGraphics2D graphics2D, Point size, boolean dark)
      throws HopException {
    NotePadStyle.setDarkMode(dark);
    return new LenientSvgGc(graphics2D, size, ICON_SIZE, 0, 0, dark);
  }

  private static SvgDocument from(
      CanvasSvgRenderResult result, Function<String, String> tableHref) {
    return from(result.getSvg(), result.getAreaOwners(), tableHref);
  }

  private static SvgDocument from(
      String svg, List<AreaOwner> areaOwners, Function<String, String> tableHref) {
    SvgDocument document = new SvgDocument();
    document.setLightSvg(svg);
    document.getHits().addAll(AreaOwnerHitMapper.toHits(areaOwners, tableHref));
    return document;
  }

  private record PaintResult(String svg, List<AreaOwner> hits) {}
}
