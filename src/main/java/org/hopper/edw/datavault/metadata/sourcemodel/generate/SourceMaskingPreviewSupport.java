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
package org.hopper.edw.datavault.metadata.sourcemodel.generate;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.RowAdapter;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMasking;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;

/** Preview of a {@link SourceMasking} card by running the generated pipeline. */
public final class SourceMaskingPreviewSupport {

  public static final int DEFAULT_ROW_LIMIT = 50;

  private SourceMaskingPreviewSupport() {}

  public record PreviewPipeline(PipelineMeta pipelineMeta, String previewTransformName) {}

  public static PreviewPipeline buildPreviewPipeline(
      SourceModel model,
      SourceMasking masking,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    validateForPreview(masking);
    PipelineMeta pipelineMeta =
        SourceMaskingPipelineGenerator.generate(model, masking, variables, metadataProvider);
    List<TransformMeta> transforms = pipelineMeta.getTransforms();
    if (transforms == null || transforms.isEmpty()) {
      throw new HopException("Generated masking pipeline has no transforms");
    }
    return new PreviewPipeline(pipelineMeta, transforms.get(transforms.size() - 1).getName());
  }

  public static List<RowMetaAndData> preview(
      SourceModel model,
      SourceMasking masking,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    int limit = rowLimit > 0 ? rowLimit : DEFAULT_ROW_LIMIT;
    if (masking != null && masking.getSampleRowLimit() > 0 && rowLimit <= 0) {
      limit = masking.getSampleRowLimit();
    }
    PreviewPipeline built = buildPreviewPipeline(model, masking, variables, metadataProvider);
    PipelineMeta pipelineMeta = built.pipelineMeta();
    String transformName = built.previewTransformName();

    List<RowMetaAndData> collected = new ArrayList<>();
    Pipeline pipeline = new LocalPipelineEngine(pipelineMeta);
    pipeline.setMetadataProvider(metadataProvider);
    if (variables != null) {
      pipeline.copyFrom(variables);
    }
    final int rowCap = limit;
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
              if (collected.size() < rowCap) {
                try {
                  collected.add(new RowMetaAndData(rowMeta.clone(), rowMeta.cloneRow(row)));
                } catch (Exception e) {
                  collected.add(new RowMetaAndData(rowMeta, row));
                }
              }
              if (collected.size() >= rowCap) {
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
            "Masking preview finished with " + pipeline.getErrors() + " error(s)");
      }
      return collected;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          "Error previewing source masking '" + (masking != null ? masking.getName() : "?") + "'",
          e);
    } finally {
      try {
        pipeline.cleanup();
      } catch (Exception ignored) {
        // ignore
      }
    }
  }

  public static void validateForPreview(SourceMasking masking) throws HopException {
    if (masking == null) {
      throw new HopException("Source masking is required");
    }
    if (Utils.isEmpty(masking.getName())) {
      throw new HopException("Source masking name is required");
    }
    if (Utils.isEmpty(masking.getParentSourceName())) {
      throw new HopException("Parent source is required for preview");
    }
    if (masking.getFields().isEmpty()) {
      throw new HopException("Source masking '" + masking.getName() + "' has no fields");
    }
  }
}
