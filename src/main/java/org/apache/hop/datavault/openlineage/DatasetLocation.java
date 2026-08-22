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
package org.apache.hop.datavault.openlineage;

import lombok.Builder;
import lombok.Getter;

/**
 * Physical location metadata for an OpenLineage dataset (DB table, file feed, Iceberg table, …).
 *
 * <p>Identity (namespace/name) is separate; this describes <em>where</em> the data lives.
 */
@Getter
@Builder(toBuilder = true)
public class DatasetLocation {

  @Builder.Default private final DatasetLocationKind kind = DatasetLocationKind.UNKNOWN;

  /** Hop DatabaseMeta name when kind is DATABASE. */
  private final String connectionName;

  private final String schemaName;
  private final String tableName;

  /** File-based sources (CSV / Parquet). */
  private final String folder;

  private final String includeFileMask;
  private final String excludeFileMask;
  private final Boolean includeSubfolders;

  /** Iceberg sources. */
  private final String catalogUri;

  private final String warehouse;
  private final String icebergNamespace;
  private final String icebergTableName;
  private final String branch;
  private final String snapshotId;

  /**
   * URI for OpenLineage {@code dataSource.uri} (JDBC URL without secrets, file URI, Iceberg
   * location, …).
   */
  private final String uri;

  /** Human label for dataSource.name (connection name, CSV, ICEBERG, …). */
  private final String dataSourceName;

  /**
   * Catalog record key for source feeds, typically {@code hop/{project}/sources/{feed}} (last-slash
   * splits namespace vs name).
   */
  private final String catalogKey;

  /** Hop Data Catalog connection name used to open the record. */
  private final String catalogConnection;

  public boolean hasStructuredFields() {
    return (kind != null && kind != DatasetLocationKind.UNKNOWN)
        || notEmpty(connectionName)
        || notEmpty(schemaName)
        || notEmpty(tableName)
        || notEmpty(folder)
        || notEmpty(includeFileMask)
        || notEmpty(catalogUri)
        || notEmpty(warehouse)
        || notEmpty(icebergNamespace)
        || notEmpty(icebergTableName)
        || notEmpty(uri)
        || notEmpty(dataSourceName)
        || notEmpty(catalogKey)
        || notEmpty(catalogConnection);
  }

  private static boolean notEmpty(String value) {
    return value != null && !value.isBlank();
  }
}
