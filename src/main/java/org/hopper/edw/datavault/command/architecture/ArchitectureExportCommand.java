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
package org.hopper.edw.datavault.command.architecture;

import java.util.Arrays;
import java.util.List;
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
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.hop.Hop;
import org.apache.hop.hop.plugin.HopCommand;
import org.apache.hop.hop.plugin.IHopCommand;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.metadata.serializer.multi.MultiMetadataProvider;
import org.hopper.edw.datavault.architecture.ArchitectureExportService;
import org.hopper.edw.datavault.architecture.ArchitectureExportService.ExportResult;
import org.hopper.edw.datavault.architecture.ArchitectureViewType;
import picocli.CommandLine;

/**
 * CLI export of Draw.io architecture diagrams. Supports Hop project/environment mixins ({@code -j}
 * <em>or</em> {@code -e}, not both) via {@code ProjectsRunOptionPlugin} so {@code PROJECT_HOME} and
 * relative paths resolve like hop-run.
 */
@Getter
@Setter
@CommandLine.Command(
    mixinStandardHelpOptions = true,
    name = "architecture-export",
    description =
        "Export Draw.io architecture diagrams (SOLUTION from workflow/.hem, DATA/MODEL/MODELS from models)."
            + " Enable a project with -j/--project OR an environment with -e/--environment"
            + " (not both). Relative -f/-o paths are resolved under ${PROJECT_HOME}.")
@HopCommand(id = "architecture-export", description = "Export architecture diagrams to Draw.io")
public class ArchitectureExportCommand implements Runnable, IHopCommand, IHasHopMetadataProvider {

  public static final String VAR_PROJECT_HOME = "PROJECT_HOME";

  private ILogChannel log;
  private CommandLine cmd;
  private IVariables variables;
  private MultiMetadataProvider metadataProvider;

  @CommandLine.Option(
      names = {"-f", "--file"},
      description =
          "Root workflow, .hem, or comma-separated model paths (for DATA/MODEL/MODELS when no --group)."
              + " Relative to the enabled project when using -j/-e.")
  private String file;

  @CommandLine.Option(
      names = {"-g", "--group", "--resource-definition-group"},
      description =
          "Resource definition group name. When set, supplies model files for DATA, MODEL, MODELS,"
              + " and SOLUTION --also-data / --also-models (overrides -f model list and map discovery).")
  private String resourceDefinitionGroup;

  @CommandLine.Option(
      names = {"-o", "--output"},
      description = "Output .drawio path (variables and project-relative paths supported)")
  private String output;

  @CommandLine.Option(
      names = {"--view"},
      defaultValue = "SOLUTION",
      description =
          "SOLUTION, DATA (inventory), MODEL (aggregated DV/BV/DM ELK diagrams),"
              + " MODELS (one Draw.io per model under type subfolders), or END_TO_END")
  private String view;

  @CommandLine.Option(
      names = {"--also-data"},
      description =
          "Also export DATA inventory (tables/sources, no ER edges) from models on the execution map"
              + " or resource definition group")
  private boolean alsoData;

  @CommandLine.Option(
      names = {"--data-output"},
      description = "Output path for DATA inventory when --also-data is set")
  private String dataOutput;

  @CommandLine.Option(
      names = {"--also-models"},
      description =
          "Also export aggregated layer model Draw.io files (data-vault, business-vault, dimensional)"
              + " from models on the execution map or resource definition group, laid out with ELK")
  private boolean alsoModels;

  @CommandLine.Option(
      names = {"--models-output"},
      description =
          "Directory for MODEL aggregates and MODELS per-file diagrams"
              + " (default ${PROJECT_HOME}/work/architecture/models)."
              + " MODELS writes data-vault/, business-vault/, dimensional/ subfolders.")
  private String modelsOutput;

  @CommandLine.Option(
      names = {"--project-home"},
      description =
          "Optional override for ${PROJECT_HOME}."
              + " Prefer -j/--project and -e/--environment from the projects plugin when possible.")
  private String projectHome;

  public ArchitectureExportCommand() {}

  @Override
  public void initialize(
      CommandLine cmd, IVariables variables, MultiMetadataProvider metadataProvider)
      throws HopException {
    this.cmd = cmd;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.log = new LogChannel("architecture-export");
    // Loads ProjectsRunOptionPlugin (-j/--project, -e/--environment) and other RUN mixins.
    Hop.addMixinPlugins(cmd, ConfigPlugin.CATEGORY_RUN);
  }

