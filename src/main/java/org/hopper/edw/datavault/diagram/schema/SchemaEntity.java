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
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Named table/entity in a schema diagram. */
@Getter
@Setter
@NoArgsConstructor
public class SchemaEntity {
  private String name;
  private String stereotype;
  private String grain;
  private final List<SchemaField> fields = new ArrayList<>();

  public SchemaEntity(String name, String stereotype) {
    this.name = name;
    this.stereotype = stereotype;
  }

  public SchemaEntity addField(SchemaField field) {
    if (field != null && field.getName() != null && !field.getName().isBlank()) {
      fields.add(field);
    }
    return this;
  }
}
