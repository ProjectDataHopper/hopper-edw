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
package org.hopper.edw.datavault.architecture;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.datavault.executionmap.CrawlOptions;
import org.hopper.edw.datavault.executionmap.ExecutionMapCrawler;
import org.hopper.edw.datavault.executionmap.ExecutionMapPersistence;
import org.hopper.edw.datavault.lineage.BvModelLineageCollector;
import org.hopper.edw.datavault.lineage.DmModelLineageCollector;
import org.hopper.edw.datavault.lineage.DvModelLineageCollector;
import org.hopper.edw.datavault.lineage.LineageSnapshot;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvModelLoadSupport;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.hopper.edw.datavault.resourcedefinition.ResourceDefinitionGroupResolver;

/** Orchestrates architecture graph build and Draw.io export. */
public final class ArchitectureExportService {

  private ArchitectureExportService() {}

  @Getter
  public static final class ExportResult {
    private final ArchitectureGraph graph;
    private final String outputPath;
    private final List<String> warnings;
    private final ExecutionMapDocument executionMap;

    public ExportResult(ArchitectureGraph graph, String outputPath, List<String> warnings) {
      this(graph, outputPath, warnings, null);
    }

    public ExportResult(
        ArchitectureGraph graph,
        String outputPath,
        List<String> warnings,
        ExecutionMapDocument executionMap) {
      this.graph = graph;
      this.outputPath = outputPath;
      this.warnings = warnings != null ? warnings : List.of();
      this.executionMap = executionMap;
    }
  }

  /**
   * Export SOLUTION architecture from a workflow/pipeline root (crawl) or an existing {@code .hem}
   * file.
   */
  public static ExportResult exportSolutionDrawio(
      String rootOrHemPath,
      String outputDrawioPath,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean crawlIfNotHem)
      throws HopException {
    List<String> warnings = new ArrayList<>();
    ExecutionMapDocument map =
        loadOrCrawlExecutionMap(
            rootOrHemPath, variables, metadataProvider, crawlIfNotHem, warnings);

    ArchitectureGraph graph = ArchitectureGraphFromExecutionMap.build(map);
    ArchitecturePathSupport.portableizeGraph(graph, variables);
    String out =
        resolveProjectRelativePath(
            resolveOutputPath(outputDrawioPath, graph.getName() + "-solution.drawio", variables),
            variables,
            true);
    writeDrawio(graph, out);
    return new ExportResult(graph, out, warnings, map);
  }

  public static ExecutionMapDocument loadOrCrawlExecutionMap(
      String rootOrHemPath,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean crawlIfNotHem,
      List<String> warnings)
      throws HopException {
    String resolvedRoot = resolveProjectRelativePath(rootOrHemPath, variables, false);
    if (resolvedRoot != null && resolvedRoot.toLowerCase().endsWith(".hem")) {
      return ExecutionMapPersistence.load(resolvedRoot, metadataProvider, variables);
    }
    if (!crawlIfNotHem) {
      throw new HopException(
          "Root path must be a .hem file or crawl must be enabled: " + rootOrHemPath);
    }
    // Architecture overview does not need table/dataset grain (that is inventory / MODEL export).
    CrawlOptions options =
        CrawlOptions.builder()
            .includeGeneratedPipelines(false)
            .includeDatasetNodes(false)
            .includeWorkflowActions(true)
            .includePipelineTransforms(false)
            .followNestedWorkflows(true)
            .followNestedPipelines(true)
            .captureSnapshots(false)
            .build();
    ExecutionMapCrawler.CrawlResult crawl =
        ExecutionMapCrawler.crawl(resolvedRoot, variables, metadataProvider, options);
    if (warnings != null && crawl.getWarnings() != null) {
      warnings.addAll(crawl.getWarnings());
    }
    return crawl.getDocument();
  }

  /**
   * Export DATA <em>inventory</em> (tables/sources, no relationship edges) from model lineage. For
   * relational diagrams with ELK layout use {@link #exportLayerModelDrawios}.
   */
  public static ExportResult exportDataDrawio(
      List<String> modelPaths,
      String outputDrawioPath,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<String> warnings = new ArrayList<>();
    List<LineageSnapshot> snapshots = new ArrayList<>();
    if (modelPaths != null) {
      for (String path : modelPaths) {
        if (Utils.isEmpty(path)) {
          continue;
        }
        String resolved = resolveProjectRelativePath(path, variables, false);
        try {
          snapshots.add(collectLineage(resolved, variables, metadataProvider));
        } catch (HopException e) {
          warnings.add("Skip model " + resolved + ": " + e.getMessage());
        }
      }
    }
    ArchitectureGraph graph = ArchitectureGraphFromLineage.build(snapshots);
    ArchitecturePathSupport.portableizeGraph(graph, variables);
    // Short stable default name (not model1+model2+…-data-inventory.drawio)
    String out =
        resolveProjectRelativePath(
            resolveOutputPath(outputDrawioPath, "data-inventory.drawio", variables),
            variables,
            true);
    writeDrawio(graph, out);
    return new ExportResult(graph, out, warnings);
  }

