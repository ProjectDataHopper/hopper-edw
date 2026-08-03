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
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

/**
 * Deterministic projection of source-to-target lineage for a model (or cross-model view).
 *
 * <p>Models remain the source of truth; this snapshot is derived and may be published or used to
 * explain DDL.
 */
@Getter
@Setter
public class LineageSnapshot {

  private String id;
  private Date capturedAt;
  private String projectKey;
  private LineageLayer modelLayer = LineageLayer.DV;
  private String modelName;
  private String modelFilename;
  private String modelHash;
  private String catalogConnection;
  private String resourceGroup;
  private final List<TableLineage> tables = new ArrayList<>();

  public LineageSnapshot addTable(TableLineage table) {
    if (table != null) {
      tables.add(table);
    }
    return this;
  }

  public Optional<TableLineage> findTableByLogicalName(String logicalName) {
    if (logicalName == null) {
      return Optional.empty();
    }
    return tables.stream()
        .filter(t -> logicalName.equalsIgnoreCase(t.getLogicalName()))
        .findFirst();
  }

  public Optional<TableLineage> findTableByPhysicalName(String physicalTableName) {
    if (physicalTableName == null) {
      return Optional.empty();
    }
    return tables.stream()
        .filter(t -> physicalTableName.equalsIgnoreCase(t.getPhysicalTableName()))
        .findFirst();
  }
}
