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
package org.hopper.edw.datavault.hopgui.file.executionmap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.hopper.edw.catalog.hopgui.perspective.DataCatalogPerspective;
import org.hopper.edw.catalog.hopgui.preview.RecordDefinitionPreviewRunner;
import org.hopper.edw.catalog.hopgui.preview.RecordDefinitionPreviewSupport;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionQuery;
import org.hopper.edw.catalog.model.RecordDefinitionRef;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.executionmap.ArtifactSnapshotSupport;
import org.hopper.edw.datavault.executionmap.DatasetNodeSupport;
import org.hopper.edw.datavault.executionmap.ExecutionMapDatasetCatalogSupport;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapArtifactSnapshot;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapArtifactType;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.workflow.WorkflowMeta;
import org.eclipse.swt.widgets.Shell;
import org.w3c.dom.Node;

/** Opens execution map nodes in Hop GUI or from embedded XML snapshots. */
public final class ExecutionMapNavigationSupport {

  private static final Class<?> PKG = HopGuiExecutionMapGraph.class;

  private ExecutionMapNavigationSupport() {}

  public static boolean isSyntheticPath(String path) {
    if (Utils.isEmpty(path)) {
      return false;
    }
    String lower = path.toLowerCase();
    return lower.startsWith("generated://")
        || lower.startsWith("synthetic://")
        || lower.startsWith("dataset://")
        || lower.startsWith("generated:")
        || lower.startsWith("synthetic:")
        || lower.startsWith("dataset:");
  }

  public static String resolvePath(IVariables variables, String path) {
    if (Utils.isEmpty(path)) {
      return path;
    }
    return variables != null ? variables.resolve(path) : path;
  }

  public static ExecutionMapArtifactSnapshot findSnapshot(
      ExecutionMapNode node, ExecutionMapDocument document) {
    if (node == null || document == null || Utils.isEmpty(node.getSnapshotId())) {
      return null;
    }
    return document.findSnapshotById(node.getSnapshotId());
  }

  public static boolean canOpenArtifactFile(ExecutionMapNode node, IVariables variables) {
    if (!canOpenArtifactFile(node)) {
      return false;
    }
    String resolved = resolvePath(variables, node.getPath());
    return Files.isRegularFile(Path.of(resolved));
  }

  public static boolean canOpenArtifactFile(ExecutionMapNode node) {
    return node != null && !Utils.isEmpty(node.getPath()) && !isSyntheticPath(node.getPath());
  }

  public static boolean canOpenFromSnapshot(ExecutionMapNode node, ExecutionMapDocument document) {
    ExecutionMapArtifactSnapshot snapshot = findSnapshot(node, document);
    return snapshot != null && !Utils.isEmpty(snapshot.getXmlGzipBase64());
  }

  public static boolean canNavigateToCatalog(
      ExecutionMapNode node,
      ExecutionMapDocument document,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (!ExecutionMapDatasetDetailsViewer.isDatasetNode(node)) {
      return false;
    }
    try {
      RecordDefinitionKey key =
          ExecutionMapDatasetCatalogSupport.resolveDatasetRecordKey(node, document, variables);
      if (key == null) {
        return false;
      }
      resolveCatalogConnectionName(node, key, variables, metadataProvider);
      return true;
    } catch (HopException e) {
      return false;
    }
  }

  public static boolean canNavigateToCatalog(
      ExecutionMapNode node, IVariables variables, IHopMetadataProvider metadataProvider) {
    return canNavigateToCatalog(node, null, variables, metadataProvider);
  }

  public static boolean canNavigate(
      ExecutionMapNode node,
      ExecutionMapDocument document,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    return canOpenArtifactFile(node)
        || canOpenFromSnapshot(node, document)
        || canNavigateToCatalog(node, document, variables, metadataProvider);
  }

  public static boolean canNavigate(ExecutionMapNode node, ExecutionMapDocument document) {
    return canOpenArtifactFile(node) || canOpenFromSnapshot(node, document);
  }

  public static RecordDefinitionKey resolveDatasetRecordKey(ExecutionMapNode node) {
    return ExecutionMapDatasetCatalogSupport.resolveDatasetRecordKey(node);
  }

