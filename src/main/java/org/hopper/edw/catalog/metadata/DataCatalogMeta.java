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
package org.hopper.edw.catalog.metadata;

import lombok.Getter;
import lombok.Setter;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.spi.IDataCatalog;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Hop metadata connection to an external data catalog. Plugin-specific options are stored in the
 * nested {@link IDataCatalog} implementation (similar to {@code DatabaseMeta} / {@code IDatabase}).
 */
@HopMetadata(
    key = "data-catalog",
    name = "i18n::DataCatalogMeta.name",
    description = "i18n::DataCatalogMeta.description",
    image = "data-catalog.svg",
    documentationUrl = "/metadata-types/data-catalog.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
public class DataCatalogMeta extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;

  @HopMetadataProperty(key = "catalog")
  private IDataCatalog catalog = new FileDataCatalog();

  @HopMetadataProperty private boolean enabled = true;

  public DataCatalogMeta() {
    super();
  }

  public DataCatalogMeta(String name) {
    super(name);
  }

  public IDataCatalog getCatalogOrDefault() {
    if (catalog == null) {
      catalog = new FileDataCatalog();
    }
    return catalog;
  }
}
