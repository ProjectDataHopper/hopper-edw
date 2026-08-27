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

import java.nio.file.Path;
import java.util.List;
import lombok.Getter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.executionmap.CrawlOptions;
import org.hopper.edw.datavault.executionmap.ExecutionMapCrawler;
import org.hopper.edw.datavault.executionmap.ExecutionMapDiffSupport;
import org.hopper.edw.datavault.executionmap.ExecutionMapDiffSupport.DiffResult;
import org.hopper.edw.datavault.executionmap.ExecutionMapPersistence;
import org.hopper.edw.datavault.executionmap.OpenLineageExportSupport;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;

/** Generates and persists Hop execution map documents. */
public final class ExecutionMapService {

  private ExecutionMapService() {}

  @Getter
  public static final class GenerateResult {
    private final ExecutionMapDocument document;
    private final String outputPath;
    private final List<String> warnings;

    public GenerateResult(ExecutionMapDocument document, String outputPath, List<String> warnings) {
      this.document = document;
      this.outputPath = outputPath;
      this.warnings = warnings;
    }
  }

  @Getter
  public static final class RefreshResult {
    private final ExecutionMapDocument document;
    private final DiffResult diff;
    private final List<String> warnings;

    public RefreshResult(ExecutionMapDocument document, DiffResult diff, List<String> warnings) {
      this.document = document;
      this.diff = diff;
      this.warnings = warnings;
    }
  }

  public static GenerateResult generate(
      String rootArtifactPath,
      String outputPath,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      CrawlOptions options)
      throws HopException {
    ExecutionMapCrawler.CrawlResult crawlResult =
        ExecutionMapCrawler.crawl(rootArtifactPath, variables, metadataProvider, options);
    ExecutionMapDocument document = crawlResult.getDocument();
    String resolvedOutput = resolveOutputPath(rootArtifactPath, outputPath, variables);
    document.setFilename(resolvedOutput);
    document.setName(Path.of(resolvedOutput).getFileName().toString());
    ExecutionMapPersistence.save(document, resolvedOutput, variables);
    return new GenerateResult(document, resolvedOutput, crawlResult.getWarnings());
  }

  public static RefreshResult refresh(
      ExecutionMapDocument previous,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      CrawlOptions options)
      throws HopException {
    if (previous == null || Utils.isEmpty(previous.getRootArtifactPath())) {
      throw new HopException("Execution map has no root artifact path to refresh");
    }
    ExecutionMapCrawler.CrawlResult crawlResult =
        ExecutionMapCrawler.crawl(
            previous.getRootArtifactPath(), variables, metadataProvider, options);
    ExecutionMapDocument document = crawlResult.getDocument();
    DiffResult diff = ExecutionMapDiffSupport.compare(previous, document);
    document.setFilename(previous.getFilename());
    document.setName(previous.getName());
    if (!Utils.isEmpty(previous.getFilename())) {
      ExecutionMapPersistence.save(document, previous.getFilename(), variables);
    }
    return new RefreshResult(document, diff, crawlResult.getWarnings());
  }

  public static String defaultLineageOutputPath(String hemPath) {
    if (Utils.isEmpty(hemPath)) {
      return "lineage.json";
    }
    int dot = hemPath.lastIndexOf('.');
    String stem = dot > 0 ? hemPath.substring(0, dot) : hemPath;
    return stem + "-lineage.json";
  }

  public static void exportLineage(
      ExecutionMapDocument document, String outputPath, IVariables variables) throws HopException {
    if (document == null) {
      throw new HopException("Execution map document is required");
    }
    String resolvedOutput =
        !Utils.isEmpty(outputPath) && variables != null
            ? variables.resolve(outputPath)
            : outputPath;
    OpenLineageExportSupport.writeJson(document, resolvedOutput);
  }

  public static String resolveOutputPath(
      String rootArtifactPath, String outputPath, IVariables variables) {
    if (!Utils.isEmpty(outputPath)) {
      return variables != null ? variables.resolve(outputPath) : outputPath;
    }
    String resolvedRoot =
        variables != null ? variables.resolve(rootArtifactPath) : rootArtifactPath;
    Path path = Path.of(resolvedRoot);
    String base = path.getFileName().toString();
    int dot = base.lastIndexOf('.');
    String stem = dot > 0 ? base.substring(0, dot) : base;
    Path parent = path.getParent();
    String filename = stem + ".hem";
    return parent != null ? parent.resolve(filename).toString() : filename;
  }
}
