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
package org.apache.hop.datavault.executionmap;

import org.apache.hop.datavault.metadata.executionmap.ExecutionMapEdgeType;

/** Controls which execution map edges influence ELK node placement. */
public final class ExecutionMapLayoutOptions {

  public static final ExecutionMapLayoutOptions DEFAULT = new ExecutionMapLayoutOptions();

  private ExecutionMapLayoutOptions() {}

  /** Returns whether the edge type should participate in layout positioning. */
  public boolean usesEdgeForLayout(ExecutionMapEdgeType edgeType) {
    if (edgeType == null) {
      return false;
    }
    return edgeType == ExecutionMapEdgeType.HOP || edgeType == ExecutionMapEdgeType.EXECUTES;
  }
}
