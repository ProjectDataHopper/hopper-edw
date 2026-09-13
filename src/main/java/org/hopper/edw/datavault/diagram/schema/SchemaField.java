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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Column on a {@link SchemaEntity}. */
@Getter
@Setter
@NoArgsConstructor
public class SchemaField {
  private String name;
  private String type;
  private boolean primaryKey;
  private boolean foreignKey;

  public SchemaField(String name) {
    this.name = name;
  }

  public SchemaField pk() {
    this.primaryKey = true;
    return this;
  }

  public SchemaField fk() {
    this.foreignKey = true;
    return this;
  }

  public SchemaField type(String type) {
    this.type = type;
    return this;
  }
}