  /**
   * Export <b>aggregated</b> layer model diagrams (ELK layout) for the given model paths:
   *
   * <ul>
   *   <li>{@code data-vault.drawio} — union of all {@code .hdv} files
   *   <li>{@code business-vault.drawio} — union of all {@code .hbv} files (+ DV derivatives)
   *   <li>{@code dimensional.drawio} — union of all {@code .hdm} files
   * </ul>
   *
   * Tables shared across files (e.g. conformed hubs/dims) are deduped by name. Does not mutate
   * model coordinates on disk.
   */
  public static List<ExportResult> exportLayerModelDrawios(
      List<String> modelPaths,
      String outputDirectory,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<ExportResult> results = new ArrayList<>();
    if (modelPaths == null || modelPaths.isEmpty()) {
      return results;
    }
    String dir =
        resolveProjectRelativePath(
            !Utils.isEmpty(outputDirectory)
                ? outputDirectory
                : "${PROJECT_HOME}/work/architecture/models",
            variables,
            true);
    String base = dir.replaceAll("/+$", "");

    List<DataVaultModel> dvModels = new ArrayList<>();
    List<BusinessVaultModel> bvModels = new ArrayList<>();
    List<DataVaultModel> bvEffectiveDv = new ArrayList<>();
    List<DimensionalModel> dmModels = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (String path : modelPaths) {
      if (Utils.isEmpty(path)) {
        continue;
      }
      try {
        String resolved = resolveProjectRelativePath(path, variables, false);
        String lower = resolved.toLowerCase();
        if (lower.endsWith(".hdv")) {
          dvModels.add(
              DvModelLoadSupport.loadDataVaultModel(resolved, null, variables, metadataProvider));
        } else if (lower.endsWith(".hbv")) {
          BusinessVaultModel bv =
              ResourceDefinitionGroupResolver.loadBusinessVaultModel(
                  resolved, variables, metadataProvider);
          bvModels.add(bv);
          try {
            DataVaultModel effective =
                org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDvModelResolver
                    .buildEffectiveDataVaultModel(bv, variables, metadataProvider);
            if (effective != null) {
              bvEffectiveDv.add(effective);
            }
          } catch (Exception e) {
            warnings.add("BV effective DV for " + resolved + ": " + e.getMessage());
          }
        } else if (lower.endsWith(".hdm")) {
          dmModels.add(
              ResourceDefinitionGroupResolver.loadDimensionalModel(
                  resolved, variables, metadataProvider));
        } else {
          warnings.add("Skip unsupported model path for layer export: " + resolved);
        }
      } catch (HopException e) {
        warnings.add("Skip model " + path + ": " + e.getMessage());
      }
    }

    // Include standalone DV models as effective DV context for BV derivatives when BV present
    if (!bvModels.isEmpty() && !dvModels.isEmpty()) {
      bvEffectiveDv.addAll(dvModels);
    }

    if (!dvModels.isEmpty()) {
      ArchitectureGraph graph = ArchitectureGraphFromModel.fromDataVaultModels(dvModels, variables);
      String out = base + "/data-vault.drawio";
      writeDrawio(graph, out);
      List<String> w = new ArrayList<>(warnings);
      results.add(new ExportResult(graph, out, w));
    }
    if (!bvModels.isEmpty()) {
      ArchitectureGraph graph =
          ArchitectureGraphFromModel.fromBusinessVaultModels(bvModels, bvEffectiveDv, variables);
      String out = base + "/business-vault.drawio";
      writeDrawio(graph, out);
      results.add(new ExportResult(graph, out, List.copyOf(warnings)));
    }
    if (!dmModels.isEmpty()) {
      ArchitectureGraph graph =
          ArchitectureGraphFromModel.fromDimensionalModels(dmModels, variables);
      String out = base + "/dimensional.drawio";
      writeDrawio(graph, out);
      results.add(new ExportResult(graph, out, List.copyOf(warnings)));
    }
    if (results.isEmpty() && !warnings.isEmpty()) {
      ArchitectureGraph empty = new ArchitectureGraph();
      empty.setName("empty");
      results.add(new ExportResult(empty, base, warnings));
    }
    return results;
  }

