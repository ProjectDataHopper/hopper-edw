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
import org.hopper.edw.catalog.transform.recorddatainput.RecordDefinitionDataInputMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.pipeline.DvPipelineSourceSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipelineCatalogSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Scans a Hop pipeline for {@link RecordDefinitionDataInputMeta} transforms and builds catalog
 * source references for lineage on a pipeline source.
 */
public final class SourcePipelineCatalogImportSupport {

  public static final String RECORD_DEFINITION_DATA_INPUT_PLUGIN_ID = "RecordDefinitionDataInput";

  private SourcePipelineCatalogImportSupport() {}

  /**
   * Load the pipeline at {@code pipelineFilename} and return every Record Definition Input (data)
   * reference found. Result may be empty.
   */
  public static List<SourcePipelineCatalogSource> importFromPipeline(
      String pipelineFilename, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(pipelineFilename)) {
      throw new HopException("Pipeline filename is required to import catalog sources");
    }
    PipelineMeta pipelineMeta =
        DvPipelineSourceSupport.loadSourcePipelineMeta(
            pipelineFilename, variables, metadataProvider);
    return importFromPipelineMeta(pipelineMeta, variables);
  }

  public static List<SourcePipelineCatalogSource> importFromPipelineMeta(
      PipelineMeta pipelineMeta, IVariables variables) {
    List<SourcePipelineCatalogSource> found = new ArrayList<>();
    if (pipelineMeta == null) {
      return found;
    }
    for (int i = 0; i < pipelineMeta.nrTransforms(); i++) {
      TransformMeta transformMeta = pipelineMeta.getTransform(i);
      if (transformMeta == null) {
        continue;
      }
      SourcePipelineCatalogSource ref = fromTransform(transformMeta, variables);
      if (ref != null) {
        found.add(ref);
      }
    }
    return found;
  }

  static SourcePipelineCatalogSource fromTransform(
      TransformMeta transformMeta, IVariables variables) {
    if (transformMeta == null) {
      return null;
    }
    ITransformMeta meta = transformMeta.getTransform();
    if (!(meta instanceof RecordDefinitionDataInputMeta dataInput)) {
      // Also accept by plugin id when classloaders differ
      String pluginId = transformMeta.getPluginId();
      if (!RECORD_DEFINITION_DATA_INPUT_PLUGIN_ID.equals(pluginId)) {
        return null;
      }
      return null;
    }

    SourcePipelineCatalogSource ref = new SourcePipelineCatalogSource();
    ref.setTransformName(transformMeta.getName());
    ref.setCatalogConnection(resolve(variables, dataInput.getCatalogConnectionName()));
    ref.setSelectFromInput(dataInput.isSelectFromInput());
    if (dataInput.isSelectFromInput()) {
      ref.setNamespaceField(resolve(variables, dataInput.getNamespaceField()));
      ref.setNameField(resolve(variables, dataInput.getNameField()));
    } else {
      ref.setNamespace(resolve(variables, dataInput.getNamespaceValue()));
      ref.setRecordName(resolve(variables, dataInput.getNameValue()));
    }
    return ref;
  }

  private static String resolve(IVariables variables, String value) {
    if (Utils.isEmpty(value)) {
      return "";
    }
    return variables != null ? variables.resolve(value) : value.trim();
  }
}
