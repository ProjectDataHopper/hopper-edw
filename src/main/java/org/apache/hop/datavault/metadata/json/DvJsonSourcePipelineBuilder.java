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
package org.apache.hop.datavault.metadata.json;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.sourcemodel.generate.SourceJsonPipelineGenerator;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Base pipeline builder for {@link DvJsonSource}: inject parent + JsonInput graph from .hsm. */
@Getter
@Setter
public abstract class DvJsonSourcePipelineBuilder extends DvSourcePipelineBuilder {

  protected DvJsonSource jsonSource;

  protected DvJsonSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      IDvTable dvTable,
      Point startPoint) {
    super(
        variables,
        metadataProvider,
        model,
        pipelineMeta,
        recordSource,
        dvSource,
        dvTable,
        startPoint);
    jsonSource = (DvJsonSource) dvSource;
  }

  @Override
  public void build() throws HopException {
    DvJsonSourceResolver.ResolvedJson resolved =
        DvJsonSourceResolver.resolve(jsonSource, variables, metadataProvider);
    PipelineMeta generated =
        SourceJsonPipelineGenerator.generate(
            resolved.model(), resolved.jsonSource(), variables, metadataProvider);
    mergeGeneratedPipeline(generated);
  }

  private void mergeGeneratedPipeline(PipelineMeta generated) throws HopException {
    if (generated.getTransforms().isEmpty()) {
      throw new HopException("Generated JSON source pipeline has no transforms");
    }
    Map<String, String> nameMap = new HashMap<>();
    int index = 0;
    TransformMeta lastCopy = null;
    for (TransformMeta transform : generated.getTransforms()) {
      String original = transform.getName();
      String unique = uniqueTransformName(original);
      nameMap.put(original, unique);
      TransformMeta copy = (TransformMeta) transform.clone();
      copy.setName(unique);
      copy.setLocation(startPoint.x + index * TRANSFORM_SPACING_X, startPoint.y);
      pipelineMeta.addTransform(copy);
      lastCopy = copy;
      index++;
    }
    for (PipelineHopMeta hop : generated.getPipelineHops()) {
      if (hop == null || hop.getFromTransform() == null || hop.getToTransform() == null) {
        continue;
      }
      String from = nameMap.get(hop.getFromTransform().getName());
      String to = nameMap.get(hop.getToTransform().getName());
      if (from == null || to == null) {
        continue;
      }
      TransformMeta fromMeta = pipelineMeta.findTransform(from);
      TransformMeta toMeta = pipelineMeta.findTransform(to);
      if (fromMeta != null && toMeta != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(fromMeta, toMeta));
      }
    }
    resultTransform = lastCopy;
  }

  private String uniqueTransformName(String base) {
    String name = base;
    int i = 2;
    while (pipelineMeta.findTransform(name) != null) {
      name = base + " " + i;
      i++;
    }
    return name;
  }
}
