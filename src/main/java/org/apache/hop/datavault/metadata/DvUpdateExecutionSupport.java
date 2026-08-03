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
package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.pipeline.PipelineMeta;

/** Execution ordering helpers for Data Vault model update runs. */
public final class DvUpdateExecutionSupport {

  private DvUpdateExecutionSupport() {}

  /**
   * Free (single-source) pipelines bucketed by table type so orchestrators can run hub → link →
   * satellite as separate phases. Parallelism is safe within a phase; satellites must not start
   * before parent links/hubs finish when foreign keys are enforced.
   */
  public static final class FreePipelineBuckets {
    private final List<PipelineMeta> hubs = new ArrayList<>();
    private final List<PipelineMeta> links = new ArrayList<>();
    private final List<PipelineMeta> satellites = new ArrayList<>();

    public List<PipelineMeta> hubs() {
      return hubs;
    }

    public List<PipelineMeta> links() {
      return links;
    }

    public List<PipelineMeta> satellites() {
      return satellites;
    }

    public void add(DvTableType tableType, List<PipelineMeta> pipelines) {
      if (tableType == null || pipelines == null || pipelines.isEmpty()) {
        return;
      }
      switch (tableType) {
        case HUB -> hubs.addAll(pipelines);
        case LINK -> links.addAll(pipelines);
        case SATELLITE -> satellites.addAll(pipelines);
        default -> {
          // ignore unknown types
        }
      }
    }

    /** Hub free pipelines, then links, then satellites (empty phases omitted). */
    public List<List<PipelineMeta>> phases() {
      List<List<PipelineMeta>> phases = new ArrayList<>(3);
      if (!hubs.isEmpty()) {
        phases.add(List.copyOf(hubs));
      }
      if (!links.isEmpty()) {
        phases.add(List.copyOf(links));
      }
      if (!satellites.isEmpty()) {
        phases.add(List.copyOf(satellites));
      }
      return phases;
    }

    public boolean isEmpty() {
      return hubs.isEmpty() && links.isEmpty() && satellites.isEmpty();
    }

    public int size() {
      return hubs.size() + links.size() + satellites.size();
    }

    /** Flattened hub → link → satellite order (for sequential staging master workflows). */
    public List<PipelineMeta> flattenedInDependencyOrder() {
      List<PipelineMeta> all = new ArrayList<>(size());
      all.addAll(hubs);
      all.addAll(links);
      all.addAll(satellites);
      return all;
    }
  }

  /** Returns hubs, then links, then satellites (model order preserved within each type). */
  public static List<IDvTable> orderTablesForPipelineExecution(List<IDvTable> tables) {
    List<IDvTable> hubs = new ArrayList<>();
    List<IDvTable> links = new ArrayList<>();
    List<IDvTable> satellites = new ArrayList<>();
    if (tables == null) {
      return List.of();
    }
    for (IDvTable table : tables) {
      if (table == null || table.getTableType() == null) {
        continue;
      }
      switch (table.getTableType()) {
        case HUB -> hubs.add(table);
        case LINK -> links.add(table);
        case SATELLITE -> satellites.add(table);
      }
    }
    List<IDvTable> ordered = new ArrayList<>(hubs.size() + links.size() + satellites.size());
    ordered.addAll(hubs);
    ordered.addAll(links);
    ordered.addAll(satellites);
    return ordered;
  }
}
