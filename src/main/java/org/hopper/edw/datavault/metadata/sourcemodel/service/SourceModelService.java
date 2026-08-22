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
package org.hopper.edw.datavault.metadata.sourcemodel.service;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataCategory;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Named Hop Server endpoint that maps a public {@link #getName() model name} to a server-side
 * {@code .hsm} path. Clients reference only the metadata name (never the filesystem path).
 *
 * <p>Used by the {@code /hop/sourceModelData} servlet and the thin {@code jdbc:hop-hsm://…} driver.
 */
@HopMetadata(
    key = "source-model-service",
    name = "i18n::SourceModelService.name",
    description = "i18n::SourceModelService.description",
    image = "source-model.svg",
    category = HopMetadataCategory.SERVERS,
    documentationUrl = "/metadata-types/source-model-service.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
public class SourceModelService extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private boolean enabled = true;

  /** Server-side VFS path to the {@code .hsm} file (variables allowed). Never sent to clients. */
  @HopMetadataProperty private String modelFilename;

  @HopMetadataProperty private String description;

  /**
   * Default max rows when the client does not specify {@code rowLimit} ({@code 0} = no default).
   */
  @HopMetadataProperty private int defaultRowLimit = 10_000;

  /**
   * Hard cap on rows returned per request ({@code 0} = no cap). Always applied for safety when
   * greater than zero.
   */
  @HopMetadataProperty private int maxRowLimit = 100_000;

  /** When false, tables/columns metadata actions are rejected. */
  @HopMetadataProperty private boolean allowSchemaMetadata = true;

  public SourceModelService() {
    super();
  }

  public SourceModelService(String name) {
    super(name);
  }

  /**
   * Resolves the effective row limit: client request → service default → hard max.
   *
   * @param requested client {@code rowLimit} ({@code <=0} means use default)
   */
  public int resolveRowLimit(int requested) {
    int limit = requested > 0 ? requested : Math.max(0, defaultRowLimit);
    if (maxRowLimit > 0) {
      if (limit <= 0) {
        limit = maxRowLimit;
      } else {
        limit = Math.min(limit, maxRowLimit);
      }
    }
    return Math.max(0, limit);
  }
}
