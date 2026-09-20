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
package org.hopper.edw.semantic.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Join from a fact (or child) entity to another entity. */
@Getter
@Setter
@NoArgsConstructor
public class SemanticRelationship {

  @HopMetadataProperty private String name;

  @HopMetadataProperty private String fromEntity;

  @HopMetadataProperty private String toEntity;

  @HopMetadataProperty(storeWithCode = true)
  private SemanticJoinType joinType = SemanticJoinType.INNER;

  /** Physical join column on {@link #fromEntity} (typically the fact FK). */
  @HopMetadataProperty private String fromField;

  /** Physical join column on {@link #toEntity} (typically the dimension key). */
  @HopMetadataProperty private String toField;

  /** Role-playing label (order date vs ship date). */
  @HopMetadataProperty private String roleName;

  public SemanticJoinType resolveJoinType() {
    return joinType != null ? joinType : SemanticJoinType.INNER;
  }
}
