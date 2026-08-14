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

import java.util.List;
import java.util.Optional;
import org.apache.hop.core.exception.HopException;

/**
 * Headless lineage query SPI. Implementations fetch raw structure only; {@link LineageGraphOps}
 * clips direction, depth, hide-jobs, and layers.
 */
public interface ILineageQueryService extends AutoCloseable {

  /** Thrown (as {@link HopException} message prefix) when the seed is missing after fetch. */
  String SEED_NOT_FOUND = "SEED_NOT_FOUND";

  LineageBackendKind kind();

  /**
   * Neighborhood around the seed. The returned graph has {@code seedNodeId} set to the matched
   * node, or this method throws {@link HopException} with {@link #SEED_NOT_FOUND}.
   */
  LineageGraph fetchGraph(LineageQuery query) throws HopException;

  default ColumnLineagePath fetchColumnPath(ColumnLineageQuery query) throws HopException {
    throw new HopException("Column lineage is not supported by " + kind());
  }

  Optional<JobDetails> fetchJob(OpenLineageRef job) throws HopException;

  Optional<DatasetDetails> fetchDataset(OpenLineageRef dataset) throws HopException;

  List<OpenLineageRef> searchDatasets(String nameHint) throws HopException;

  List<OpenLineageRef> searchJobs(String nameHint) throws HopException;

  boolean supportsColumnLineage();

  /** File-folder and Local-models: true. Marquez: false (need follow-up). */
  boolean facetsInlineOnGraph();

  @Override
  default void close() {
    // HttpClient / VFS handles; Marquez may no-op
  }
}
