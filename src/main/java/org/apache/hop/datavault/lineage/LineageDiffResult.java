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
package org.apache.hop.datavault.lineage;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/** Aggregated lineage drift between a baseline snapshot and the current models. */
@Getter
public final class LineageDiffResult {

  private final String modelName;
  private final LineageLayer layer;
  private final String baselineSource;
  private final List<LineageDiffEntry> entries = new ArrayList<>();
  private boolean baselineMissing;

  public LineageDiffResult(String modelName, LineageLayer layer, String baselineSource) {
    this.modelName = modelName;
    this.layer = layer;
    this.baselineSource = baselineSource;
  }

  public LineageDiffResult add(LineageDiffEntry entry) {
    if (entry != null) {
      entries.add(entry);
    }
    return this;
  }

  public void setBaselineMissing(boolean baselineMissing) {
    this.baselineMissing = baselineMissing;
  }

  public boolean hasBlocking() {
    return entries.stream().anyMatch(e -> e.getSeverity() == LineageDiffSeverity.BLOCKING);
  }

  public boolean hasWarnings() {
    return entries.stream().anyMatch(e -> e.getSeverity() == LineageDiffSeverity.WARNING);
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  public long countBySeverity(LineageDiffSeverity severity) {
    return entries.stream().filter(e -> e.getSeverity() == severity).count();
  }
}
