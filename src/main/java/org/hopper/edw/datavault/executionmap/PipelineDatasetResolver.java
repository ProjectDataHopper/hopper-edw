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
package org.hopper.edw.datavault.executionmap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapEdgeType;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNodeType;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;

/** Adds dataset leaf nodes from TableInput and TableOutput transforms in pipelines. */
public final class PipelineDatasetResolver {

  private static final Pattern FROM_TABLE = Pattern.compile("(?is)\\bfrom\\s+([\\w.\"]+)");

  private PipelineDatasetResolver() {}

  public static void resolvePipeline(
      ExecutionMapContext context, String pipelineNodeId, PipelineMeta pipelineMeta) {
    if (context == null
        || pipelineMeta == null
        || Utils.isEmpty(pipelineNodeId)
        || !context.getOptions().isIncludeDatasetNodes()) {
      return;
    }
    String originModelNodeId = DatasetNodeSupport.resolveOriginModelNodeId(context, pipelineNodeId);
    for (TransformMeta transformMeta : pipelineMeta.getTransforms()) {
      if (transformMeta == null) {
        continue;
      }
      ITransformMeta transform = transformMeta.getTransform();
      if (transform instanceof TableInputMeta tableInput) {
        resolveTableInput(
            context, pipelineNodeId, originModelNodeId, transformMeta.getName(), tableInput);
      } else if (transform instanceof TableOutputMeta tableOutput) {
        resolveTableOutput(
            context, pipelineNodeId, originModelNodeId, transformMeta.getName(), tableOutput);
      }
    }
  }

  private static void resolveTableInput(
      ExecutionMapContext context,
      String pipelineNodeId,
      String originModelNodeId,
      String transformName,
      TableInputMeta tableInput) {
    String connection =
        DatasetNodeSupport.resolveValue(context.getVariables(), tableInput.getConnection());
    String tableName = extractTableName(tableInput, transformName);
    if (Utils.isEmpty(connection) || Utils.isEmpty(tableName)) {
      return;
    }
    String datasetNodeId =
        resolveDatasetNodeId(
            context,
            originModelNodeId,
            pipelineNodeId,
            ExecutionMapNodeType.SOURCE_DATASET,
            connection,
            tableName,
            transformName);
    if (!Utils.isEmpty(datasetNodeId)) {
      context.addEdge(
          ExecutionMapEdgeType.READS_FROM, datasetNodeId, pipelineNodeId, transformName);
      DatasetNodeSupport.linkDatasetToModel(
          context, originModelNodeId, datasetNodeId, transformName);
    }
  }

  private static void resolveTableOutput(
      ExecutionMapContext context,
      String pipelineNodeId,
      String originModelNodeId,
      String transformName,
      TableOutputMeta tableOutput) {
    String connection =
        DatasetNodeSupport.resolveValue(context.getVariables(), tableOutput.getConnection());
    String tableName =
        DatasetNodeSupport.resolveValue(context.getVariables(), tableOutput.getTableName());
    if (Utils.isEmpty(tableName)) {
      tableName = extractTableFromTransformName(transformName);
    }
    if (Utils.isEmpty(connection) || Utils.isEmpty(tableName)) {
      return;
    }
    String datasetNodeId =
        resolveDatasetNodeId(
            context,
            originModelNodeId,
            pipelineNodeId,
            ExecutionMapNodeType.TARGET_DATASET,
            connection,
            tableName,
            transformName);
    if (!Utils.isEmpty(datasetNodeId)) {
      context.addEdge(ExecutionMapEdgeType.WRITES_TO, pipelineNodeId, datasetNodeId, transformName);
      DatasetNodeSupport.linkDatasetToModel(
          context, originModelNodeId, datasetNodeId, transformName);
    }
  }

  private static String resolveDatasetNodeId(
      ExecutionMapContext context,
      String originModelNodeId,
      String pipelineNodeId,
      ExecutionMapNodeType nodeType,
      String connection,
      String tableName,
      String transformName) {
    if (!Utils.isEmpty(originModelNodeId)) {
      String catalogNodeId =
          PipelineDatasetCatalogResolver.resolveDatasetNodeId(
              context,
              originModelNodeId,
              pipelineNodeId,
              nodeType,
              connection,
              tableName,
              transformName);
      if (!Utils.isEmpty(catalogNodeId)) {
        return catalogNodeId;
      }
    }
    return DatasetNodeSupport.getOrCreateDatasetNode(
        context, nodeType, connection, tableName, "DATABASE", pipelineNodeId);
  }

  private static String extractTableName(TableInputMeta tableInput, String transformName) {
    String fromName = extractTableFromTransformName(transformName);
    if (!Utils.isEmpty(fromName)) {
      return fromName;
    }
    try {
      String sql = tableInput.getEffectiveSql(null);
      if (!Utils.isEmpty(sql)) {
        Matcher matcher = FROM_TABLE.matcher(sql);
        if (matcher.find()) {
          return stripQuotes(matcher.group(1));
        }
      }
    } catch (HopException ignored) {
      // fall through to transform name
    }
    String sql = tableInput.getSql();
    if (!Utils.isEmpty(sql)) {
      Matcher matcher = FROM_TABLE.matcher(sql);
      if (matcher.find()) {
        return stripQuotes(matcher.group(1));
      }
    }
    return null;
  }

  private static String extractTableFromTransformName(String transformName) {
    if (Utils.isEmpty(transformName)) {
      return null;
    }
    String trimmed = transformName.trim();
    int dot = trimmed.lastIndexOf('.');
    if (dot > 0 && dot < trimmed.length() - 1) {
      return trimmed.substring(dot + 1).trim();
    }
    if (trimmed.toLowerCase().startsWith("target_")) {
      return trimmed.substring("target_".length());
    }
    if (trimmed.toLowerCase().startsWith("source")) {
      int space = trimmed.indexOf(' ');
      if (space > 0 && space < trimmed.length() - 1) {
        String candidate = trimmed.substring(space + 1).trim();
        dot = candidate.lastIndexOf('.');
        if (dot > 0 && dot < candidate.length() - 1) {
          return candidate.substring(dot + 1).trim();
        }
        return candidate;
      }
    }
    return null;
  }

  private static String stripQuotes(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    int dot = trimmed.lastIndexOf('.');
    if (dot > 0 && dot < trimmed.length() - 1) {
      return trimmed.substring(dot + 1);
    }
    return trimmed;
  }
}
