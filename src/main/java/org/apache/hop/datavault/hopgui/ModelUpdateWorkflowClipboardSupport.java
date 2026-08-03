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
package org.apache.hop.datavault.hopgui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.config.DvRunConfigurationSupport;
import org.apache.hop.datavault.workflow.actions.businessvaultupdate.ActionBusinessVaultUpdate;
import org.apache.hop.datavault.workflow.actions.datavaultupdate.ActionDataVaultUpdate;
import org.apache.hop.datavault.workflow.actions.dimensionalupdate.ActionDimensionalUpdate;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.eclipse.swt.widgets.Shell;

/** Copies a model-update workflow action to the Hop workflow clipboard format. */
public final class ModelUpdateWorkflowClipboardSupport {

  private static final Class<?> PKG = ModelUpdateWorkflowClipboardSupport.class;

  /** Clipboard root tag used by {@code HopGuiWorkflowClipboardDelegate}. */
  public static final String XML_TAG_WORKFLOW_ACTIONS = "workflow-actions";

  private ModelUpdateWorkflowClipboardSupport() {}

  public static void copyUpdateWorkflowToClipboard(
      HopGui hopGui, IAction updateAction, IVariables variables, String modelFilename)
      throws HopException {
    String pipelineRunConfiguration =
        resolvePipelineRunConfigurationForClipboard(hopGui, updateAction);
    if (pipelineRunConfiguration == null && requiresPipelineRunConfigurationChoice(hopGui)) {
      return;
    }

    IAction clonedAction = (IAction) updateAction.clone();
    if (!Utils.isEmpty(pipelineRunConfiguration)) {
      applyPipelineRunConfiguration(clonedAction, pipelineRunConfiguration);
    }

    ActionMeta updateMeta = new ActionMeta(clonedAction);
    updateMeta.setLocation(50, 50);
    String actionName = resolveActionNameFromModelFilename(variables, modelFilename);
    if (!Utils.isEmpty(actionName)) {
      updateMeta.setName(actionName);
    }

    StringBuilder xml = new StringBuilder(5000).append(XmlHandler.getXmlHeader());
    xml.append(XmlHandler.openTag(XML_TAG_WORKFLOW_ACTIONS)).append(Const.CR);
    xml.append(XmlHandler.openTag(WorkflowMeta.XML_TAG_ACTIONS)).append(Const.CR);
    xml.append(updateMeta.getXml());
    xml.append(XmlHandler.closeTag(WorkflowMeta.XML_TAG_ACTIONS)).append(Const.CR);
    xml.append(XmlHandler.openTag(WorkflowMeta.XML_TAG_HOPS)).append(Const.CR);
    xml.append(XmlHandler.closeTag(WorkflowMeta.XML_TAG_HOPS)).append(Const.CR);
    xml.append(XmlHandler.openTag(WorkflowMeta.XML_TAG_NOTEPADS)).append(Const.CR);
    xml.append(XmlHandler.closeTag(WorkflowMeta.XML_TAG_NOTEPADS)).append(Const.CR);
    xml.append(XmlHandler.closeTag(XML_TAG_WORKFLOW_ACTIONS)).append(Const.CR);

    try {
      GuiResource.getInstance().toClipboard(xml.toString());
    } catch (Throwable e) {
      throw new HopException(
          BaseMessages.getString(HopGui.class, "HopGui.Dialog.ExceptionCopyToClipboard.Message"),
          e);
    }
  }

