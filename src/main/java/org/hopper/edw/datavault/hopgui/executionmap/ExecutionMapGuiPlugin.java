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
package org.hopper.edw.datavault.hopgui.executionmap;

import java.nio.file.Path;
import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.file.pipeline.HopGuiPipelineGraph;
import org.apache.hop.ui.hopgui.file.pipeline.context.HopGuiPipelineContext;
import org.apache.hop.ui.hopgui.file.pipeline.context.HopGuiPipelineTransformContext;
import org.apache.hop.ui.hopgui.file.workflow.HopGuiWorkflowGraph;
import org.apache.hop.ui.hopgui.file.workflow.context.HopGuiWorkflowActionContext;
import org.apache.hop.ui.hopgui.file.workflow.context.HopGuiWorkflowContext;
import org.apache.hop.workflow.WorkflowMeta;
import org.eclipse.swt.SWT;
import org.hopper.edw.datavault.command.executionmap.ExecutionMapService;
import org.hopper.edw.datavault.executionmap.ExecutionMapSubRootSupport;
import org.hopper.edw.datavault.hopgui.file.executionmap.HopExecutionMapFileType;

/** Toolbar and context-menu entry points for generating execution maps. */
@GuiPlugin(description = "i18n::ExecutionMapGuiPlugin.Description")
public class ExecutionMapGuiPlugin {

  public static final String TOOLBAR_ITEM = "HopGuiWorkflowGraph-ToolBar-10047-execution-map";

  @GuiToolbarElement(
      root = HopGuiWorkflowGraph.GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM,
      toolTip = "i18n::ExecutionMapGuiPlugin.Toolbar.Tooltip",
      image = "execution-map.svg")
  public void generateFromWorkflowToolbar() {
    HopGuiWorkflowGraph workflowGraph = HopGui.getActiveWorkflowGraph();
    if (workflowGraph != null) {
      generateFromWorkflow(workflowGraph);
    }
  }

  @GuiToolbarElement(
      root = HopGuiPipelineGraph.GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = "HopGuiPipelineGraph-ToolBar-10047-execution-map",
      toolTip = "i18n::ExecutionMapGuiPlugin.Toolbar.Tooltip",
      image = "execution-map.svg")
  public void generateFromPipelineToolbar() {
    HopGuiPipelineGraph pipelineGraph = HopGui.getActivePipelineGraph();
    if (pipelineGraph != null) {
      generateFromPipeline(pipelineGraph);
    }
  }

  @GuiContextAction(
      id = "workflow-graph-workflow-zzz-execution-map",
      parentId = HopGuiWorkflowContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::ExecutionMapGuiPlugin.Action.Name",
      tooltip = "i18n::ExecutionMapGuiPlugin.Action.Tooltip",
      image = "execution-map.svg",
      category =
          "i18n:org.apache.hop.ui.hopgui.file.workflow:HopGuiWorkflowGraph.ContextualAction.Category.Basic.Text",
      categoryOrder = "9")
  public void generateFromWorkflowContext(HopGuiWorkflowContext context) {
    if (context.getWorkflowGraph() != null) {
      generateFromWorkflow(context.getWorkflowGraph());
    }
  }

  @GuiContextAction(
      id = "pipeline-graph-pipeline-zzz-execution-map",
      parentId = HopGuiPipelineContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::ExecutionMapGuiPlugin.Action.Name",
      tooltip = "i18n::ExecutionMapGuiPlugin.Action.Tooltip",
      image = "execution-map.svg",
      category =
          "i18n:org.apache.hop.ui.hopgui.file.pipeline:HopGuiPipelineGraph.ContextualAction.Category.Basic.Text",
      categoryOrder = "9")
  public void generateFromPipelineContext(HopGuiPipelineContext context) {
    if (context.getPipelineGraph() != null) {
      generateFromPipeline(context.getPipelineGraph());
    }
  }

  @GuiContextAction(
      id = "workflow-zzz-action-execution-map",
      parentId = HopGuiWorkflowActionContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::ExecutionMapGuiPlugin.SubRoot.Action.Name",
      tooltip = "i18n::ExecutionMapGuiPlugin.SubRoot.Action.Tooltip",
      image = "execution-map.svg",
      category =
          "i18n:org.apache.hop.ui.hopgui.file.workflow:HopGuiWorkflowGraph.ContextualAction.Category.Basic.Text",
      categoryOrder = "1")
  public void generateFromWorkflowActionContext(HopGuiWorkflowActionContext context) {
    if (context.getWorkflowGraph() == null || context.getActionMeta() == null) {
      return;
    }
    try {
      String rootPath =
          ExecutionMapSubRootSupport.resolveFromWorkflowAction(
              context.getActionMeta(),
              context.getWorkflowMeta(),
              context.getWorkflowGraph().getVariables(),
              context.getWorkflowGraph().getHopGui().getMetadataProvider());
      generateAndOpen(
          context.getWorkflowGraph().getHopGui(),
          context.getWorkflowGraph().getVariables(),
          rootPath,
          defaultOutputPath(rootPath));
    } catch (Exception e) {
      new ErrorDialog(
          context.getWorkflowGraph().getHopGui().getShell(),
          "Execution map",
          "Failed to resolve sub-root artifact for this action",
          e);
    }
  }