  public static void openNode(
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ExecutionMapDocument document,
      ExecutionMapNode node) {
    if (hopGui == null || node == null) {
      return;
    }
    Shell shell = hopGui.getShell();
    try {
      if (canOpenArtifactFile(node, variables)) {
        openArtifactFile(hopGui, shell, variables, node.getPath());
        return;
      }
      if (canOpenFromSnapshot(node, document)) {
        openFromSnapshot(hopGui, variables, metadataProvider, findSnapshot(node, document), node);
        return;
      }
      if (canNavigateToCatalog(node, document, variables, metadataProvider)) {
        openDatasetInCatalog(hopGui, variables, metadataProvider, document, node);
        return;
      }
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ExecutionMapNavigationSupport.Error.OpenTitle"),
          BaseMessages.getString(
              PKG, "ExecutionMapNavigationSupport.Error.OpenMessage", describeNode(node)),
          null);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "ExecutionMapNavigationSupport.Error.OpenTitle"),
          BaseMessages.getString(
              PKG, "ExecutionMapNavigationSupport.Error.OpenMessage", describeNode(node)),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  public static void openNode(
      HopGui hopGui, IVariables variables, ExecutionMapDocument document, ExecutionMapNode node) {
    openNode(
        hopGui, variables, hopGui != null ? hopGui.getMetadataProvider() : null, document, node);
  }

