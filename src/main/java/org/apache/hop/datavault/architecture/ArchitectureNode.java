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
package org.apache.hop.datavault.architecture;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** Vertex in a derived architecture graph. */
@Getter
@Setter
public class ArchitectureNode {

  private String id;
  private String name;
  private ArchitectureNodeKind kind = ArchitectureNodeKind.OTHER;
  private ArchitectureLayer layer = ArchitectureLayer.OTHER;

  /** Subtype detail (HUB, LINK, DV_UPDATE, plugin id, …). */
  private String detailType;

  private String path;
  private String description;

  /**
   * Optional freeform layout coordinates (from ELK). When set, Draw.io freeform mode places the
   * node at these pixels; null means swimlane auto-layout.
   */
  private Integer x;

  private Integer y;
  private Integer width;
  private Integer height;
  private final Map<String, String> properties = new LinkedHashMap<>();

  public ArchitectureNode() {}

  public ArchitectureNode(String id, String name, ArchitectureNodeKind kind) {
    this.id = id;
    this.name = name;
    this.kind = kind;
  }

  public ArchitectureNode property(String key, String value) {
    if (key != null && value != null) {
      properties.put(key, value);
    }
    return this;
  }

  /** True when freeform (ELK) coordinates are available for export. */
  public boolean hasLayoutCoordinates() {
    return x != null && y != null;
  }

  public ArchitectureNode layoutBox(int x, int y, int width, int height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    return this;
  }
}
