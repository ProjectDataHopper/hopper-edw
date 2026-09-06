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
package org.hopper.edw.datavault.documentation.command;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.config.plugin.ConfigPlugin;
import org.apache.hop.core.config.plugin.IConfigOptions;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.hop.Hop;
import org.apache.hop.hop.plugin.HopCommand;
import org.apache.hop.hop.plugin.IHopCommand;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.metadata.serializer.multi.MultiMetadataProvider;
import org.hopper.edw.datavault.documentation.ProjectDocumentationOptions;
import org.hopper.edw.datavault.documentation.ProjectDocumentationResult;
import org.hopper.edw.datavault.documentation.ProjectDocumentationService;
import org.hopper.edw.datavault.documentation.model.DocumentationScope;
import picocli.CommandLine;

@Getter
@Setter
@CommandLine.Command(
    mixinStandardHelpOptions = true,
    name = "project-doc",
    description =
        "Generate a self-contained HTML documentation set for a Hop project (no server required)."
            + " Enable a project with -j/--project OR an environment with -e/--environment (not both).")
@HopCommand(id = "project-doc", description = "Generate static HTML project documentation")
public class ProjectDocumentationCommand implements Runnable, IHopCommand, IHasHopMetadataProvider {

  private ILogChannel log;
  private CommandLine cmd;
  private IVariables variables;
  private MultiMetadataProvider metadataProvider;

  @CommandLine.Option(
      names = {"-s", "--source-folder"},
      description = "Source folder to document (default ${PROJECT_HOME})")
  private String sourceFolder;

  @CommandLine.Option(
      names = {"-t", "--target-folder"},
      description = "Output folder for the HTML documentation set",
      required = true)
  private String targetFolder;

  @CommandLine.Option(
      names = {"-n", "--project-name"},
      description = "Project name shown in the generated site header")
  private String projectName;

  @CommandLine.Option(
      names = {"--scope"},
      description = "PROJECT (default) or ENVIRONMENT")
  private String scope;

  @CommandLine.Option(
      names = {"--theme"},
      description = "CSS theme: default, compact, or high-contrast")
  private String theme;

  @CommandLine.Option(
      names = {"--no-parameters"},
      description = "Omit pipeline/workflow parameters")
  private boolean noParameters;

  @CommandLine.Option(
      names = {"--no-notes"},
      description = "Omit canvas notes")
  private boolean noNotes;

  @CommandLine.Option(
      names = {"--no-metadata"},
      description = "Omit Hop metadata objects")
  private boolean noMetadata;

  @CommandLine.Option(
      names = {"--no-catalog"},
      description = "Omit catalog record definitions")
  private boolean noCatalog;

  @CommandLine.Option(
      names = {"--no-lineage"},
      description = "Omit source-to-target lineage")
  private boolean noLineage;

  @CommandLine.Option(
      names = {"--no-dark-svg"},
      description = "Do not generate dark-theme SVG copies")
  private boolean noDarkSvg;

  @CommandLine.Option(
      names = {"--project-home"},
      description = "Optional override for ${PROJECT_HOME}")
  private String projectHome;

  public ProjectDocumentationCommand() {}

  @Override
  public void initialize(
      CommandLine cmd, IVariables variables, MultiMetadataProvider metadataProvider)
      throws HopException {
    this.cmd = cmd;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.log = new LogChannel("project-doc");
    Hop.addMixinPlugins(cmd, ConfigPlugin.CATEGORY_RUN);
  }

  @Override
  public void run() {
    try {
      System.setProperty(Const.HOP_PLATFORM_RUNTIME, "DOC");
      handleMixinActions();
      if (StringUtils.isNotEmpty(projectHome)) {
        variables.setVariable(
            ProjectDocumentationService.VAR_PROJECT_HOME, variables.resolve(projectHome));
      }

      ProjectDocumentationOptions options = ProjectDocumentationOptions.defaults();
      options.setSourceFolder(sourceFolder);
      options.setTargetFolder(targetFolder);
      options.setProjectName(projectName);
      options.setScope(DocumentationScope.parse(scope));
      options.setTheme(theme);
      options.setIncludingParameters(!noParameters);
      options.setIncludingNotes(!noNotes);
      options.setIncludingMetadata(!noMetadata);
      options.setIncludingCatalog(!noCatalog);
      options.setIncludingLineage(!noLineage);
      options.setDarkSvg(!noDarkSvg);

      ProjectDocumentationResult result =
          ProjectDocumentationService.generate(options, variables, metadataProvider, log);
      log.logBasic("Wrote documentation to " + result.getOutputFolder());
      if (result.getErrors() > 0) {
        System.exit(1);
      }
    } catch (Exception e) {
      log.logError("Project documentation failed", e);
      System.exit(1);
    }
  }

  private void handleMixinActions() throws HopException {
    Map<String, Object> mixins = cmd.getMixins();
    for (Object mixin : mixins.values()) {
      if (mixin instanceof IConfigOptions configOptions) {
        configOptions.handleOption(log, this, variables);
      }
    }
  }
}
