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
package org.apache.hop.datavault.metadata.sourcemodel.generate;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.RowAdapter;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Interactive preview of a {@link SourceJson} extraction by generating and running a limited local
 * pipeline.
 */
public final class SourceJsonPreviewSupport {

  public static final int DEFAULT_ROW_LIMIT = 50;

  private SourceJsonPreviewSupport() {}

  public record PreviewPipeline(PipelineMeta pipelineMeta, String previewTransformName) {}

  /** Builds the generated pipeline and the transform name to preview (last transform). */
  public static PreviewPipeline buildPreviewPipeline(
      SourceModel model,
      SourceJson jsonSource,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    validateForPreview(jsonSource);
    PipelineMeta pipelineMeta =
        SourceJsonPipelineGenerator.generate(model, jsonSource, variables, metadataProvider);
    List<TransformMeta> transforms = pipelineMeta.getTransforms();
    if (transforms == null || transforms.isEmpty()) {
      throw new HopException("Generated JSON source pipeline has no transforms");
    }
    String lastName = transforms.get(transforms.size() - 1).getName();
    return new PreviewPipeline(pipelineMeta, lastName);
  }

  /**
   * Runs a limited local pipeline and returns rows from the last transform. Intended for headless
   * unit tests and non-SWT callers; the GUI prefers {@code PipelinePreviewProgressDialog}.
   */
  public static List<RowMetaAndData> preview(
      SourceModel model,
      SourceJson jsonSource,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    int limit = rowLimit > 0 ? rowLimit : DEFAULT_ROW_LIMIT;
    PreviewPipeline built = buildPreviewPipeline(model, jsonSource, variables, metadataProvider);
    PipelineMeta pipelineMeta = built.pipelineMeta();
    String transformName = built.previewTransformName();

    List<RowMetaAndData> collected = new ArrayList<>();
    Pipeline pipeline = new LocalPipelineEngine(pipelineMeta);
    pipeline.setMetadataProvider(metadataProvider);
    if (variables != null) {
      pipeline.copyFrom(variables);
    }
    try {
      pipeline.prepareExecution();
      ITransform runThread = pipeline.findRunThread(transformName);
      if (runThread == null) {
        throw new HopException("Preview transform '" + transformName + "' was not started");
      }
      runThread.addRowListener(
          new RowAdapter() {
            @Override
            public void rowWrittenEvent(IRowMeta rowMeta, Object[] row) {
              if (collected.size() < limit) {
                try {
                  collected.add(new RowMetaAndData(rowMeta.clone(), rowMeta.cloneRow(row)));
                } catch (Exception e) {
                  collected.add(new RowMetaAndData(rowMeta, row));
                }
              }
              if (collected.size() >= limit) {
                try {
                  pipeline.stopAll();
                } catch (Exception ignored) {
                  // best effort
                }
              }
            }
          });
      pipeline.startThreads();
      pipeline.waitUntilFinished();
      if (pipeline.getErrors() > 0) {
        throw new HopException(
            "JSON source preview finished with "
                + pipeline.getErrors()
                + " error(s). Check Hop logs for details.");
      }
      return collected;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          "Error previewing source JSON '"
              + (jsonSource != null ? jsonSource.getName() : "?")
              + "'",
          e);
    } finally {
      try {
        pipeline.cleanup();
      } catch (Exception ignored) {
        // ignore
      }
    }
  }

  public static void validateForPreview(SourceJson jsonSource) throws HopException {
    if (jsonSource == null) {
      throw new HopException("Source JSON is required");
    }
    if (Utils.isEmpty(jsonSource.getName())) {
      throw new HopException("Source JSON name is required");
    }
    if (Utils.isEmpty(jsonSource.getParentSourceName())) {
      throw new HopException("Parent source is required for preview");
    }
    if (Utils.isEmpty(jsonSource.getJsonFieldName())) {
      throw new HopException("JSON field name is required for preview");
    }
    if (jsonSource.getFields().isEmpty()) {
      throw new HopException("At least one projected field is required for preview");
    }
  }
}
