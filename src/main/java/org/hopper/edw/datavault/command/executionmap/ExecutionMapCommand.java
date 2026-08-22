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
package org.hopper.edw.datavault.command.executionmap;

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
import org.hopper.edw.datavault.command.executionmap.ExecutionMapService.GenerateResult;
import org.hopper.edw.datavault.executionmap.CrawlOptions;
import org.apache.hop.hop.Hop;
import org.apache.hop.hop.plugin.HopCommand;
import org.apache.hop.hop.plugin.IHopCommand;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.metadata.serializer.multi.MultiMetadataProvider;
import picocli.CommandLine;

@Getter
@Setter
@CommandLine.Command(
    mixinStandardHelpOptions = true,
    name = "execution-map",
    description = "Crawl a workflow or pipeline and write a read-only .hem execution map")
@HopCommand(
    id = "execution-map",
    description = "Crawl a workflow or pipeline and write a read-only .hem execution map")
public class ExecutionMapCommand implements Runnable, IHopCommand, IHasHopMetadataProvider {

  public static final String VAR_PROJECT_HOME = "PROJECT_HOME";

  private ILogChannel log;
  private CommandLine cmd;
  private IVariables variables;
  private MultiMetadataProvider metadataProvider;

  @CommandLine.Option(
      names = {"-f", "--file"},
      required = true,
      description = "Root workflow (.hwf) or pipeline (.hpl) to crawl")
  private String file;

  @CommandLine.Option(
      names = {"-o", "--output"},
      description = "Output .hem file (default: same basename as input)")
  private String output;

  @CommandLine.Option(
      names = {"--project-home"},
      description = "Project home folder (sets ${PROJECT_HOME})")
  private String projectHome;

  @CommandLine.Option(
      names = {"--no-generated-pipelines"},
      description = "Skip statically generated DV/BV/DM pipelines")
  private boolean noGeneratedPipelines;

  @CommandLine.Option(
      names = {"--no-datasets"},
      description = "Skip dataset/table leaf nodes")
  private boolean noDatasets;

  @CommandLine.Option(
      names = {"--include-actions"},
      description =
          "Include workflow action nodes in the execution map (still crawl action references when omitted)")
  private boolean includeActions;

  @CommandLine.Option(
      names = {"--include-transforms"},
      description =
          "Include pipeline transform nodes in the execution map (still crawl transform references when omitted)")
  private boolean includeTransforms;

  @CommandLine.Option(
      names = {"--export-lineage"},
      description = "Also write OpenLineage JSON alongside the .hem file")
  private boolean exportLineage;

  @Override
  public void initialize(
      CommandLine cmd, IVariables variables, MultiMetadataProvider metadataProvider)
      throws HopException {
    this.cmd = cmd;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.log = new LogChannel("HopExecutionMap");

    Hop.addMixinPlugins(cmd, ConfigPlugin.CATEGORY_RUN);
  }

  @Override
  public void run() {
    try {
      System.setProperty(Const.HOP_PLATFORM_RUNTIME, "GUI");
      handleMixinActions();
      applyProjectHome();
      CrawlOptions options =
          CrawlOptions.builder()
              .includeGeneratedPipelines(!noGeneratedPipelines)
              .includeDatasetNodes(!noDatasets)
              .includeWorkflowActions(includeActions)
              .includePipelineTransforms(includeTransforms)
              .build();
      GenerateResult result =
          ExecutionMapService.generate(file, output, variables, metadataProvider, options);
      log.logBasic(
          "Wrote execution map with "
              + result.getDocument().getNodesOrEmpty().size()
              + " nodes to "
              + result.getOutputPath());
      if (exportLineage) {
        String lineagePath = ExecutionMapService.defaultLineageOutputPath(result.getOutputPath());
        ExecutionMapService.exportLineage(result.getDocument(), lineagePath, variables);
        log.logBasic("Wrote OpenLineage JSON to " + lineagePath);
      }
      for (String warning : result.getWarnings()) {
        log.logBasic("Warning: " + warning);
      }
    } catch (Exception e) {
      log.logError("Error generating execution map", e);
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

  private void applyProjectHome() {
    if (StringUtils.isNotEmpty(projectHome)) {
      variables.setVariable(VAR_PROJECT_HOME, variables.resolve(projectHome));
    }
  }
}
