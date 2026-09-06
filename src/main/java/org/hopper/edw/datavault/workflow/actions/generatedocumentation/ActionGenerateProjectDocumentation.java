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
package org.hopper.edw.datavault.workflow.actions.generatedocumentation;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;
import org.hopper.edw.datavault.documentation.ProjectDocumentationOptions;
import org.hopper.edw.datavault.documentation.ProjectDocumentationResult;
import org.hopper.edw.datavault.documentation.ProjectDocumentationService;
import org.hopper.edw.datavault.documentation.model.DocumentationScope;

@Action(
    id = "GENERATE_PROJECT_DOCUMENTATION",
    name = "i18n::ActionGenerateProjectDocumentation.Name",
    description = "i18n::ActionGenerateProjectDocumentation.Description",
    image = "edw-logo.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionGenerateProjectDocumentation.Keywords",
    documentationUrl = "/help/action-generate-project-documentation-dialog.html")
@GuiPlugin(description = "Generate project documentation action")
@Getter
@Setter
public class ActionGenerateProjectDocumentation extends ActionBase implements Cloneable, IAction {

  private static final Class<?> PKG = ActionGenerateProjectDocumentation.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "GENERATE_PROJECT_DOCUMENTATION_ACTION";

  @GuiWidgetElement(
      order = "0100",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionGenerateProjectDocumentation.Target.Label",
      toolTip = "i18n::ActionGenerateProjectDocumentation.Target.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String targetFolder = "${PROJECT_HOME}/work/documentation";

  @GuiWidgetElement(
      order = "0200",
      type = GuiElementType.FOLDER,
      variables = true,
      label = "i18n::ActionGenerateProjectDocumentation.Source.Label",
      toolTip = "i18n::ActionGenerateProjectDocumentation.Source.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String sourceFolder;

  @GuiWidgetElement(
      order = "0300",
      type = GuiElementType.COMBO,
      comboValuesMethod = "getScopeCodes",
      label = "i18n::ActionGenerateProjectDocumentation.Scope.Label",
      toolTip = "i18n::ActionGenerateProjectDocumentation.Scope.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String scope = DocumentationScope.PROJECT.name();

  @GuiWidgetElement(
      order = "0400",
      type = GuiElementType.COMBO,
      comboValuesMethod = "getThemeCodes",
      label = "i18n::ActionGenerateProjectDocumentation.Theme.Label",
      toolTip = "i18n::ActionGenerateProjectDocumentation.Theme.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String theme = ProjectDocumentationOptions.THEME_DEFAULT;

  @GuiWidgetElement(
      order = "0500",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateProjectDocumentation.Parameters.Label",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includingParameters = true;

  @GuiWidgetElement(
      order = "0600",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateProjectDocumentation.Notes.Label",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includingNotes = true;

  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateProjectDocumentation.Metadata.Label",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includingMetadata = true;

  @GuiWidgetElement(
      order = "0800",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateProjectDocumentation.Catalog.Label",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includingCatalog = true;

  @GuiWidgetElement(
      order = "0900",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateProjectDocumentation.Lineage.Label",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean includingLineage = true;

  @GuiWidgetElement(
      order = "1000",
      type = GuiElementType.CHECKBOX,
      label = "i18n::ActionGenerateProjectDocumentation.DarkSvg.Label",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean darkSvg = true;

  public ActionGenerateProjectDocumentation() {
    this("");
  }

  public ActionGenerateProjectDocumentation(String name) {
    super(name, "");
  }

  public List<String> getScopeCodes(ILogChannel log, IHopMetadataProvider metadataProvider) {
    return List.of(DocumentationScope.PROJECT.name(), DocumentationScope.ENVIRONMENT.name());
  }

  public List<String> getThemeCodes(ILogChannel log, IHopMetadataProvider metadataProvider) {
    return List.of(
        ProjectDocumentationOptions.THEME_DEFAULT,
        ProjectDocumentationOptions.THEME_COMPACT,
        ProjectDocumentationOptions.THEME_HIGH_CONTRAST);
  }

  public ProjectDocumentationOptions toOptions() {
    ProjectDocumentationOptions options = ProjectDocumentationOptions.defaults();
    options.setTargetFolder(resolve(targetFolder));
    options.setSourceFolder(Utils.isEmpty(sourceFolder) ? null : resolve(sourceFolder));
    options.setScope(DocumentationScope.parse(scope));
    options.setTheme(theme);
    options.setIncludingParameters(includingParameters);
    options.setIncludingNotes(includingNotes);
    options.setIncludingMetadata(includingMetadata);
    options.setIncludingCatalog(includingCatalog);
    options.setIncludingLineage(includingLineage);
    options.setDarkSvg(darkSvg);
    options.setProjectName(getVariable(ProjectDocumentationService.VAR_PROJECT_NAME));
    return options;
  }

  @Override
  public Result execute(Result prevResult, int nr) throws HopException {
    Result result = prevResult != null ? prevResult : new Result();
    try {
      ProjectDocumentationResult generated =
          ProjectDocumentationService.generate(
              toOptions(), this, getMetadataProvider(), getLogChannel());
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionGenerateProjectDocumentation.Log.Wrote",
              generated.getOutputFolder(),
              generated.getPagesWritten(),
              generated.getSearchEntries()));
      for (String warning : generated.getWarnings()) {
        logBasic(warning);
      }
      result.setResult(generated.getErrors() == 0);
      result.setNrErrors(generated.getErrors());
    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "ActionGenerateProjectDocumentation.Error.Failed"), e);
      result.setResult(false);
      result.setNrErrors(1);
    }
    return result;
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