  @Override
  public void run() {
    try {
      System.setProperty(Const.HOP_PLATFORM_RUNTIME, "GUI");
      handleMixinActions();
      applyProjectHome();

      ArchitectureViewType viewType;
      try {
        viewType = ArchitectureViewType.valueOf(view.trim().toUpperCase());
      } catch (Exception e) {
        viewType = ArchitectureViewType.SOLUTION;
      }

      String groupName =
          !Utils.isEmpty(resourceDefinitionGroup)
              ? variables.resolve(resourceDefinitionGroup)
              : null;

      if (viewType == ArchitectureViewType.DATA
          || viewType == ArchitectureViewType.MODEL
          || viewType == ArchitectureViewType.MODELS) {
        List<String> models = resolveModelPaths(groupName, file);
        if (models.isEmpty()) {
          throw new HopException(
              "Missing --group resource definition group or --file model path(s) for "
                  + viewType
                  + " view");
        }
        if (viewType == ArchitectureViewType.DATA) {
          ExportResult result =
              ArchitectureExportService.exportDataDrawio(
                  models, output, variables, metadataProvider);
          log.logBasic(
              "Wrote DATA inventory: "
                  + result.getOutputPath()
                  + " ("
                  + result.getGraph().nodeCount()
                  + " nodes)");
        } else if (viewType == ArchitectureViewType.MODEL) {
          String dir = !Utils.isEmpty(modelsOutput) ? modelsOutput : output;
          List<ExportResult> results =
              ArchitectureExportService.exportLayerModelDrawios(
                  models, dir, variables, metadataProvider);
          for (ExportResult r : results) {
            logModelResult(r, true);
          }
        } else {
          String dir = !Utils.isEmpty(modelsOutput) ? modelsOutput : output;
          List<ExportResult> results =
              ArchitectureExportService.exportPerModelDrawios(
                  models, dir, variables, metadataProvider);
          for (ExportResult r : results) {
            logModelResult(r, false);
          }
        }
      } else {
        if (Utils.isEmpty(file)) {
          throw new HopException("Missing --file root workflow, .hem, or model path(s)");
        }
        ExportResult result =
            ArchitectureExportService.exportSolutionDrawio(
                file, output, variables, metadataProvider, true);
        log.logBasic(
            "Wrote SOLUTION architecture: "
                + result.getOutputPath()
                + " ("
                + result.getGraph().nodeCount()
                + " nodes, "
                + result.getGraph().edgeCount()
                + " edges)");
        List<String> mapModels =
            ArchitectureExportService.modelPathsFromExecutionMap(result.getExecutionMap());
        List<String> models = resolveModelPaths(groupName, null);
        if (models.isEmpty()) {
          models = mapModels;
        }
        if (alsoData) {
          if (models.isEmpty()) {
            log.logBasic("No models discovered for DATA inventory export");
          } else {
            ExportResult data =
                ArchitectureExportService.exportDataDrawio(
                    models, dataOutput, variables, metadataProvider);
            log.logBasic(
                "Wrote DATA inventory: "
                    + data.getOutputPath()
                    + " ("
                    + data.getGraph().nodeCount()
                    + " nodes)");
          }
        }
        if (alsoModels) {
          if (models.isEmpty()) {
            log.logBasic("No models discovered for MODEL export");
          } else {
            List<ExportResult> modelResults =
                ArchitectureExportService.exportLayerModelDrawios(
                    models, modelsOutput, variables, metadataProvider);
            for (ExportResult r : modelResults) {
              logModelResult(r, true);
            }
          }
        }
      }
    } catch (Exception e) {
      log.logError("Architecture export failed", e);
      System.exit(1);
    }
  }

  private List<String> resolveModelPaths(String groupName, String filePaths) throws HopException {
    List<String> fromFile = List.of();
    if (!Utils.isEmpty(filePaths)) {
      fromFile =
          Arrays.stream(filePaths.split("[,;]"))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .toList();
    }
    return ArchitectureExportService.resolveModelPaths(groupName, fromFile, metadataProvider);
  }

  /**
   * Applies project/environment mixins (including {@code -j}/{@code -e} from {@code
   * ProjectsRunOptionPlugin}). Must run after picocli has parsed the command line so mixin fields
   * are populated.
   */
  private void handleMixinActions() throws HopException {
    Map<String, Object> mixins = cmd.getMixins();
    for (Object mixin : mixins.values()) {
      if (mixin instanceof IConfigOptions configOptions) {
        configOptions.handleOption(log, this, variables);
      }
    }
  }

  private void logModelResult(ExportResult r, boolean aggregated) {
    if (r.getWarnings() != null) {
      for (String warning : r.getWarnings()) {
        log.logBasic("Model export: " + warning);
      }
    }
    if (r.getGraph() != null && r.getGraph().nodeCount() > 0) {
      log.logBasic(
          (aggregated ? "Wrote aggregated MODEL diagram: " : "Wrote per-model diagram: ")
              + r.getOutputPath()
              + " ("
              + r.getGraph().nodeCount()
              + " nodes, "
              + r.getGraph().edgeCount()
              + " edges)");
    }
  }

  /** Optional explicit override after project mixins have set PROJECT_HOME. */
  private void applyProjectHome() {
    if (StringUtils.isNotEmpty(projectHome)) {
      variables.setVariable(VAR_PROJECT_HOME, variables.resolve(projectHome));
    }
  }

  @Override
  public MultiMetadataProvider getMetadataProvider() {
    return metadataProvider;
  }

  @Override
  public void setMetadataProvider(MultiMetadataProvider metadataProvider) {
    this.metadataProvider = metadataProvider;
  }
}
