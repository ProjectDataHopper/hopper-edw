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
package org.hopper.edw.datavault.transform.syntheticdata;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * One node in an optional nested XML document. {@code nodeType} is {@code GROUP}, {@code ELEMENT},
 * {@code WRAPPER}, or {@code ROW}. Attributes are {@code attr:field} pairs separated by commas.
 * Rows must already be ordered by the group fields.
 */
@Getter
@Setter
public class SyntheticXmlNode {

  @HopMetadataProperty(key = "id")
  private String id = "";

  @HopMetadataProperty(key = "parentId")
  private String parentId = "";

  @HopMetadataProperty(key = "nodeType")
  private String nodeType = "ROW";

  @HopMetadataProperty(key = "element")
  private String element = "";

  @HopMetadataProperty(key = "groupField")
  private String groupField = "";

  @HopMetadataProperty(key = "attributes")
  private String attributes = "";

  public SyntheticXmlNode() {}

  public SyntheticXmlNode(SyntheticXmlNode other) {
    this.id = other.id;
    this.parentId = other.parentId;
    this.nodeType = other.nodeType;
    this.element = other.element;
    this.groupField = other.groupField;
    this.attributes = other.attributes;
  }
}
