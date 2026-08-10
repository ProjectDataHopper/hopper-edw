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
package org.apache.hop.catalog.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Optional link from a record definition to a physical database table.
 *
 * <p>When this record owns the column layout of that table (published hubs/sats/dims, ops tables,
 * {@code PHYSICAL_TABLE}), {@link #fields} is the authoritative layout. For {@code DV_SOURCE}
 * records, {@link #fields} is unused — the source contract lives on {@link DvSourceRecord#fields}.
 */
@Getter
@Setter
@NoArgsConstructor
public class PhysicalTableRef {

  @HopMetadataProperty private String databaseMetaName;

  @HopMetadataProperty private String schemaName;

  @HopMetadataProperty private String tableName;

  /**
   * Column layout for this physical table when the parent record definition owns the layout (vault
   * targets, dimensional tables, generic physical tables). Empty for location-only refs.
   */
  @HopMetadataProperty private List<CatalogSourceField> fields = new ArrayList<>();
}
