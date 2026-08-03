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
package org.apache.hop.datavault.metadata.dimensional;

import java.util.HashMap;
import java.util.Map;
import org.apache.hop.catalog.hopgui.preview.RecordDefinitionPreviewSupport;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.datavault.metadata.DvSourcePreviewInputSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Helpers for dimensional tables that use a data-catalog record definition as the staging source.
 */
public final class DmSourceRecordDefinitionSupport {

  private static final Class<?> PKG = DmSourceRecordDefinitionSupport.class;
  private static final int TRANSFORM_SPACING_X = 128;

  private DmSourceRecordDefinitionSupport() {}

  public static String resolveCatalogConnection(
      DimensionalConfiguration config,
      DmSourceConfiguration source,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String connection = source != null ? source.resolveSourceCatalogConnection(variables) : null;
    if (!Utils.isEmpty(connection)) {
      return connection;
    }
    if (config != null && !Utils.isEmpty(config.getDataCatalogConnection())) {
      String configured = config.getDataCatalogConnection();
      if (variables != null) {
        configured = variables.resolve(configured);
      }
      if (!Utils.isEmpty(configured)) {
        return configured;
      }
    }
    return DvSourceCatalogService.resolvePreferredCatalogConnection(
        null, variables, metadataProvider);
  }

  public static RecordDefinition loadRecordDefinition(
      DmSourceConfiguration source,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (source == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DmSourceRecordDefinitionSupport.Error.MissingConfiguration"));
    }
    String catalogConnection =
        resolveCatalogConnection(config, source, variables, metadataProvider);
    String namespace = source.resolveSourceRecordNamespace(variables);
    String name = source.resolveSourceRecordName(variables);
    if (Utils.isEmpty(namespace) || Utils.isEmpty(name)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourceRecordDefinitionSupport.Error.MissingRecordKey"));
    }

    RecordDefinition definition =
        RecordDefinitionRegistry.getInstance()
            .read(
                catalogConnection,
                new RecordDefinitionKey(namespace, name),
                variables,
                metadataProvider);
    if (definition == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DmSourceRecordDefinitionSupport.Error.RecordNotFound",
              namespace,
              name,
              catalogConnection));
    }
    if (!RecordDefinitionPreviewSupport.supportsPreview(definition)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DmSourceRecordDefinitionSupport.Error.UnsupportedRecord", namespace, name));
    }
    return definition;
  }

  public static IRowMeta tryResolveSourceRowMeta(
      DmSourceConfiguration source,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    try {
      RecordDefinition definition =
          loadRecordDefinition(source, config, variables, metadataProvider);
      return resolveRowMetaFromDefinition(definition, variables);
    } catch (HopException ignored) {
      return null;
    }
  }

  public static IRowMeta resolveSourceRowMeta(
      DmSourceConfiguration source,
      DimensionalConfiguration config,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    RecordDefinition definition = loadRecordDefinition(source, config, variables, metadataProvider);
    IRowMeta rowMeta = resolveRowMetaFromDefinition(definition, variables);
    if (rowMeta == null || rowMeta.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourceRecordDefinitionSupport.Error.NoFields"));
    }
    return rowMeta;
  }

  private static IRowMeta resolveRowMetaFromDefinition(
      RecordDefinition definition, IVariables variables) throws HopException {
    if (definition.getFields() != null && !definition.getFields().isEmpty()) {
      return definition.getFields();
    }
    if (definition.getDvSource() != null
        && definition.getDvSource().getFields() != null
        && !definition.getDvSource().getFields().isEmpty()) {
      return DvSourceFieldSupport.toRowMetaFromCatalog(
          definition.getDvSource().getFields(), variables);
    }
    return null;
  }

  public static TransformMeta appendSourceInput(
      DmSourceConfiguration source,
      DimensionalConfiguration config,
      PipelineMeta pipelineMeta,
      Point location,
      String transformNamePrefix,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    RecordDefinition definition = loadRecordDefinition(source, config, variables, metadataProvider);
    DvSourcePreviewInputSupport.PreviewPipeline preview =
        RecordDefinitionPreviewSupport.buildPreviewPipeline(
            definition, variables, metadataProvider, 0);
    return mergePreviewPipeline(pipelineMeta, location, transformNamePrefix, preview);
  }

  static TransformMeta mergePreviewPipeline(
      PipelineMeta target,
      Point location,
      String transformNamePrefix,
      DvSourcePreviewInputSupport.PreviewPipeline preview)
      throws HopException {
    if (target == null || preview == null || preview.pipelineMeta() == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DmSourceRecordDefinitionSupport.Error.MissingPreviewPipeline"));
    }

    PipelineMeta source = preview.pipelineMeta();
    String outputTransformName = preview.previewTransformName();
    Map<String, String> renamed = new HashMap<>();
    TransformMeta outputTransform = null;
    int x = location != null ? location.x : 100;
    int y = location != null ? location.y : 100;

    for (int i = 0; i < source.nrTransforms(); i++) {
      TransformMeta original = source.getTransform(i);
      String uniqueName = uniqueTransformName(target, transformNamePrefix, original.getName());
      TransformMeta copy = (TransformMeta) original.clone();
      copy.setName(uniqueName);
      copy.setLocation(x, y);
      target.addTransform(copy);
      renamed.put(original.getName(), uniqueName);
      if (original.getName().equals(outputTransformName)) {
        outputTransform = copy;
      }
      x += TRANSFORM_SPACING_X;
    }

    for (int i = 0; i < source.nrPipelineHops(); i++) {
      PipelineHopMeta hop = source.getPipelineHop(i);
      if (hop == null || hop.getFromTransform() == null || hop.getToTransform() == null) {
        continue;
      }
      String fromName = renamed.get(hop.getFromTransform().getName());
      String toName = renamed.get(hop.getToTransform().getName());
      TransformMeta from = target.findTransform(fromName);
      TransformMeta to = target.findTransform(toName);
      if (from != null && to != null) {
        target.addPipelineHop(new PipelineHopMeta(from, to));
      }
    }

    if (outputTransform == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DmSourceRecordDefinitionSupport.Error.MissingOutputTransform",
              outputTransformName));
    }
    return outputTransform;
  }

  private static String uniqueTransformName(
      PipelineMeta pipelineMeta, String prefix, String baseName) {
    String sanitized = Utils.isEmpty(baseName) ? "source" : baseName.trim();
    String candidate = Utils.isEmpty(prefix) ? sanitized : prefix + sanitized;
    if (pipelineMeta.findTransform(candidate) == null) {
      return candidate;
    }
    int suffix = 2;
    while (pipelineMeta.findTransform(candidate + "_" + suffix) != null) {
      suffix++;
    }
    return candidate + "_" + suffix;
  }
}