  @GuiContextAction(
      id = "pipeline-zzz-transform-execution-map",
      parentId = HopGuiPipelineTransformContext.CONTEXT_ID,
      type = GuiActionType.Modify,
      name = "i18n::ExecutionMapGuiPlugin.SubRoot.Action.Name",
      tooltip = "i18n::ExecutionMapGuiPlugin.SubRoot.Action.Tooltip",
      image = "execution-map.svg",
      category =
          "i18n:org.apache.hop.ui.hopgui.file.pipeline:HopGuiPipelineGraph.ContextualAction.Category.Basic.Text",
      categoryOrder = "1")
  public void generateFromPipelineTransformContext(HopGuiPipelineTransformContext context) {
    if (context.getPipelineGraph() == null || context.getTransformMeta() == null) {
      return;
    }
    try {
      String rootPath =
          ExecutionMapSubRootSupport.resolveFromPipelineTransform(
              context.getTransformMeta(),
              context.getPipelineMeta(),
              context.getPipelineGraph().getVariables(),
              context.getPipelineGraph().getHopGui().getMetadataProvider());
      generateAndOpen(
          context.getPipelineGraph().getHopGui(),
          context.getPipelineGraph().getVariables(),
          rootPath,
          defaultOutputPath(rootPath));
    } catch (Exception e) {
      new ErrorDialog(
          context.getPipelineGraph().getHopGui().getShell(),
          "Execution map",
          "Failed to resolve sub-root artifact for this transform",
          e);
    }
  }

  private static void generateFromWorkflow(HopGuiWorkflowGraph workflowGraph) {
    WorkflowMeta workflowMeta = workflowGraph.getWorkflowMeta();
    if (workflowMeta == null || workflowMeta.getFilename() == null) {
      return;
    }
    generateAndOpen(
        workflowGraph.getHopGui(),
        workflowGraph.getVariables(),
        workflowMeta.getFilename(),
        defaultOutputPath(workflowMeta.getFilename()));
  }

  private static void generateFromPipeline(HopGuiPipelineGraph pipelineGraph) {
    if (pipelineGraph.getPipelineMeta() == null
        || pipelineGraph.getPipelineMeta().getFilename() == null) {
      return;
    }
    generateAndOpen(
        pipelineGraph.getHopGui(),
        pipelineGraph.getVariables(),
        pipelineGraph.getPipelineMeta().getFilename(),
        defaultOutputPath(pipelineGraph.getPipelineMeta().getFilename()));
  }

  private static String defaultOutputPath(String sourcePath) {
    Path path = Path.of(sourcePath);
    String base = path.getFileName().toString();
    int dot = base.lastIndexOf('.');
    String stem = dot > 0 ? base.substring(0, dot) : base;
    Path parent = path.getParent();
    String output = stem + ".hem";
    return parent != null ? parent.resolve(output).toString() : output;
  }

  private static void generateAndOpen(
      HopGui hopGui,
      org.apache.hop.core.variables.IVariables variables,
      String sourcePath,
      String suggestedOutput) {
    ExecutionMapGenerationDialog.Result generationOptions =
        ExecutionMapGenerationDialog.open(
            hopGui.getShell(), ExecutionMapGenerationDialog.Purpose.GENERATE);
    if (generationOptions == null) {
      return;
    }

    HopExecutionMapFileType executionMapFileType = new HopExecutionMapFileType();
    String outputPath;
    try {
      outputPath =
          BaseDialog.presentFileDialog(
              true,
              hopGui.getShell(),
              null,
              variables,
              HopVfs.getFileObject(variables.resolve(suggestedOutput)),
              executionMapFileType.getFilterExtensions(),
              executionMapFileType.getFilterNames(),
              true);
    } catch (Exception e) {
      new ErrorDialog(hopGui.getShell(), "Execution map", "Failed to open save file dialog", e);
      return;
    }
    if (outputPath == null) {
      return;
    }
    try {
      String resolvedOutputPath = variables.resolve(outputPath);
      ExecutionMapService.GenerateResult result =
          ExecutionMapService.generate(
              sourcePath,
              resolvedOutputPath,
              variables,
              hopGui.getMetadataProvider(),
              generationOptions.getCrawlOptions());
      if (generationOptions.isExportLineage()) {
        String lineagePath = ExecutionMapService.defaultLineageOutputPath(result.getOutputPath());
        ExecutionMapService.exportLineage(result.getDocument(), lineagePath, variables);
      }
      IHopFileTypeHandler handler =
          executionMapFileType.openFile(hopGui, resolvedOutputPath, variables);
      if (handler != null) {
        HopGui.getExplorerPerspective().setActiveFileTypeHandler(handler);
      }
      StringBuilder message =
          new StringBuilder("Execution map written to:\n").append(resolvedOutputPath);
      if (generationOptions.isExportLineage()) {
        message
            .append("\nOpenLineage JSON written to:\n")
            .append(ExecutionMapService.defaultLineageOutputPath(resolvedOutputPath));
      }
      MessageBox box = new MessageBox(hopGui.getShell(), SWT.ICON_INFORMATION | SWT.OK);
      box.setText("Execution map");
      box.setMessage(message.toString());
      box.open();
    } catch (Exception e) {
      new ErrorDialog(hopGui.getShell(), "Execution map", "Failed to generate execution map", e);
    }
  }
}
