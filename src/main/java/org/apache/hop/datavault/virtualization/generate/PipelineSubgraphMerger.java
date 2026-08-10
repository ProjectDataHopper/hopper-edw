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
package org.apache.hop.datavault.virtualization.generate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Copies a generated subgraph into a host pipeline with unique transform names. */
public final class PipelineSubgraphMerger {

  private PipelineSubgraphMerger() {}

  public record MergedSubgraph(TransformMeta outputTransform, Map<String, String> nameMap) {}

  /**
   * Merges all transforms/hops from {@code subgraph} into {@code host}. Returns the last transform
   * in subgraph order (fallback: first root with no incoming hop, or last added).
   */
  public static MergedSubgraph merge(
      PipelineMeta host,
      PipelineMeta subgraph,
      Point startLocation,
      Function<String, String> uniqueName)
      throws HopException {
    if (host == null || subgraph == null) {
      throw new HopException("Host and subgraph pipelines are required");
    }
    if (subgraph.getTransforms().isEmpty()) {
      throw new HopException("Subgraph has no transforms");
    }
    Map<String, String> nameMap = new HashMap<>();
    int index = 0;
    for (TransformMeta transform : subgraph.getTransforms()) {
      String original = transform.getName();
      String unique = uniqueName.apply(original);
      nameMap.put(original, unique);
      TransformMeta copy = (TransformMeta) transform.clone();
      copy.setName(unique);
      if (startLocation != null) {
        copy.setLocation(startLocation.x + index * 160, startLocation.y);
      }
      host.addTransform(copy);
      index++;
    }
    for (PipelineHopMeta hop : subgraph.getPipelineHops()) {
      if (hop == null || hop.getFromTransform() == null || hop.getToTransform() == null) {
        continue;
      }
      String from = nameMap.get(hop.getFromTransform().getName());
      String to = nameMap.get(hop.getToTransform().getName());
      if (from == null || to == null) {
        continue;
      }
      TransformMeta fromMeta = host.findTransform(from);
      TransformMeta toMeta = host.findTransform(to);
      if (fromMeta != null && toMeta != null) {
        host.addPipelineHop(new PipelineHopMeta(fromMeta, toMeta));
      }
    }

    // Prefer a sink: transform with no outgoing hops in the renamed subgraph.
    Set<String> hasOutgoing = new HashSet<>();
    for (PipelineHopMeta hop : subgraph.getPipelineHops()) {
      if (hop != null && hop.getFromTransform() != null) {
        hasOutgoing.add(hop.getFromTransform().getName().toLowerCase(Locale.ROOT));
      }
    }
    TransformMeta output = null;
    for (TransformMeta transform : subgraph.getTransforms()) {
      if (transform == null) {
        continue;
      }
      if (!hasOutgoing.contains(transform.getName().toLowerCase(Locale.ROOT))) {
        output = host.findTransform(nameMap.get(transform.getName()));
      }
    }
    if (output == null) {
      TransformMeta last = subgraph.getTransforms().get(subgraph.getTransforms().size() - 1);
      output = host.findTransform(nameMap.get(last.getName()));
    }
    return new MergedSubgraph(output, nameMap);
  }
}
