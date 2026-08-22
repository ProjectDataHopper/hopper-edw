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
package org.apache.hop.datavault.lineage;

import lombok.Getter;
import lombok.Setter;

/** Upstream feed or parent table that contributes to a target table. */
@Getter
@Setter
public class TableSourceRef {

  private TableSourceKind kind;
  private String name;
  private String catalogKey;
  private String physicalRef;
  private TableSourceRole role = TableSourceRole.OTHER;

  public TableSourceRef() {}

  public TableSourceRef(TableSourceKind kind, String name, TableSourceRole role) {
    this.kind = kind;
    this.name = name;
    this.role = role;
  }
}
