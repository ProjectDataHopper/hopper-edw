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

import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;

/** Measured card size for an execution map node. */
public record ExecutionMapNodeCardMetrics(
    int width, int height, int nameX, int nameY, int nameWidth, int nameHeight) {

  public static ExecutionMapNodeCardMetrics defaultFor(ExecutionMapNode node) {
    String name = node != null && node.getName() != null ? node.getName() : "?";
    int estimatedNameWidth = Math.max(ExecutionMapMetrics.MIN_NODE_WIDTH - 80, name.length() * 8);
    int width =
        Math.max(ExecutionMapMetrics.MIN_NODE_WIDTH, 16 + 32 + 16 + estimatedNameWidth + 16);
    int height = Math.max(ExecutionMapMetrics.MIN_NODE_HEIGHT, 16 + 32 + 16 + 14 + 16);
    int nameX = 16 + 32 + 16;
    int nameY = 16;
    return new ExecutionMapNodeCardMetrics(width, height, nameX, nameY, estimatedNameWidth, 14);
  }
}
