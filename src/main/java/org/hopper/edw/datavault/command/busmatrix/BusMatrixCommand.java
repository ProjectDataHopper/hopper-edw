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
package org.hopper.edw.datavault.command.busmatrix;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.config.plugin.ConfigPlugin;
import org.apache.hop.core.config.plugin.IConfigOptions;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.hop.Hop;
import org.apache.hop.hop.plugin.HopCommand;
import org.apache.hop.hop.plugin.IHopCommand;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.metadata.serializer.multi.MultiMetadataProvider;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.datavault.busmatrix.BusMatrix;
import org.hopper.edw.datavault.busmatrix.BusMatrixBuilder;
import org.hopper.edw.datavault.busmatrix.BusMatrixCsvWriter;
import org.hopper.edw.datavault.busmatrix.BusMatrixSvgPainter;
import org.hopper.edw.datavault.resourcedefinition.ResourceDefinitionGroupResolver;
import picocli.CommandLine;

@Getter
@Setter
@CommandLine.Command(
    mixinStandardHelpOptions = true,
    name = "bus-matrix",
    description = "Export a Kimball bus matrix for a resource definition group as CSV or SVG")
@HopCommand(id = "bus-matrix", description = "Export a Kimball bus matrix as CSV or SVG")
public class BusMatrixCommand implements Runnable, IHopCommand, IHasHopMetadataProvider {

  private ILogChannel log;
  private CommandLine cmd;
  private IVariables variables;
  private MultiMetadataProvider metadataProvider;

  @CommandLine.Option(
      names = {"-g", "--group", "--resource-definition-group"},
      description = "Resource definition group name",
      required = true)
  private String groupName;

  @CommandLine.Option(
      names = {"-f", "--format"},
      description = "csv or svg (default csv)")
  private String format = "csv";

  @CommandLine.Option(
      names = {"-o", "--output"},
      description = "Output file (variables and project-relative paths supported)",
      required = true)
  private String output;

  @CommandLine.Option(
      names = {"--hide-unused-dimensions"},
      description = "Omit dimension columns that no remaining fact uses")
  private boolean hideUnusedDimensions;

  @CommandLine.Option(
      names = {"--dark"},
      description = "Dark SVG colours (svg format only)")
  private boolean dark;

  public BusMatrixCommand() {}

  @Override
  public void initialize(
      CommandLine cmd, IVariables variables, MultiMetadataProvider metadataProvider)
      throws HopException {
    this.cmd = cmd;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.log = new LogChannel("bus-matrix");
    Hop.addMixinPlugins(cmd, ConfigPlugin.CATEGORY_RUN);
  }

  @Override
  public void run() {
    try {
      System.setProperty(Const.HOP_PLATFORM_RUNTIME, "GUI");
      handleMixinActions();
      String resolvedGroup = variables.resolve(groupName);
      ResourceDefinitionGroupMeta group =
          ResourceDefinitionGroupResolver.loadGroup(resolvedGroup, metadataProvider);
      BusMatrix matrix = BusMatrixBuilder.build(group, variables, metadataProvider);
      if (hideUnusedDimensions) {
        matrix = matrix.filtered(null, null, null, null, true);
      }
      String kind = Const.NVL(format, "csv").trim().toLowerCase();
      String content =
          "svg".equals(kind)
              ? BusMatrixSvgPainter.paint(matrix, dark)
              : BusMatrixCsvWriter.write(matrix);
      String resolvedOutput = variables.resolve(output);
      try (FileObject file = HopVfs.getFileObject(resolvedOutput)) {
        if (file.getParent() != null && !file.getParent().exists()) {
          file.getParent().createFolder();
        }
        try (java.io.OutputStream out = HopVfs.getOutputStream(file, false)) {
          out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
      }
      if (log != null) {
        log.logBasic(
            "Wrote bus matrix ("
                + matrix.getRows().size()
                + " processes × "
                + matrix.getColumns().size()
                + " dimensions) to "
                + resolvedOutput);
      }
    } catch (Exception e) {
      throw new CommandLine.ExecutionException(cmd, e.getMessage(), e);
    }
  }

  private void handleMixinActions() throws HopException {
    if (cmd == null) {
      return;
    }
    Map<String, Object> mixins = cmd.getMixins();
    for (Object mixin : mixins.values()) {
      if (mixin instanceof IConfigOptions configOptions) {
        configOptions.handleOption(log, this, variables);
      }
    }
  }
}
