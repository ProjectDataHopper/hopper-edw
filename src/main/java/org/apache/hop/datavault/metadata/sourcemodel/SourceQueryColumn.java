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
package org.apache.hop.datavault.metadata.sourcemodel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Projected column in a {@link SourceQuery} (table.column → output alias). */
@Getter
@Setter
@NoArgsConstructor
public class SourceQueryColumn {

  @HopMetadataProperty private String tableName;
  @HopMetadataProperty private String columnName;

  /** Output field name for the composed feed; defaults to {@link #columnName} when empty. */
  @HopMetadataProperty private String alias;

  /**
   * Logical primary-key position of this projected field for the composed feed (1-based). Zero
   * means the column is not part of the feed grain. Published to catalog {@code SourceField}
   * primary-key positions for hub key import and mapping reuse.
   */
  @HopMetadataProperty private int primaryKeyPosition;

  public SourceQueryColumn(String tableName, String columnName) {
    this.tableName = tableName;
    this.columnName = columnName;
  }

  public SourceQueryColumn(String tableName, String columnName, String alias) {
    this.tableName = tableName;
    this.columnName = columnName;
    this.alias = alias;
  }

  public String resolveAlias() {
    if (!Utils.isEmpty(alias)) {
      return alias.trim();
    }
    return columnName != null ? columnName.trim() : "";
  }

  public boolean isPrimaryKey() {
    return primaryKeyPosition > 0;
  }
}
