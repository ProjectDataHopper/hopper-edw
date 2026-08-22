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

import lombok.Getter;
import lombok.Setter;

/** One structural change derived from a DDL statement. */
@Getter
@Setter
public class DdlDelta {

  private DdlDeltaType type = DdlDeltaType.OTHER;
  private String tableName;
  private String columnName;
  private String rawSql;
  private String summary;

  public DdlDelta() {}

  public DdlDelta(DdlDeltaType type, String tableName, String columnName, String rawSql) {
    this.type = type;
    this.tableName = tableName;
    this.columnName = columnName;
    this.rawSql = rawSql;
  }
}