  /**
   * @deprecated use {@link #exportLayerModelDrawios} — keeps CLI/action wiring stable.
   */
  public static List<ExportResult> exportModelsDrawio(
      List<String> modelPaths,
      String outputDirectory,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return exportLayerModelDrawios(modelPaths, outputDirectory, variables, metadataProvider);
  }

  /**
   * Export <b>one Draw.io per model file</b> under type subfolders:
   *
   * <ul>
   *   <li>{@code data-vault/{basename}.drawio} — each {@code .hdv}
   *   <li>{@code business-vault/{basename}.drawio} — each {@code .hbv}
   *   <li>{@code dimensional/{basename}.drawio} — each {@code .hdm}
   * </ul>
   *
   * Basename is the model file name without extension. Does not mutate model coordinates on disk.
   */
  public static List<ExportResult> exportPerModelDrawios(
      List<String> modelPaths,
      String outputDirectory,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<ExportResult> results = new ArrayList<>();
    if (modelPaths == null || modelPaths.isEmpty()) {
      return results;
    }
    String dir =
        resolveProjectRelativePath(
            !Utils.isEmpty(outputDirectory)
                ? outputDirectory
                : "${PROJECT_HOME}/work/architecture/models",
            variables,
            true);
    String base = dir.replaceAll("/+$", "");
    List<String> skipWarnings = new ArrayList<>();

    for (String path : modelPaths) {
      if (Utils.isEmpty(path)) {
        continue;
      }
      List<String> modelWarnings = new ArrayList<>();
      try {
        String resolved = resolveProjectRelativePath(path, variables, false);
        String lower = resolved.toLowerCase();
        String basename = modelBasename(resolved);
        if (Utils.isEmpty(basename)) {
          skipWarnings.add("Skip model with empty basename: " + resolved);
          continue;
        }
        String out = perModelDrawioPath(base, resolved);
        if (out == null) {
          skipWarnings.add("Skip unsupported model path for per-model export: " + resolved);
          continue;
        }
        if (lower.endsWith(".hdv")) {
          DataVaultModel model =
              DvModelLoadSupport.loadDataVaultModel(resolved, null, variables, metadataProvider);
          ArchitectureGraph graph = ArchitectureGraphFromModel.fromDataVault(model, variables);
          writeDrawio(graph, out);
          results.add(new ExportResult(graph, out, modelWarnings));
        } else if (lower.endsWith(".hbv")) {
          BusinessVaultModel bv =
              ResourceDefinitionGroupResolver.loadBusinessVaultModel(
                  resolved, variables, metadataProvider);
          DataVaultModel effective = null;
          try {
            effective =
                BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
                    bv, variables, metadataProvider);
          } catch (Exception e) {
            modelWarnings.add("BV effective DV for " + resolved + ": " + e.getMessage());
          }
          ArchitectureGraph graph =
              ArchitectureGraphFromModel.fromBusinessVault(bv, effective, variables);
          writeDrawio(graph, out);
          results.add(new ExportResult(graph, out, modelWarnings));
        } else if (lower.endsWith(".hdm")) {
          DimensionalModel dm =
              ResourceDefinitionGroupResolver.loadDimensionalModel(
                  resolved, variables, metadataProvider);
          ArchitectureGraph graph = ArchitectureGraphFromModel.fromDimensional(dm, variables);
          writeDrawio(graph, out);
          results.add(new ExportResult(graph, out, modelWarnings));
        }
      } catch (HopException e) {
        skipWarnings.add("Skip model " + path + ": " + e.getMessage());
      }
    }

