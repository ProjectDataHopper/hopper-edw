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
package org.apache.hop.catalog.transform.tablemetadata;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One discovered table column ready for pipeline output / catalog field mapping. */
@Getter
@Setter
@NoArgsConstructor
public class DatabaseTableMetadataColumn {

  private String databaseConnection;
  private String schemaName;
  private String tableName;
  private int fieldPosition;
  private String fieldName;
  private String fieldType;
  private Long fieldLength;
  private Long fieldPrecision;
  private long primaryKeyPosition;
  private String sourceDataType;
  private String fkConstraintName;
  private Long fkPosition;
  private String fkReferencedSchema;
  private String fkReferencedTable;
  private String fkReferencedColumn;
}
