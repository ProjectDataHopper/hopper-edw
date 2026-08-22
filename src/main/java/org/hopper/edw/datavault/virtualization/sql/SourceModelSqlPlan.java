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
package org.hopper.edw.datavault.virtualization.sql;

import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.PipelineMeta;

/**
 * Result of planning free SQL against a source model: executable pipeline plus explain metadata.
 */
public record SourceModelSqlPlan(
    PipelineMeta pipelineMeta,
    String outputTransformName,
    IRowMeta outputRowMeta,
    List<String> pushdownSqlFragments,
    List<String> residualOperators,
    List<String> warnings,
    boolean fullPushdown) {

  public String explainText() {
    StringBuilder sb = new StringBuilder();
    if (fullPushdown) {
      sb.append("Strategy: full database pushdown\n");
    } else {
      sb.append("Strategy: residual Hop pipeline (mixed sources or unsupported pushdown)\n");
    }
    if (pushdownSqlFragments != null && !pushdownSqlFragments.isEmpty()) {
      sb.append("\n-- Pushdown SQL --\n");
      for (int i = 0; i < pushdownSqlFragments.size(); i++) {
        if (pushdownSqlFragments.size() > 1) {
          sb.append("-- fragment ").append(i + 1).append(" --\n");
        }
        sb.append(pushdownSqlFragments.get(i)).append('\n');
      }
    }
    if (residualOperators != null && !residualOperators.isEmpty()) {
      sb.append("\n-- Residual operators --\n");
      for (String op : residualOperators) {
        sb.append("  - ").append(op).append('\n');
      }
    }
    if (warnings != null && !warnings.isEmpty()) {
      sb.append("\n-- Warnings --\n");
      for (String w : warnings) {
        sb.append("  - ").append(w).append('\n');
      }
    }
    if (pipelineMeta != null) {
      sb.append("\n-- Pipeline transforms --\n");
      pipelineMeta
          .getTransforms()
          .forEach(
              t ->
                  sb.append("  - ")
                      .append(t.getName())
                      .append(" (")
                      .append(t.getTransformPluginId())
                      .append(")\n"));
    }
    return sb.toString().trim();
  }
}
