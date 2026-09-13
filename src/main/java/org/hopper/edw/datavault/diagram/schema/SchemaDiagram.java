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
package org.hopper.edw.datavault.diagram.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.util.Utils;

/** Format-agnostic ER/class schema used by PlantUML, Mermaid, and Draw.io exporters. */
@Getter
@Setter
@NoArgsConstructor
public class SchemaDiagram {
  private String title;
  private final Map<String, SchemaEntity> entitiesByName = new LinkedHashMap<>();
  private final List<SchemaRelation> relations = new ArrayList<>();

  public SchemaEntity addEntity(SchemaEntity entity) {
    if (entity == null || Utils.isEmpty(entity.getName())) {
      return entity;
    }
    entitiesByName.putIfAbsent(entity.getName(), entity);
    return entitiesByName.get(entity.getName());
  }

  public void addRelation(SchemaRelation relation) {
    if (relation == null
        || Utils.isEmpty(relation.getFromName())
        || Utils.isEmpty(relation.getToName())) {
      return;
    }
    relations.add(relation);
  }

  public List<SchemaEntity> getEntities() {
    return new ArrayList<>(entitiesByName.values());
  }

  public SchemaEntity findEntity(String name) {
    return entitiesByName.get(name);
  }
}
