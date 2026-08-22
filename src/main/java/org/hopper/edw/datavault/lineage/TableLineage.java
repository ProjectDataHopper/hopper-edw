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
package org.hopper.edw.datavault.lineage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

/** Table-level lineage for one DV/BV/DM target object. */
@Getter
@Setter
public class TableLineage {

  private LineageLayer layer = LineageLayer.DV;
  private String logicalName;
  private String physicalTableName;
  private String tableType;
  private String modelName;
  private String modelFilename;
  private String targetDatabaseMetaName;
  private String schemaName;
  private String description;
  private final List<LineageReason> reasons = new ArrayList<>();
  private final List<TableSourceRef> sources = new ArrayList<>();
  private final List<FieldLineage> fields = new ArrayList<>();

  public TableLineage addReason(LineageReason reason) {
    if (reason != null) {
      reasons.add(reason);
    }
    return this;
  }

  public TableLineage addSource(TableSourceRef source) {
    if (source != null) {
      sources.add(source);
    }
    return this;
  }

  public TableLineage addField(FieldLineage field) {
    if (field != null) {
      fields.add(field);
    }
    return this;
  }

  public Optional<FieldLineage> findField(String targetFieldName) {
    if (targetFieldName == null) {
      return Optional.empty();
    }
    return fields.stream()
        .filter(f -> targetFieldName.equalsIgnoreCase(f.getTargetFieldName()))
        .findFirst();
  }
}
