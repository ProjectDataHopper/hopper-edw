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
package org.hopper.edw.datavault.metadata.pipeline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Resolves pipeline transform fields without calling GUI-oriented extension points. This is
 * required for headless CLI commands such as {@code hop execution-map}.
 */
public final class HeadlessPipelineFieldSupport {

  private HeadlessPipelineFieldSupport() {}

  public static IRowMeta resolveTransformFields(
      PipelineMeta pipelineMeta, IVariables variables, String transformName)
      throws HopTransformException {
    if (pipelineMeta == null) {
      throw new HopTransformException("Pipeline metadata is required to resolve transform fields");
    }
    if (Utils.isEmpty(transformName)) {
      throw new HopTransformException("Transform name is required to resolve transform fields");
    }

    TransformMeta transformMeta = pipelineMeta.findTransform(transformName);
    if (transformMeta == null) {
      throw new HopTransformException(
          "Transform '" + transformName + "' was not found in pipeline metadata");
    }

    Map<String, IRowMeta> cache = new HashMap<>();
    return resolveTransformFields(pipelineMeta, variables, transformMeta, null, cache);
  }

  private static IRowMeta resolveTransformFields(
      PipelineMeta pipelineMeta,
      IVariables variables,
      TransformMeta transformMeta,
      TransformMeta targetTransform,
      Map<String, IRowMeta> cache)
      throws HopTransformException {
    String cacheKey = cacheKey(transformMeta, targetTransform);
    IRowMeta cached = cache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    if (targetTransform != null && transformMeta.isSendingErrorRowsToTransform(targetTransform)) {
      IRowMeta row = pipelineMeta.getPrevTransformFields(variables, transformMeta);
      if (row == null || row.isEmpty()) {
        row =
            resolveThisTransformFields(
                pipelineMeta, variables, transformMeta, targetTransform, new RowMeta(), cache);
      }
      if (transformMeta.getTransformErrorMeta() != null) {
        row.addRowMeta(transformMeta.getTransformErrorMeta().getErrorRowMeta(variables));
      }
      cache.put(cacheKey, row);
      return row;
    }

    IRowMeta row = new RowMeta();
    collectPreviousTransformFields(
        pipelineMeta, variables, transformMeta, targetTransform, row, cache);
    IRowMeta result =
        resolveThisTransformFields(
            pipelineMeta, variables, transformMeta, targetTransform, row, cache);
    cache.put(cacheKey, result);
    return result;
  }

  private static void collectPreviousTransformFields(
      PipelineMeta pipelineMeta,
      IVariables variables,
      TransformMeta transformMeta,
      TransformMeta targetTransform,
      IRowMeta row,
      Map<String, IRowMeta> cache)
      throws HopTransformException {
    List<TransformMeta> previousTransforms =
        pipelineMeta.findPreviousTransforms(transformMeta, false);
    for (int i = 0; i < previousTransforms.size(); i++) {
      TransformMeta previousTransform = previousTransforms.get(i);
      IRowMeta add =
          resolveTransformFields(pipelineMeta, variables, previousTransform, transformMeta, cache);
      if (add == null) {
        add = new RowMeta();
      }
      if (i == 0) {
        row.addRowMeta(add);
      } else {
        for (int x = 0; x < add.size(); x++) {
          IValueMeta valueMeta = add.getValueMeta(x);
          if (row.searchValueMeta(valueMeta.getName()) == null) {
            row.addValueMeta(valueMeta);
          }
        }
      }
    }
  }

  private static IRowMeta resolveThisTransformFields(
      PipelineMeta pipelineMeta,
      IVariables variables,
      TransformMeta transformMeta,
      TransformMeta targetTransform,
      IRowMeta row,
      Map<String, IRowMeta> cache)
      throws HopTransformException {
    ITransformMeta transform = transformMeta.getTransform();
    IRowMeta[] infoRowMeta;
    TransformMeta[] infoTransforms = pipelineMeta.getInfoTransform(transformMeta);
    if (Utils.isEmpty(infoTransforms)) {
      try {
        infoRowMeta = new IRowMeta[] {transform.getTableFields(variables)};
      } catch (HopDatabaseException databaseException) {
        throw new HopTransformException(
            "Error getting table fields in transform " + transformMeta.getName(),
            databaseException);
      }
    } else {
      infoRowMeta = new IRowMeta[infoTransforms.length];
      for (int i = 0; i < infoTransforms.length; i++) {
        infoRowMeta[i] =
            resolveTransformFields(pipelineMeta, variables, infoTransforms[i], null, cache);
      }
    }

    IRowMeta fields = row.clone();
    IRowMeta[] clonedInfo = cloneRowMetaInterfaces(infoRowMeta);
    transform.getFields(
        pipelineMeta,
        fields,
        transformMeta.getName(),
        clonedInfo,
        targetTransform,
        variables,
        pipelineMeta.getMetadataProvider());
    return fields;
  }

  private static IRowMeta[] cloneRowMetaInterfaces(IRowMeta[] rowMetas) {
    if (rowMetas == null) {
      return null;
    }
    IRowMeta[] clones = new IRowMeta[rowMetas.length];
    for (int i = 0; i < rowMetas.length; i++) {
      clones[i] = rowMetas[i] != null ? rowMetas[i].clone() : null;
    }
    return clones;
  }

  private static String cacheKey(TransformMeta transformMeta, TransformMeta targetTransform) {
    return transformMeta.getName()
        + (targetTransform != null ? "-" + targetTransform.getName() : "");
  }
}
