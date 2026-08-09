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

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.gui.Point;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.jspecify.annotations.NonNull;

/**
 * Pipeline-backed feed on a {@link SourceModel} canvas.
 *
 * <p>Points at a Hop pipeline ({@code .hpl}) and an output transform, with a declared field layout
 * that becomes the catalog contract when published as {@code DV_SOURCE} type {@code PIPELINE}.
 *
 * <p>Catalog connection for <em>publishing</em> this feed comes from {@link
 * SourceModelConfiguration} (same as tables/queries/JSON). Optional {@link #catalogSources} lists
 * zero or more catalog record definitions discovered inside the pipeline (Record Definition Input
 * transforms) for lineage and impact analysis.
 */
@Getter
@Setter
public class SourcePipeline extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;

  /** Path to the source pipeline ({@code .hpl}); variables allowed. */
  @HopMetadataProperty private String pipelineFilename;

  /** Transform whose outgoing fields define the feed grain. */
  @HopMetadataProperty private String outputTransformName;

  /** Optional MetaInject / local run configuration name (default local when empty). */
  @HopMetadataProperty private String pipelineRunConfiguration;

  /**
   * Optional catalog source name when this pipeline feed is published (same role as {@link
   * SourceTable#getCatalogSourceName()}). Empty = use {@link #getName()}.
   */
  @HopMetadataProperty private String catalogSourceName;

  /**
   * Catalog record definitions referenced inside the pipeline (typically imported from Record
   * Definition Input transforms). May be empty.
   */
  @HopMetadataProperty(key = "catalog-source", groupKey = "catalog-sources")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourcePipelineCatalogSource> catalogSources = new ArrayList<>();

  @HopMetadataProperty(key = "column", groupKey = "columns")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceColumn> fields = new ArrayList<>();

  @HopMetadataProperty(inline = true)
  private Point location = new Point(50, 50);

  private boolean selected;

  public SourcePipeline() {}

  public SourcePipeline(String name) {
    setName(name);
  }

  public @NonNull List<SourceColumn> getFields() {
    if (fields == null) {
      fields = new ArrayList<>();
    }
    return fields;
  }

  public void setFields(List<SourceColumn> fields) {
    this.fields = fields != null ? fields : new ArrayList<>();
  }

  public @NonNull List<SourcePipelineCatalogSource> getCatalogSources() {
    if (catalogSources == null) {
      catalogSources = new ArrayList<>();
    }
    return catalogSources;
  }

  public void setCatalogSources(List<SourcePipelineCatalogSource> catalogSources) {
    this.catalogSources = catalogSources != null ? catalogSources : new ArrayList<>();
  }

  public SourceColumn findField(String fieldName) {
    if (fieldName == null || fieldName.isBlank()) {
      return null;
    }
    for (SourceColumn field : getFields()) {
      if (field != null && fieldName.equals(field.getName())) {
        return field;
      }
    }
    return null;
  }

  public List<SourceColumn> primaryKeyFields() {
    List<SourceColumn> keys = new ArrayList<>();
    for (SourceColumn field : getFields()) {
      if (field != null && field.isPrimaryKey()) {
        keys.add(field);
      }
    }
    keys.sort((a, b) -> Integer.compare(a.getPrimaryKeyPosition(), b.getPrimaryKeyPosition()));
    return keys;
  }

  /** Name used when publishing this pipeline feed to the catalog. */
  public String resolveCatalogSourceName() {
    if (catalogSourceName != null && !catalogSourceName.isBlank()) {
      return catalogSourceName.trim();
    }
    return getName() != null ? getName().trim() : "";
  }

  public Point getLocation() {
    if (location == null) {
      location = new Point(50, 50);
    }
    return location;
  }

  public void setLocation(Point p) {
    if (p == null) {
      this.location = new Point(50, 50);
    } else {
      this.location = new Point(p.x, p.y);
    }
  }

  public void setLocation(int x, int y) {
    if (location == null) {
      location = new Point(x, y);
    } else {
      location.x = x;
      location.y = y;
    }
  }
}
