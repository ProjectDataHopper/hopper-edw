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
package org.apache.hop.datavault.metadata.businessvault;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Explicit source table declaration for a SQL business table (dbt {@code source()} analogue).
 *
 * <p>Used by {@code {{ source('sourceName', 'tableName') }}} in the authoring SQL.
 */
@Getter
@Setter
@NoArgsConstructor
public class BvSqlSource {

  /** Logical source name used in {@code source('sourceName', ...)}. */
  @HopMetadataProperty private String sourceName;

  /** Optional Hop {@link org.apache.hop.core.database.DatabaseMeta} name for qualification. */
  @HopMetadataProperty private String databaseName;

  /** Optional schema. */
  @HopMetadataProperty private String schemaName;

  /** Physical table or view name. */
  @HopMetadataProperty private String tableName;

  @HopMetadataProperty private String description;

  public BvSqlSource(String sourceName, String tableName) {
    this.sourceName = sourceName;
    this.tableName = tableName;
  }

  public BvSqlSource(String sourceName, String databaseName, String schemaName, String tableName) {
    this.sourceName = sourceName;
    this.databaseName = databaseName;
    this.schemaName = schemaName;
    this.tableName = tableName;
  }
}