  static String resolvePipelineRunConfigurationForClipboard(HopGui hopGui, IAction updateAction)
      throws HopException {
    List<String> runConfigurations =
        listPipelineRunConfigurationNames(hopGui.getMetadataProvider());
    if (runConfigurations.isEmpty()) {
      return "";
    }
    if (runConfigurations.size() == 1) {
      return runConfigurations.getFirst();
    }

    Shell shell = hopGui != null ? hopGui.getShell() : null;
    if (shell == null || shell.isDisposed()) {
      throw new HopException(
          "Unable to prompt for a pipeline run configuration because the Hop GUI shell is not available");
    }

    String[] choices = runConfigurations.toArray(new String[0]);
    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            shell,
            choices,
            BaseMessages.getString(
                PKG, "ModelUpdateWorkflowClipboardSupport.PipelineRunConfiguration.Dialog.Title"),
            BaseMessages.getString(
                PKG,
                "ModelUpdateWorkflowClipboardSupport.PipelineRunConfiguration.Dialog.Message"));

    String current = readPipelineRunConfiguration(updateAction);
    if (Utils.isEmpty(current)) {
      current =
          DvRunConfigurationSupport.resolvePipelineRunConfiguration(null, hopGui.getVariables());
    }
    if (!Utils.isEmpty(current)) {
      int index = dialog.getSelectionNr(current);
      if (index >= 0) {
        dialog.setSelectedNrs(new int[] {index});
      }
    }

    String selected = dialog.open();
    return selected != null ? selected : null;
  }

  static boolean requiresPipelineRunConfigurationChoice(HopGui hopGui) throws HopException {
    return listPipelineRunConfigurationNames(hopGui.getMetadataProvider()).size() > 1;
  }

  static List<String> listPipelineRunConfigurationNames(IHopMetadataProvider metadataProvider)
      throws HopException {
    if (metadataProvider == null) {
      return List.of();
    }
    List<String> names =
        metadataProvider.getSerializer(PipelineRunConfiguration.class).listObjectNames();
    if (names == null || names.isEmpty()) {
      return List.of();
    }
    List<String> filtered = new ArrayList<>();
    for (String name : names) {
      if (!Utils.isEmpty(name)) {
        filtered.add(name);
      }
    }
    Collections.sort(filtered);
    return filtered;
  }

  static void applyPipelineRunConfiguration(IAction action, String pipelineRunConfiguration) {
    if (action == null || Utils.isEmpty(pipelineRunConfiguration)) {
      return;
    }
    if (action instanceof ActionDimensionalUpdate dimensionalUpdate) {
      dimensionalUpdate.setPipelineRunConfiguration(pipelineRunConfiguration);
    } else if (action instanceof ActionDataVaultUpdate dataVaultUpdate) {
      dataVaultUpdate.setPipelineRunConfiguration(pipelineRunConfiguration);
    } else if (action instanceof ActionBusinessVaultUpdate businessVaultUpdate) {
      businessVaultUpdate.setPipelineRunConfiguration(pipelineRunConfiguration);
    }
  }

  static String readPipelineRunConfiguration(IAction action) {
    if (action instanceof ActionDimensionalUpdate dimensionalUpdate) {
      return dimensionalUpdate.getPipelineRunConfiguration();
    }
    if (action instanceof ActionDataVaultUpdate dataVaultUpdate) {
      return dataVaultUpdate.getPipelineRunConfiguration();
    }
    if (action instanceof ActionBusinessVaultUpdate businessVaultUpdate) {
      return businessVaultUpdate.getPipelineRunConfiguration();
    }
    return null;
  }

  static String resolveActionNameFromModelFilename(IVariables variables, String modelFilename) {
    if (Utils.isEmpty(modelFilename)) {
      return null;
    }
    String resolved = variables != null ? variables.resolve(modelFilename) : modelFilename;
    if (Utils.isEmpty(resolved)) {
      return null;
    }
    String baseName = resolved.trim();
    if (Utils.isEmpty(baseName)) {
      return null;
    }
    int lastSlash = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
    if (lastSlash >= 0 && lastSlash < baseName.length() - 1) {
      baseName = baseName.substring(lastSlash + 1);
    }
    int lastDot = baseName.lastIndexOf('.');
    if (lastDot > 0) {
      baseName = baseName.substring(0, lastDot);
    }
    return Utils.isEmpty(baseName) ? null : baseName;
  }
}
