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
package org.apache.hop.datavault.workflow.actions.exportarchitecture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.architecture.ArchitectureExportService;
import org.apache.hop.datavault.architecture.ArchitectureExportService.ExportResult;
import org.apache.hop.datavault.architecture.ArchitectureViewType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/**
 * Exports a Draw.io architecture diagram from a workflow root (SOLUTION view) and/or model lineage
 * (DATA view). Models remain the source of truth; the export is derived.
 */
@Action(
    id = "EXPORT_ARCHITECTURE",
    name = "i18n::ActionExportArchitecture.Name",
    description = "i18n::ActionExportArchitecture.Description",
    image = "execution-map.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionExportArchitecture.Keywords",
    documentationUrl = "/workflow/actions/exportarchitecture.html")
@GuiPlugin(description = "Export Architecture action")
@Getter
@Setter
public class ActionExportArchitecture extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionExportArchitecture.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "EXPORT_ARCHITECTURE_ACTION";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FILENAME,
      variables = true,
      label = "i18n::ActionExportArchitecture.Root.Label",
      toolTip = "i18n::ActionExportArchitecture.Root.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String rootArtifactFilename;

  @GuiWidgetElement(
      order = "0150",
      type = GuiElementType.METADATA,
      metadata = ResourceDefinitionGroupMeta.class,
      label = "i18n::ActionExportArchitecture.ResourceDefinitionGroup.Label",
      toolTip = "i18n::ActionExportArchitecture.ResourceDefinitionGroup.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String resourceDefinitionGroup;

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.COMBO,
      comboValuesMethod = "getViewTypeCodes",
      label = "i18n::ActionExportArchitecture.ViewType.Label",
      toolTip = "i18n::ActionExportArchitecture.ViewType.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String viewType = ArchitectureViewType.SOLUTION.name();

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.FILENAME,
      variables = true,
      label = "i18n::ActionExportArchitecture.OutputFile.Label",
      toolTip = "i18n::ActionExportArchitecture.OutputFile.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String outputDrawioFile;

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionExportArchitecture.AlsoExportData.Label",
      toolTip = "i18n::ActionExportArchitecture.AlsoExportData.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean alsoExportDataView;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.FILENAME,
      variables = true,
      label = "i18n::ActionExportArchitecture.DataOutputFile.Label",
      toolTip = "i18n::ActionExportArchitecture.DataOutputFile.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String dataOutputDrawioFile;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionExportArchitecture.AlsoExportModels.Label",
      toolTip = "i18n::ActionExportArchitecture.AlsoExportModels.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean alsoExportModelDiagrams;

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionExportArchitecture.ModelsOutputFolder.Label",
      toolTip = "i18n::ActionExportArchitecture.ModelsOutputFolder.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String modelsOutputFolder;

  public ActionExportArchitecture() {
    this("");
  }

  public ActionExportArchitecture(String name) {
    super(name, "");
  }

  /**
   * Combo values for {@link #viewType}.
   *
   * <p>Hop GUI {@code comboValuesMethod} contract: {@code (ILogChannel, IHopMetadataProvider) ->
   * List&lt;String&gt;}.
   */
  public List<String> getViewTypeCodes(ILogChannel log, IHopMetadataProvider metadataProvider) {
    return Arrays.asList(
        ArchitectureViewType.SOLUTION.name(),
        ArchitectureViewType.DATA.name(),
        ArchitectureViewType.MODEL.name(),
        ArchitectureViewType.MODELS.name(),
        ArchitectureViewType.END_TO_END.name());
  }

  @Override
  public Result execute(Result prevResult, int nr) throws HopException {
    Result result = prevResult != null ? prevResult : new Result();
    try {
      ArchitectureViewType view = parseViewType(viewType);
      String root = resolve(rootArtifactFilename);
      String groupName = resolve(resourceDefinitionGroup);

      if (view == ArchitectureViewType.DATA
          || view == ArchitectureViewType.MODEL
          || view == ArchitectureViewType.MODELS) {
        List<String> modelPaths = resolveModelPaths(groupName, root);
        if (modelPaths.isEmpty()) {
          throw new HopException(
              BaseMessages.getString(PKG, "ActionExportArchitecture.Error.MissingModels"));
        }
        if (view == ArchitectureViewType.DATA) {
          ExportResult dataExport =
              ArchitectureExportService.exportDataDrawio(
                  modelPaths, resolve(outputDrawioFile), this, getMetadataProvider());
          logWroteData(dataExport);
        } else if (view == ArchitectureViewType.MODEL) {
          String dir = resolve(modelsOutputFolder);
          if (Utils.isEmpty(dir)) {
            dir = resolve(outputDrawioFile);
          }
          List<ExportResult> modelExports =
              ArchitectureExportService.exportLayerModelDrawios(
                  modelPaths, dir, this, getMetadataProvider());
          for (ExportResult modelExport : modelExports) {
            logWroteModel(modelExport);
          }
        } else {
          // MODELS — one Draw.io per model under type subfolders
          String dir = resolve(modelsOutputFolder);
          if (Utils.isEmpty(dir)) {
            dir = resolve(outputDrawioFile);
          }
          List<ExportResult> modelExports =
              ArchitectureExportService.exportPerModelDrawios(
                  modelPaths, dir, this, getMetadataProvider());
          for (ExportResult modelExport : modelExports) {
            logWrotePerModel(modelExport);
          }
        }
      } else {
        // SOLUTION or END_TO_END: crawl workflow / load .hem
        if (Utils.isEmpty(root)) {
          throw new HopException(
              BaseMessages.getString(PKG, "ActionExportArchitecture.Error.MissingRoot"));
        }
        ExportResult export =
            ArchitectureExportService.exportSolutionDrawio(
                root, resolve(outputDrawioFile), this, getMetadataProvider(), true);
        logBasic(
            BaseMessages.getString(
                PKG,
                "ActionExportArchitecture.Log.WroteSolution",
                export.getOutputPath(),
                export.getGraph().nodeCount(),
                export.getGraph().edgeCount()));
        for (String warning : export.getWarnings()) {
          logBasic(warning);
        }

        List<String> mapModels =
            ArchitectureExportService.modelPathsFromExecutionMap(export.getExecutionMap());
        List<String> modelPaths = resolveModelPaths(groupName, null);
        if (modelPaths.isEmpty()) {
          modelPaths = mapModels;
        }
        if (alsoExportDataView) {
          if (modelPaths.isEmpty()) {
            logBasic(BaseMessages.getString(PKG, "ActionExportArchitecture.Log.NoModelsForData"));
          } else {
            ExportResult dataExport =
                ArchitectureExportService.exportDataDrawio(
                    modelPaths, resolve(dataOutputDrawioFile), this, getMetadataProvider());
            logWroteData(dataExport);
          }
        }
        if (alsoExportModelDiagrams) {
          if (modelPaths.isEmpty()) {
            logBasic(BaseMessages.getString(PKG, "ActionExportArchitecture.Log.NoModelsForData"));
          } else {
            List<ExportResult> modelExports =
                ArchitectureExportService.exportLayerModelDrawios(
                    modelPaths, resolve(modelsOutputFolder), this, getMetadataProvider());
            for (ExportResult modelExport : modelExports) {
              logWroteModel(modelExport);
            }
          }
        }
      }

      result.setResult(true);
      result.setNrErrors(0);
    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "ActionExportArchitecture.Error.Failed"), e);
      result.setResult(false);
      result.setNrErrors(1);
    }
    return result;
  }

  /**
   * When a resource definition group is set, use its model files; otherwise use fallback paths
   * (from root for model views, or from the execution map for SOLUTION also-export).
   */
  private List<String> resolveModelPaths(String groupName, String rootPaths) throws HopException {
    List<String> fallback = splitPaths(rootPaths);
    return ArchitectureExportService.resolveModelPaths(groupName, fallback, getMetadataProvider());
  }

  private void logWroteData(ExportResult dataExport) {
    logBasic(
        BaseMessages.getString(
            PKG,
            "ActionExportArchitecture.Log.WroteData",
            dataExport.getOutputPath(),
            dataExport.getGraph().nodeCount(),
            dataExport.getGraph().edgeCount()));
    for (String warning : dataExport.getWarnings()) {
      logBasic(warning);
    }
  }

  private void logWroteModel(ExportResult modelExport) {
    for (String warning : modelExport.getWarnings()) {
      logBasic(warning);
    }
    if (modelExport.getGraph() != null && modelExport.getGraph().nodeCount() > 0) {
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionExportArchitecture.Log.WroteModel",
              modelExport.getOutputPath(),
              modelExport.getGraph().nodeCount(),
              modelExport.getGraph().edgeCount()));
    }
  }

  private void logWrotePerModel(ExportResult modelExport) {
    for (String warning : modelExport.getWarnings()) {
      logBasic(warning);
    }
    if (modelExport.getGraph() != null && modelExport.getGraph().nodeCount() > 0) {
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionExportArchitecture.Log.WrotePerModel",
              modelExport.getOutputPath(),
              modelExport.getGraph().nodeCount(),
              modelExport.getGraph().edgeCount()));
    }
  }

  private static ArchitectureViewType parseViewType(String code) {
    if (Utils.isEmpty(code)) {
      return ArchitectureViewType.SOLUTION;
    }
    try {
      return ArchitectureViewType.valueOf(code.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return ArchitectureViewType.SOLUTION;
    }
  }

  private static List<String> splitPaths(String value) {
    List<String> paths = new ArrayList<>();
    if (Utils.isEmpty(value)) {
      return paths;
    }
    Arrays.stream(value.split("[,;\\n]"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(paths::add);
    return paths;
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }

  @Override
  public boolean isUnconditional() {
    return false;
  }
}