    if (!skipWarnings.isEmpty()) {
      if (results.isEmpty()) {
        ArchitectureGraph empty = new ArchitectureGraph();
        empty.setName("empty");
        results.add(new ExportResult(empty, base, skipWarnings));
      } else {
        // Attach skip warnings to the last successful result so callers can log them.
        ExportResult last = results.get(results.size() - 1);
        List<String> combined = new ArrayList<>(last.getWarnings());
        combined.addAll(skipWarnings);
        results.set(
            results.size() - 1,
            new ExportResult(
                last.getGraph(), last.getOutputPath(), combined, last.getExecutionMap()));
      }
    }
    return results;
  }

  /**
   * Prefer model files listed on a resource definition group when {@code groupName} is non-empty;
   * otherwise return {@code fallbackPaths} (may be empty).
   */
  public static List<String> resolveModelPaths(
      String groupName, List<String> fallbackPaths, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (!Utils.isEmpty(groupName)) {
      return modelPathsFromResourceDefinitionGroup(groupName, metadataProvider);
    }
    return fallbackPaths != null ? fallbackPaths : List.of();
  }

  /**
   * Model paths from a resource definition group (DV, then BV, then DM; list order within each).
   */
  public static List<String> modelPathsFromResourceDefinitionGroup(
      String groupName, IHopMetadataProvider metadataProvider) throws HopException {
    ResourceDefinitionGroupMeta group =
        ResourceDefinitionGroupResolver.loadGroup(groupName, metadataProvider);
    return modelPathsFromResourceDefinitionGroup(group);
  }

  /** Model paths from a loaded resource definition group (DV, then BV, then DM). */
  public static List<String> modelPathsFromResourceDefinitionGroup(
      ResourceDefinitionGroupMeta group) {
    List<String> paths = new ArrayList<>();
    if (group == null) {
      return paths;
    }
    for (String modelFile : group.getDataVaultModelFiles()) {
      if (!Utils.isEmpty(modelFile)) {
        paths.add(modelFile);
      }
    }
    for (String modelFile : group.getBusinessVaultModelFiles()) {
      if (!Utils.isEmpty(modelFile)) {
        paths.add(modelFile);
      }
    }
    for (String modelFile : group.getDimensionalModelFiles()) {
      if (!Utils.isEmpty(modelFile)) {
        paths.add(modelFile);
      }
    }
    return paths;
  }

  /** Discover model paths from an execution map document. */
  public static List<String> modelPathsFromExecutionMap(ExecutionMapDocument map) {
    List<String> paths = new ArrayList<>();
    if (map == null) {
      return paths;
    }
    for (ExecutionMapNode node : map.getNodesOrEmpty()) {
      if (node == null || node.getNodeType() == null) {
        continue;
      }
      if (node.getNodeType() == ExecutionMapNodeType.DATA_VAULT_MODEL
          || node.getNodeType() == ExecutionMapNodeType.BUSINESS_VAULT_MODEL
          || node.getNodeType() == ExecutionMapNodeType.DIMENSIONAL_MODEL) {
        if (!Utils.isEmpty(node.getPath())) {
          paths.add(node.getPath());
        }
      }
    }
    return paths;
  }

  /**
   * File basename without extension for a model path ({@code models/retail-360.hdv} → {@code
   * retail-360}).
   */
  public static String modelBasename(String path) {
    if (Utils.isEmpty(path)) {
      return "";
    }
    String normalized = path.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
    // Strip trailing query/fragment if any (VFS edge cases)
    int q = name.indexOf('?');
    if (q >= 0) {
      name = name.substring(0, q);
    }
    String lower = name.toLowerCase();
    if (lower.endsWith(".hdv")
        || lower.endsWith(".hbv")
        || lower.endsWith(".hdm")
        || lower.endsWith(".hsm")
        || lower.endsWith(".drawio")) {
      return name.substring(0, name.lastIndexOf('.'));
    }
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      return name.substring(0, dot);
    }
    return name;
  }

  /**
   * Output path for a single model under type subfolders, or {@code null} if the extension is not a
   * DV/BV/DM model file.
   *
   * <p>Examples: {@code base/data-vault/retail-360.drawio}, {@code
   * base/business-vault/retail-360.drawio}, {@code base/dimensional/retail-f-orders.drawio}.
   */
  public static String perModelDrawioPath(String baseDirectory, String modelPath) {
    if (Utils.isEmpty(baseDirectory) || Utils.isEmpty(modelPath)) {
      return null;
    }
    String base = baseDirectory.replace('\\', '/').replaceAll("/+$", "");
    String basename = modelBasename(modelPath);
    if (Utils.isEmpty(basename)) {
      return null;
    }
    String lower = modelPath.replace('\\', '/').toLowerCase();
    if (lower.endsWith(".hdv")) {
      return base + "/data-vault/" + basename + ".drawio";
    }
    if (lower.endsWith(".hbv")) {
      return base + "/business-vault/" + basename + ".drawio";
    }
    if (lower.endsWith(".hdm")) {
      return base + "/dimensional/" + basename + ".drawio";
    }
    return null;
  }

  public static void writeDrawio(ArchitectureGraph graph, String outputPath) throws HopException {
    if (Utils.isEmpty(outputPath)) {
      throw new HopException("Architecture export output path is empty");
    }
    String xml = DrawioArchitectureExporter.export(graph);
    try {
      ensureParentFolder(outputPath);
      try (OutputStream out = HopVfs.getOutputStream(outputPath, false)) {
        out.write(xml.getBytes(StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      throw new HopException("Unable to write Draw.io architecture file: " + outputPath, e);
    }
  }

  private static LineageSnapshot collectLineage(
      String path, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    String lower = path.toLowerCase();
    if (lower.endsWith(".hdv")) {
      DataVaultModel model =
          DvModelLoadSupport.loadDataVaultModel(path, null, variables, metadataProvider);
      return DvModelLineageCollector.collect(model, variables, metadataProvider, null);
    }
    if (lower.endsWith(".hbv")) {
      BusinessVaultModel model =
          ResourceDefinitionGroupResolver.loadBusinessVaultModel(path, variables, metadataProvider);
      return BvModelLineageCollector.collect(model, variables);
    }
    if (lower.endsWith(".hdm")) {
      DimensionalModel model =
          ResourceDefinitionGroupResolver.loadDimensionalModel(path, variables, metadataProvider);
      return DmModelLineageCollector.collect(model, variables, metadataProvider);
    }
    throw new HopException("Unsupported model file for data architecture export: " + path);
  }

  private static String resolveOutputPath(
      String configured, String defaultName, IVariables variables) {
    String out = configured;
    if (Utils.isEmpty(out)) {
      String projectHome = variables != null ? variables.getVariable("PROJECT_HOME") : null;
      if (!Utils.isEmpty(projectHome)) {
        out = "${PROJECT_HOME}/work/architecture/" + defaultName;
      } else {
        out = defaultName;
      }
    }
    return variables != null ? variables.resolve(out) : out;
  }

  /**
   * Resolve a path relative to {@code PROJECT_HOME} when a Hop project/environment is enabled.
   *
   * <p>Same idea as hop-run's {@code HopRunCalculateFilenameExtensionPoint}: if the path is not
   * absolute and {@code PROJECT_HOME} is set, prefer {@code ${PROJECT_HOME}/&lt;path&gt;}.
   *
   * @param forWrite when true, always place relative paths under PROJECT_HOME (file may not exist
   *     yet); when false, try PROJECT_HOME if the CWD-relative path does not exist
   */
  public static String resolveProjectRelativePath(
      String path, IVariables variables, boolean forWrite) throws HopException {
    if (Utils.isEmpty(path)) {
      return path;
    }
    String resolved = variables != null ? variables.resolve(path) : path;
    if (isAbsoluteOrVfs(resolved)) {
      return resolved;
    }
    String projectHome = variables != null ? variables.getVariable("PROJECT_HOME") : null;
    if (Utils.isEmpty(projectHome)) {
      return resolved;
    }
    String relative = path.trim();
    while (relative.startsWith("./") || relative.startsWith(".\\")) {
      relative = relative.substring(2);
    }
    String underProject =
        variables.resolve("${PROJECT_HOME}/" + relative.replace('\\', '/').replaceAll("^/+", ""));
    if (forWrite) {
      return underProject;
    }
    try {
      FileObject cwdRelative = HopVfs.getFileObject(resolved);
      if (cwdRelative.exists()) {
        return resolved;
      }
      FileObject projectRelative = HopVfs.getFileObject(underProject);
      if (projectRelative.exists()) {
        return underProject;
      }
    } catch (Exception ignored) {
      // fall through to project path
    }
    // Prefer project home for relative reads that do not exist under CWD (matches hop-run).
    return underProject;
  }

  private static boolean isAbsoluteOrVfs(String path) {
    if (Utils.isEmpty(path)) {
      return false;
    }
    if (path.startsWith("/") || path.startsWith("\\")) {
      return true;
    }
    // Windows drive letter
    if (path.length() >= 3
        && Character.isLetter(path.charAt(0))
        && path.charAt(1) == ':'
        && (path.charAt(2) == '/' || path.charAt(2) == '\\')) {
      return true;
    }
    // VFS schemes: file://, s3://, pvfs://, zip:...
    int scheme = path.indexOf("://");
    if (scheme > 0) {
      return true;
    }
    return path.startsWith("zip:");
  }

  private static void ensureParentFolder(String outputPath) throws HopException {
    try {
      FileObject file = HopVfs.getFileObject(outputPath);
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to create parent folder for " + outputPath, e);
    }
  }
}
