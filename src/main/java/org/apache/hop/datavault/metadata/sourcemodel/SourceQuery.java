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
 * Named multi-table projection that becomes a composed feed for hubs, links, and satellites.
 *
 * <p>Published to the data catalog as a composite {@code DV_SOURCE} (later PR). Generation produces
 * either single-connection SQL or a Merge Join pipeline depending on {@link #generationMode}.
 */
@Getter
@Setter
public class SourceQuery extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;

  /** Driving table name (FROM clause). */
  @HopMetadataProperty private String drivingTableName;

  @HopMetadataProperty(key = "join", groupKey = "joins")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceQueryJoin> joins = new ArrayList<>();

  @HopMetadataProperty(key = "column", groupKey = "columns")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SourceQueryColumn> columns = new ArrayList<>();

  /** Optional SQL WHERE fragment (no leading WHERE keyword). */
  @HopMetadataProperty private String whereClause;

  @HopMetadataProperty(storeWithCode = true)
  private SourceQueryGenerationMode generationMode = SourceQueryGenerationMode.AUTO;

  /** Catalog feed name used on last publish (may equal {@link #getName()}). */
  @HopMetadataProperty private String publishedCatalogName;

  /** Optional canvas location when the query is shown as a node. */
  @HopMetadataProperty(inline = true)
  private Point location = new Point(50, 50);

  private boolean selected;

  public SourceQuery() {}

  public SourceQuery(String name) {
    setName(name);
  }

  public @NonNull List<SourceQueryJoin> getJoins() {
    if (joins == null) {
      joins = new ArrayList<>();
    }
    return joins;
  }

  public void setJoins(List<SourceQueryJoin> joins) {
    this.joins = joins != null ? joins : new ArrayList<>();
  }

  public @NonNull List<SourceQueryColumn> getColumns() {
    if (columns == null) {
      columns = new ArrayList<>();
    }
    return columns;
  }

  public void setColumns(List<SourceQueryColumn> columns) {
    this.columns = columns != null ? columns : new ArrayList<>();
  }

  public SourceQueryGenerationMode resolveGenerationMode() {
    return generationMode != null ? generationMode : SourceQueryGenerationMode.AUTO;
  }

  public Point getLocation() {
    if (location == null) {
      location = new Point(50, 50);
    }
    return location;
  }
}
