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
package org.hopper.edw.datavault.command.svg;

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
import picocli.CommandLine;

@Getter
@Setter
@CommandLine.Command(
    mixinStandardHelpOptions = true,
    name = "svg",
    description = "Export pipelines, workflows, models, and execution maps to SVG")
@HopCommand(
    id = "svg",
    description = "Export pipelines, workflows, models, and execution maps to SVG")
public class SvgExportCommand implements Runnable, IHopCommand, IHasHopMetadataProvider {

  public static final String VAR_PROJECT_HOME = "PROJECT_HOME";

  private ILogChannel log;
  private CommandLine cmd;
  private IVariables variables;
  private MultiMetadataProvider metadataProvider;

  @CommandLine.Option(
      names = {"-f", "--file"},
      description = "Input file (.hpl, .hwf, .hdv, .hbv, .hdm, or .hem)")
  private String file;

  @CommandLine.Option(
      names = {"-o", "--output"},
      description = "Output SVG file (default: same basename as input)")
  private String output;

  @CommandLine.Option(
      names = {"-s", "--source-folder"},
      description = "Source folder for batch export")
  private String sourceFolder;

  @CommandLine.Option(
      names = {"-t", "--target-folder"},
      description = "Target folder for batch export (mirrors relative paths)")
  private String targetFolder;

  @CommandLine.Option(
      names = {"-r", "--recursive"},
      description = "Recursively process sub-folders in batch mode")
  private boolean recursive;

  @CommandLine.Option(
      names = {"--no-notes"},
      description = "Exclude notes from the SVG image")
  private boolean noNotes;

  @CommandLine.Option(
      names = {"--magnification"},
      defaultValue = "1.0",
      description = "Magnification factor for the SVG canvas")
  private float magnification;

  @CommandLine.Option(
      names = {"--show-hash-keys"},
      description = "Show hash key field names on Data Vault tables (.hdv only)")
  private boolean showHashKeys;

  @CommandLine.Option(
      names = {"--project-home"},
      description = "Project home folder (sets ${PROJECT_HOME})")
  private String projectHome;

  @CommandLine.Option(
      names = {"--export-scope"},
      description = "Execution map export scope: focused (default) or full")
  private String exportScope;

  public SvgExportCommand() {}

  @Override
  public void initialize(
      CommandLine cmd, IVariables variables, MultiMetadataProvider metadataProvider)
      throws HopException {
    this.cmd = cmd;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.log = new LogChannel("HopSvg");
    Hop.addMixinPlugins(cmd, ConfigPlugin.CATEGORY_RUN);
  }

  protected void handleMixinActions() throws HopException {
    Map<String, Object> mixins = cmd.getMixins();
    for (Object mixin : mixins.values()) {
      if (mixin instanceof IConfigOptions configOptions) {
        configOptions.handleOption(log, this, variables);
      }
    }
  }

  @Override
  public void run() {
    try {
      System.setProperty(Const.HOP_PLATFORM_RUNTIME, "SVG");
      handleMixinActions();
      applyProjectHome();

      SvgRenderOptions options =
          SvgRenderOptions.fromCli(
              noNotes, magnification, showHashKeys, ExecutionMapExportScope.fromCli(exportScope));

      if (StringUtils.isNotEmpty(file)) {
        exportSingleFile(options);
        return;
      }

      if (StringUtils.isNotEmpty(sourceFolder)) {
        exportBatch(options);
        return;
      }

      log.logError("Specify --file for single export or --source-folder for batch export.");
      System.exit(1);
    } catch (Exception e) {
      log.logError("Error exporting SVG", e);
      System.exit(1);
    }
  }

  private void applyProjectHome() {
    if (StringUtils.isNotEmpty(projectHome)) {
      variables.setVariable(VAR_PROJECT_HOME, variables.resolve(projectHome));
    }
    if (StringUtils.isEmpty(sourceFolder)
        && StringUtils.isNotEmpty(variables.getVariable(VAR_PROJECT_HOME))) {
      sourceFolder = variables.getVariable(VAR_PROJECT_HOME);
    }
  }

  private void exportSingleFile(SvgRenderOptions options) throws HopException {
    String resolvedFile = variables.resolve(file);
    String outputPath =
        StringUtils.isNotEmpty(output)
            ? variables.resolve(output)
            : SvgExportService.defaultOutputPath(resolvedFile);
    SvgExportService.exportFile(
        resolvedFile, outputPath, options, variables, metadataProvider, log);
    log.logBasic("Finished SVG export for " + resolvedFile);
  }

  private void exportBatch(SvgRenderOptions options) throws HopException {
    if (StringUtils.isEmpty(targetFolder)) {
      log.logError("Batch export requires --target-folder.");
      System.exit(1);
    }
    log.logBasic("Exporting SVG from " + sourceFolder + " to " + targetFolder);
    SvgExportService.exportFolder(
        sourceFolder, targetFolder, recursive, options, variables, metadataProvider, log);
    log.logBasic("Finished batch SVG export.");
  }
}
