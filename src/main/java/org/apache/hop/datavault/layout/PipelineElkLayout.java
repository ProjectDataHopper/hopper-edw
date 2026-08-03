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
package org.apache.hop.datavault.layout;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.pipeline.HopGuiPipelineGraph;

/** GUI plugin that applies ELK layout to the active pipeline graph. */
@GuiPlugin
public class PipelineElkLayout {

  protected static final Class<?> PKG = PipelineElkLayout.class;
  private static final String KEY_PREFIX = "PipelineElkLayout";

  public static final String ID_TOOLBAR_ITEM_ELK_LAYOUT =
      "HopGuiPipelineGraph-ToolBar-10045-elk-layout";

  @GuiToolbarElement(
      root = HopGuiPipelineGraph.GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = ID_TOOLBAR_ITEM_ELK_LAYOUT,
      toolTip = "i18n::PipelineElkLayout.Toolbar.Layout.Tooltip",
      image = "elk-layout.svg")
  public void layoutActivePipeline() {
    HopGui hopGui = HopGui.getInstance();
    HopGuiPipelineGraph pipelineGraph = HopGui.getActivePipelineGraph();
    if (pipelineGraph == null) {
      return;
    }

    PipelineMeta pipelineMeta = pipelineGraph.getPipelineMeta();
    if (pipelineMeta == null) {
      return;
    }

    List<TransformMeta> transforms = pipelineMeta.getTransforms();
    if (transforms == null || transforms.isEmpty()) {
      return;
    }

    try {
      ElkLayout layout = ElkLayoutGuiSupport.promptForLayout(hopGui.getShell());
      if (layout == null) {
        return;
      }
      TransformMeta[] transformArray = transforms.toArray(new TransformMeta[0]);
      int[] indexes = pipelineMeta.getTransformIndexes(transforms);
      Point[] previousLocations = ElkLayoutGuiSupport.captureLocations(transforms);

      DvPipelineElkLayout.layout(pipelineMeta, layout);

      Point[] currentLocations = ElkLayoutGuiSupport.captureLocations(transforms);
      hopGui.undoDelegate.addUndoPosition(
          pipelineMeta, transformArray, indexes, previousLocations, currentLocations);

      pipelineGraph.setChanged();
      pipelineGraph.updateGui();
    } catch (HopException e) {
      ElkLayoutGuiSupport.showLayoutError(hopGui.getShell(), PKG, KEY_PREFIX, e);
    } catch (Exception e) {
      ElkLayoutGuiSupport.showLayoutError(hopGui.getShell(), PKG, KEY_PREFIX, e);
    }
  }
}
