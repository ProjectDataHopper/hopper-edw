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
package org.apache.hop.datavault.impact;

import java.util.List;
import org.apache.hop.catalog.model.RecordDefinitionKey;

/** Convenience queries over an {@link ImpactGraph}. */
public final class ImpactQuery {

  private ImpactQuery() {}

  public static List<ImpactNode> blastRadius(
      ImpactGraph graph, RecordDefinitionKey sourceKey, String fieldName) {
    if (graph == null) {
      return List.of();
    }
    return graph.blastRadius(sourceKey, fieldName);
  }

  public static String formatLabels(
      ImpactGraph graph, RecordDefinitionKey sourceKey, String fieldName) {
    if (graph == null) {
      return "";
    }
    return graph.formatBlastRadiusLabels(sourceKey, fieldName);
  }
}
