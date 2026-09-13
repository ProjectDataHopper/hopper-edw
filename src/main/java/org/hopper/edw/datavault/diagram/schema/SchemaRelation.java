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

/** Directed relationship between two {@link SchemaEntity} names. */
@Getter
@Setter
@NoArgsConstructor
public class SchemaRelation {
  private String fromName;
  private String toName;
  private SchemaCardinality fromCardinality = SchemaCardinality.ONE_OR_MANY;
  private SchemaCardinality toCardinality = SchemaCardinality.ONE;
  private String label;

  public SchemaRelation(String fromName, String toName, String label) {
    this.fromName = fromName;
    this.toName = toName;
    this.label = label;
  }
}
