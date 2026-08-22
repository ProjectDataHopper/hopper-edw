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
package org.apache.hop.datavault.hopgui;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.workflow.WorkflowMeta;
import org.w3c.dom.Node;

/** Opens model-generated pipelines and workflows in the Hop Explorer without marking them dirty. */
public final class ModelGeneratedArtifactOpenSupport {

  private ModelGeneratedArtifactOpenSupport() {}

  /**
   * Serializes the pipeline to XML and reloads it via the Hop plugin registry before opening in the
   * Explorer. This ensures transforms are loaded with the correct classloaders (for example when
   * opening the Table Input transform dialog) and clears the changed flag so the tab is not marked
   * dirty.
   */
  public static void openGeneratedPipeline(
      HopGui hopGui, PipelineMeta pipelineMeta, IVariables variables) throws HopException {
    if (hopGui == null || pipelineMeta == null) {
      return;
    }
    PipelineMeta reloaded =
        reloadPipelineForGuiOpen(pipelineMeta, hopGui.getMetadataProvider(), variables);
    reloaded.clearChanged();
    HopGui.getExplorerPerspective().addPipeline(reloaded);
  }

  public static PipelineMeta reloadPipelineForGuiOpen(
      PipelineMeta pipelineMeta, IHopMetadataProvider metadataProvider, IVariables variables)
      throws HopException {
    String xml = pipelineMeta.getXml(variables);
    Node pipelineNode = XmlHandler.loadXmlString(xml, PipelineMeta.XML_TAG);
    return new PipelineMeta(pipelineNode, metadataProvider);
  }

  public static IHopFileTypeHandler openGeneratedWorkflow(WorkflowMeta workflowMeta)
      throws HopException {
    if (workflowMeta == null) {
      return null;
    }
    workflowMeta.clearChanged();
    return HopGui.getExplorerPerspective().addWorkflow(workflowMeta);
  }
}
