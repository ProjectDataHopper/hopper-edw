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
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * A catalog record definition referenced from inside a pipeline-backed source (typically a Record
 * Definition Input transform). Captured for lineage, drift, and impact — not required for load.
 */
@Getter
@Setter
@NoArgsConstructor
public class SourcePipelineCatalogSource {

  /** Pipeline transform that references the catalog (when imported from a .hpl). */
  @HopMetadataProperty private String transformName;

  @HopMetadataProperty private String catalogConnection;

  /** Fixed namespace when not selecting from input. */
  @HopMetadataProperty private String namespace;

  /** Fixed record definition name when not selecting from input. */
  @HopMetadataProperty private String recordName;

  /** True when the transform resolves namespace/name from stream fields. */
  @HopMetadataProperty private boolean selectFromInput;

  @HopMetadataProperty private String namespaceField;

  @HopMetadataProperty private String nameField;

  public SourcePipelineCatalogSource(String catalogConnection, String namespace, String recordName) {
    this.catalogConnection = catalogConnection;
    this.namespace = namespace;
    this.recordName = recordName;
  }

  public SourcePipelineCatalogSource(SourcePipelineCatalogSource other) {
    if (other == null) {
      return;
    }
    this.transformName = other.transformName;
    this.catalogConnection = other.catalogConnection;
    this.namespace = other.namespace;
    this.recordName = other.recordName;
    this.selectFromInput = other.selectFromInput;
    this.namespaceField = other.namespaceField;
    this.nameField = other.nameField;
  }
}
