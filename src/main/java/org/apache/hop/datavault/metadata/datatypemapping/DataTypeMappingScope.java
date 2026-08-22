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
package org.apache.hop.datavault.metadata.datatypemapping;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Optional applicability hints for a {@link DataTypeMappingMeta} profile. Empty scope means the
 * profile is always applicable when explicitly attached. Scope is advisory for bulk-suggest and
 * mismatch warnings.
 */
@Getter
@Setter
@NoArgsConstructor
public class DataTypeMappingScope {

  /**
   * Source kind codes (e.g. DATABASE, CSV, PARQUET, ICEBERG, JSON, PIPELINE, COMPOSITE). Empty =
   * any kind.
   */
  @HopMetadataProperty(key = "sourceKind", groupKey = "sourceKinds")
  private List<String> sourceKinds = new ArrayList<>();

  /** Glob/regex against database connection name. */
  @HopMetadataProperty private String databaseNamePattern;

  @HopMetadataProperty private String schemaNamePattern;

  /** File path, Iceberg table path, Kafka topic, etc. */
  @HopMetadataProperty private String pathPattern;

  @HopMetadataProperty private String catalogNamespacePattern;

  public List<String> getSourceKinds() {
    if (sourceKinds == null) {
      sourceKinds = new ArrayList<>();
    }
    return sourceKinds;
  }

  public boolean isEmpty() {
    return getSourceKinds().isEmpty()
        && Utils.isEmpty(databaseNamePattern)
        && Utils.isEmpty(schemaNamePattern)
        && Utils.isEmpty(pathPattern)
        && Utils.isEmpty(catalogNamespacePattern);
  }
}
