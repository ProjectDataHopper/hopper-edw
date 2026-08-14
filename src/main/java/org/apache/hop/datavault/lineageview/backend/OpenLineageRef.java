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
package org.apache.hop.datavault.lineageview.backend;

import lombok.Builder;
import lombok.Value;
import org.apache.hop.core.util.Utils;

/** OpenLineage dataset or job identity. Both fields are required for a valid ref. */
@Value
@Builder
public class OpenLineageRef {
  String namespace;
  String name;

  public boolean isComplete() {
    return !Utils.isEmpty(namespace) && !Utils.isEmpty(name);
  }

  /** Marquez {@code nodeId}, e.g. {@code dataset:Vault:f_orders}. */
  public String toNodeId(LineageNodeKind kind) {
    String prefix = kind == LineageNodeKind.JOB ? "job" : "dataset";
    return prefix + ":" + namespace + ":" + name;
  }

  /**
   * Parse {@code job:ns:name} / {@code dataset:ns:name}. The name may contain colons; split on the
   * first colon after the kind prefix.
   */
  public static OpenLineageRef fromNodeId(String nodeId) {
    if (Utils.isEmpty(nodeId)) {
      return null;
    }
    String rest = nodeId;
    if (rest.startsWith("job:") || rest.startsWith("dataset:")) {
      rest = rest.substring(rest.indexOf(':') + 1);
    }
    int colon = rest.indexOf(':');
    if (colon < 0) {
      return OpenLineageRef.builder().namespace("").name(rest).build();
    }
    return OpenLineageRef.builder()
        .namespace(rest.substring(0, colon))
        .name(rest.substring(colon + 1))
        .build();
  }
}
