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
package org.apache.hop.datavault.executionmap;

import java.util.Date;
import java.util.List;
import lombok.Getter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapRootArtifactType;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Entry point for building a Hop execution map from a root workflow or pipeline. */
public final class ExecutionMapCrawler {

  private ExecutionMapCrawler() {}

  @Getter
  public static final class CrawlResult {
    private final ExecutionMapDocument document;
    private final List<String> warnings;

    public CrawlResult(ExecutionMapDocument document, List<String> warnings) {
      this.document = document;
      this.warnings = warnings;
    }
  }

  public static CrawlResult crawl(
      String rootArtifactPath,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      CrawlOptions options)
      throws HopException {
    if (Utils.isEmpty(rootArtifactPath)) {
      throw new HopException("Root artifact path is required");
    }
    String resolvedPath =
        variables != null ? variables.resolve(rootArtifactPath) : rootArtifactPath;
    String lower = resolvedPath.toLowerCase();
    boolean isWorkflow = lower.endsWith(".hwf");
    boolean isPipeline = lower.endsWith(".hpl");
    if (!isWorkflow && !isPipeline) {
      throw new HopException(
          "Execution map crawl requires a .hwf or .hpl root artifact: " + rootArtifactPath);
    }

    ExecutionMapDocument document = new ExecutionMapDocument();
    // Store portable path; crawl still uses resolvedPath for I/O below.
    document.setRootArtifactPath(ExecutionMapPathSupport.toStoredPath(resolvedPath, variables));
    document.setRootArtifactType(
        isWorkflow ? ExecutionMapRootArtifactType.WORKFLOW : ExecutionMapRootArtifactType.PIPELINE);
    document.setCrawledAt(new Date());
    document.setHopProject(ExecutionMapContext.resolveProjectKey(variables));

    ExecutionMapContext context =
        new ExecutionMapContext(document, variables, metadataProvider, options);

    if (isWorkflow) {
      WorkflowCrawler.crawlWorkflow(context, resolvedPath, null, true, null);
    } else {
      PipelineCrawler.crawlPipeline(context, resolvedPath, null, true, null);
    }

    ExecutionMapLayoutSupport.layout(document);
    return new CrawlResult(document, context.getWarnings());
  }
}
