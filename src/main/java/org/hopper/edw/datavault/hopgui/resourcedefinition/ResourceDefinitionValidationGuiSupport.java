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
package org.hopper.edw.datavault.hopgui.resourcedefinition;

import java.util.List;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.resourcedefinition.SchemaCompareMode;
import org.hopper.edw.datavault.resourcedefinition.SchemaImpactSimulationRequest;
import org.hopper.edw.datavault.resourcedefinition.SchemaImpactSimulationResult;
import org.hopper.edw.datavault.resourcedefinition.SchemaImpactSimulationService;
import org.hopper.edw.datavault.resourcedefinition.SchemaValidationReportFileWriter;
import org.hopper.edw.datavault.resourcedefinition.ValidationOptions;
import org.hopper.edw.datavault.resourcedefinition.ValidationReport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.widgets.Shell;

/** GUI entry points for resource definition group validation with impact enrichment. */
public final class ResourceDefinitionValidationGuiSupport {

  private static final Class<?> PKG = ResourceDefinitionValidationGuiSupport.class;

  private ResourceDefinitionValidationGuiSupport() {}

  public static ValidationReport validateAndShowResults(
      HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return null;
    }
    Shell shell = hopGui.getShell();
    try {
      ResourceDefinitionValidationOptionsDialog optionsDialog =
          new ResourceDefinitionValidationOptionsDialog(shell, hopGui, group);
      ValidationOptions options = optionsDialog.open();
      if (options == null) {
        return null;
      }
      return validateAndShowResults(hopGui, group, options);
    } catch (Exception e) {
      if (shell != null && !shell.isDisposed()) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Title"),
            BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Message"),
            e instanceof HopException ? e : new HopException(e));
      }
      return null;
    }
  }

  public static ValidationReport validateAndShowResults(
      HopGui hopGui, ResourceDefinitionGroupMeta group, ValidationOptions options)
      throws HopException {
    if (hopGui == null || group == null || options == null) {
      return null;
    }
    Shell shell = hopGui.getShell();
    SchemaImpactSimulationResult simulation =
        runSimulation(group, options, hopGui.getVariables(), hopGui.getMetadataProvider());
    maybeWriteReport(options, simulation, hopGui.getVariables());
    ValidationReport report = simulation.validationReport();
    if (shell != null && !shell.isDisposed()) {
      ResourceDefinitionValidationResultsDialog dialog =
          new ResourceDefinitionValidationResultsDialog(
              shell, hopGui, group, report, simulation, options);
      dialog.open();
    }
    return report;
  }

  public static ValidationReport validateAndShowResults(
      Shell shell, IVariables variables, IHopMetadataProvider metadataProvider, String groupName) {
    try {
      SchemaImpactSimulationRequest request =
          SchemaImpactSimulationRequest.builder()
              .resourceDefinitionGroup(groupName)
              .compareMode(SchemaCompareMode.LIVE_SOURCE)
              .includeImpact(true)
              .build();
      SchemaImpactSimulationResult simulation =
          SchemaImpactSimulationService.run(request, variables, metadataProvider);
      ValidationReport report = simulation.validationReport();
      if (shell != null && !shell.isDisposed()) {
        ResourceDefinitionValidationResultsDialog dialog =
            new ResourceDefinitionValidationResultsDialog(
                shell, variables, metadataProvider, report, simulation);
        dialog.open();
      }
      return report;
    } catch (Exception e) {
      if (shell != null && !shell.isDisposed()) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Title"),
            BaseMessages.getString(PKG, "ResourceDefinitionValidationGuiSupport.Error.Message"),
            e instanceof HopException ? e : new HopException(e));
      }
      return null;
    }
  }

  public static SchemaImpactSimulationResult runLiveSimulation(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return runSimulation(group, ValidationOptions.defaults(), variables, metadataProvider);
  }

  public static SchemaImpactSimulationResult runSimulation(
      ResourceDefinitionGroupMeta group,
      ValidationOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    ValidationOptions effective = options != null ? options : ValidationOptions.defaults();
    SchemaImpactSimulationRequest request =
        effective.toSimulationRequest(
            group != null ? group.getName() : null,
            group == null || group.isDetailedDataTypeChecking());
    return SchemaImpactSimulationService.run(request, group, variables, metadataProvider);
  }

  private static void maybeWriteReport(
      ValidationOptions options, SchemaImpactSimulationResult simulation, IVariables variables)
      throws HopException {
    if (options == null || !options.writeReport() || Utils.isEmpty(options.reportOutputPath())) {
      return;
    }
    List<String> written =
        SchemaValidationReportFileWriter.write(
            options.reportOutputPath(),
            options.reportFileBaseName(),
            simulation,
            options.reportFormat(),
            variables);
    // Paths are available to the results dialog via the simulation/report; no UI toast required.
    if (written != null) {
      written.size(); // touch for linters; silent success
    }
  }
}