  public static void openDatasetInCatalog(
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ExecutionMapDocument document,
      ExecutionMapNode node)
      throws HopException {
    if (hopGui == null || node == null) {
      return;
    }
    RecordDefinitionKey key =
        ExecutionMapDatasetCatalogSupport.resolveDatasetRecordKey(node, document, variables);
    if (key == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "ExecutionMapNavigationSupport.Error.MissingDatasetKey"));
    }
    String catalogConnectionName =
        resolveCatalogConnectionName(node, key, variables, metadataProvider);
    DataCatalogPerspective perspective = DataCatalogPerspective.getInstance();
    if (perspective == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "ExecutionMapNavigationSupport.Error.CatalogUnavailable"));
    }
    perspective.selectRecordDefinition(catalogConnectionName, key);
  }

  public static void openDatasetInCatalog(
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ExecutionMapNode node)
      throws HopException {
    openDatasetInCatalog(hopGui, variables, metadataProvider, null, node);
  }

  public static boolean canPreviewDataset(
      ExecutionMapNode node,
      ExecutionMapDocument document,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (!ExecutionMapDatasetDetailsViewer.isDatasetNode(node)) {
      return false;
    }
    try {
      RecordDefinition definition =
          resolveDatasetRecordDefinition(node, document, variables, metadataProvider);
      return definition != null && RecordDefinitionPreviewSupport.supportsPreview(definition);
    } catch (HopException e) {
      return false;
    }
  }

  public static void previewDataset(
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ExecutionMapDocument document,
      ExecutionMapNode node)
      throws HopException {
    if (hopGui == null || node == null) {
      return;
    }
    RecordDefinition definition =
        resolveDatasetRecordDefinition(node, document, variables, metadataProvider);
    if (definition == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "ExecutionMapNavigationSupport.Error.PreviewNoDefinition"));
    }
    if (!RecordDefinitionPreviewSupport.supportsPreview(definition)) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "ExecutionMapNavigationSupport.Error.PreviewUnsupported",
              definition.getKey() != null ? definition.getKey().toString() : node.getName()));
    }
    RecordDefinitionPreviewRunner.run(hopGui.getShell(), definition, variables, metadataProvider);
  }

  public static RecordDefinition resolveDatasetRecordDefinition(
      ExecutionMapNode node,
      ExecutionMapDocument document,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (node == null || metadataProvider == null) {
      return null;
    }
    RecordDefinitionKey key =
        ExecutionMapDatasetCatalogSupport.resolveDatasetRecordKey(node, document, variables);
    if (key == null) {
      return null;
    }
    String catalogConnectionName =
        resolveCatalogConnectionName(node, key, variables, metadataProvider);
    return RecordDefinitionRegistry.getInstance()
        .read(catalogConnectionName, key, variables, metadataProvider);
  }

  public static void openArtifactFile(HopGui hopGui, Shell shell, IVariables variables, String path)
      throws HopException {
    if (hopGui == null || Utils.isEmpty(path)) {
      return;
    }
    String resolved = resolvePath(variables, path);
    if (Utils.isEmpty(resolved)) {
      throw new HopException("Artifact path is empty");
    }
    if (!Files.isRegularFile(Path.of(resolved))) {
      throw new HopException("Artifact file not found: " + resolved);
    }
    try {
      ExplorerPerspective perspective = HopGui.getExplorerPerspective();
      IHopFileTypeHandler existing = perspective.findFileTypeHandlerByFilename(resolved);
      if (existing != null) {
        perspective.setActiveFileTypeHandler(existing);
      } else {
        hopGui.fileDelegate.fileOpen(resolved);
      }
    } catch (Exception e) {
      throw new HopException("Unable to open artifact file: " + resolved, e);
    }
  }

  public static void openFromSnapshot(
      HopGui hopGui,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ExecutionMapArtifactSnapshot snapshot,
      ExecutionMapNode node)
      throws HopException {
    if (hopGui == null || snapshot == null) {
      return;
    }
    String xml = ArtifactSnapshotSupport.decodeXml(snapshot.getXmlGzipBase64());
    if (Utils.isEmpty(xml)) {
      throw new HopException("Snapshot XML is empty");
    }
    ExecutionMapArtifactType artifactType = snapshot.getArtifactType();
    if (artifactType == null) {
      artifactType = inferArtifactType(node);
    }
    ExplorerPerspective perspective = HopGui.getExplorerPerspective();
    if (artifactType == ExecutionMapArtifactType.WORKFLOW || isWorkflowNode(node)) {
      Node workflowNode = XmlHandler.loadXmlString(xml, WorkflowMeta.XML_TAG);
      WorkflowMeta workflowMeta = new WorkflowMeta(workflowNode, metadataProvider, variables);
      if (!Utils.isEmpty(snapshot.getSourcePath())) {
        workflowMeta.setFilename(resolvePath(variables, snapshot.getSourcePath()));
      } else if (node != null && !Utils.isEmpty(node.getPath())) {
        workflowMeta.setFilename(resolvePath(variables, node.getPath()));
      }
      workflowMeta.setName(
          !Utils.isEmpty(node != null ? node.getName() : null)
              ? node.getName()
              : workflowMeta.getName());
      perspective.addWorkflow(workflowMeta);
      return;
    }
    Node pipelineNode = XmlHandler.loadXmlString(xml, PipelineMeta.XML_TAG);
    PipelineMeta pipelineMeta = new PipelineMeta(pipelineNode, metadataProvider);
    if (!Utils.isEmpty(snapshot.getSourcePath())) {
      pipelineMeta.setFilename(resolvePath(variables, snapshot.getSourcePath()));
    } else if (node != null && !Utils.isEmpty(node.getPath())) {
      pipelineMeta.setFilename(resolvePath(variables, node.getPath()));
    }
    pipelineMeta.setName(
        !Utils.isEmpty(node != null ? node.getName() : null)
            ? node.getName()
            : pipelineMeta.getName());
    perspective.addPipeline(pipelineMeta);
  }

  public static String describeNode(ExecutionMapNode node) {
    if (node == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    if (!Utils.isEmpty(node.getName())) {
      builder.append(node.getName());
    }
    if (node.getNodeType() != null) {
      if (!builder.isEmpty()) {
        builder.append(" (");
        builder.append(node.getNodeType().name());
        builder.append(")");
      } else {
        builder.append(node.getNodeType().name());
      }
    }
    if (!Utils.isEmpty(node.getPath())) {
      if (!builder.isEmpty()) {
        builder.append(System.lineSeparator());
      }
      builder.append(node.getPath());
    }
    return builder.toString();
  }

  public static String buildTooltip(
      ExecutionMapNode node,
      ExecutionMapDocument document,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (node == null) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    if (ExecutionMapDatasetDetailsViewer.isDatasetNode(node)) {
      builder.append(ExecutionMapDatasetDetailsViewer.describeDataset(node));
    } else {
      builder.append(describeNode(node));
    }
    if (canOpenFromSnapshot(node, document)) {
      if (!builder.isEmpty()) {
        builder.append(System.lineSeparator());
      }
      builder.append(BaseMessages.getString(PKG, "ExecutionMapNavigationSupport.Tooltip.Snapshot"));
    }
    if (canNavigateToCatalog(node, document, variables, metadataProvider)) {
      if (!builder.isEmpty()) {
        builder.append(System.lineSeparator());
      }
      builder.append(
          BaseMessages.getString(PKG, "ExecutionMapNavigationSupport.Tooltip.OpenInCatalog"));
    }
    if (canPreviewDataset(node, document, variables, metadataProvider)) {
      if (!builder.isEmpty()) {
        builder.append(System.lineSeparator());
      }
      builder.append(
          BaseMessages.getString(PKG, "ExecutionMapNavigationSupport.Tooltip.PreviewDataset"));
    }
    if (!canNavigateToCatalog(node, document, variables, metadataProvider)
        && (canOpenArtifactFile(node) || canOpenFromSnapshot(node, document))) {
      if (!builder.isEmpty()) {
        builder.append(System.lineSeparator());
      }
      builder.append(
          BaseMessages.getString(PKG, "ExecutionMapNavigationSupport.Tooltip.DoubleClick"));
    }
    return builder.isEmpty() ? null : builder.toString();
  }

  public static String buildTooltip(ExecutionMapNode node, ExecutionMapDocument document) {
    return buildTooltip(node, document, null, null);
  }

  private static String resolveCatalogConnectionName(
      ExecutionMapNode node,
      RecordDefinitionKey key,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String fromProperty =
        resolvePath(variables, node.getProperty(DatasetNodeSupport.PROPERTY_CATALOG_CONNECTION));
    if (!Utils.isEmpty(fromProperty)) {
      if (metadataProvider == null
          || catalogContainsRecord(fromProperty, key, variables, metadataProvider)) {
        return fromProperty;
      }
    }
    List<String> matches = findCatalogMatches(key, variables, metadataProvider);
    if (matches.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "ExecutionMapNavigationSupport.Error.RecordNotFound", key.toString()));
    }
    if (matches.size() > 1) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "ExecutionMapNavigationSupport.Error.AmbiguousCatalog",
              key.toString(),
              String.join(", ", matches)));
    }
    return matches.get(0);
  }

  private static boolean catalogContainsRecord(
      String catalogConnectionName,
      RecordDefinitionKey key,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (Utils.isEmpty(catalogConnectionName) || key == null || metadataProvider == null) {
      return false;
    }
    try {
      return RecordDefinitionRegistry.getInstance()
              .read(catalogConnectionName, key, variables, metadataProvider)
          != null;
    } catch (HopException e) {
      return false;
    }
  }

  private static List<String> findCatalogMatches(
      RecordDefinitionKey key, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    List<String> matches = new ArrayList<>();
    if (key == null || metadataProvider == null) {
      return matches;
    }
    List<RecordDefinitionRef> refs =
        RecordDefinitionRegistry.getInstance()
            .listAll(new RecordDefinitionQuery(), variables, metadataProvider);
    for (RecordDefinitionRef ref : refs) {
      if (ref.getKey() != null && key.equals(ref.getKey())) {
        matches.add(ref.getCatalogConnectionName());
      }
    }
    return matches;
  }

  private static ExecutionMapArtifactType inferArtifactType(ExecutionMapNode node) {
    if (node == null || node.getNodeType() == null) {
      return ExecutionMapArtifactType.PIPELINE;
    }
    return switch (node.getNodeType()) {
      case ROOT_WORKFLOW, WORKFLOW, WORKFLOW_EXECUTOR, BULK_MASTER_WORKFLOW ->
          ExecutionMapArtifactType.WORKFLOW;
      case GENERATED_PIPELINE, ORCHESTRATOR_PIPELINE -> ExecutionMapArtifactType.GENERATED_PIPELINE;
      default -> ExecutionMapArtifactType.PIPELINE;
    };
  }

  private static boolean isWorkflowNode(ExecutionMapNode node) {
    if (node == null || node.getNodeType() == null) {
      return false;
    }
    return switch (node.getNodeType()) {
      case ROOT_WORKFLOW, WORKFLOW, WORKFLOW_EXECUTOR, BULK_MASTER_WORKFLOW -> true;
      default -> false;
    };
  }
}
