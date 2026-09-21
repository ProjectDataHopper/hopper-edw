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
 * Replaces one field, or forces a child count, when parent and child indexes match. Indexes are
 * 0-based. An empty index matches every row. {@code parentOp} and {@code childOp} are {@code =} or
 * {@code <=}.
 */
@Getter
@Setter
public class SyntheticOverride {

  @HopMetadataProperty(key = "parentIndex")
  private String parentIndex = "";

  @HopMetadataProperty(key = "parentOp")
  private String parentOp = "=";

  @HopMetadataProperty(key = "childIndex")
  private String childIndex = "";

  @HopMetadataProperty(key = "childOp")
  private String childOp = "=";

  @HopMetadataProperty(key = "field")
  private String field = "";

  @HopMetadataProperty(key = "generator")
  private String generator = "";

  @HopMetadataProperty(key = "arguments")
  private String arguments = "";

  /** Applied when the child index is empty. Replaces the random child count for that parent. */
  @HopMetadataProperty(key = "forceChildCount")
  private String forceChildCount = "";

  public SyntheticOverride() {}

  public SyntheticOverride(SyntheticOverride other) {
    this.parentIndex = other.parentIndex;
    this.parentOp = other.parentOp;
    this.childIndex = other.childIndex;
    this.childOp = other.childOp;
    this.field = other.field;
    this.generator = other.generator;
    this.arguments = other.arguments;
    this.forceChildCount = other.forceChildCount;
  }
}
