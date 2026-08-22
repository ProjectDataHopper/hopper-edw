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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/** A table-level lineage job extracted from an execution map document. */
@Getter
public final class ExecutionMapLineageEdge {

  private final String jobNamespace;
  private final String jobName;
  private final String jobPath;
  private final List<DatasetRef> inputs = new ArrayList<>();
  private final List<DatasetRef> outputs = new ArrayList<>();

  public ExecutionMapLineageEdge(String jobNamespace, String jobName, String jobPath) {
    this.jobNamespace = jobNamespace;
    this.jobName = jobName;
    this.jobPath = jobPath;
  }

  @Getter
  public static final class DatasetRef {
    private final String namespace;
    private final String name;
    private final String kind;

    public DatasetRef(String namespace, String name, String kind) {
      this.namespace = namespace;
      this.name = name;
      this.kind = kind;
    }
  }
}
