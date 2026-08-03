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
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.jspecify.annotations.NonNull;

/**
 * FK-style relationship edge between two {@link SourceTable} nodes on a source model canvas.
 *
 * <p>Child columns reference parent columns (parallel lists). Imported FKs default to {@link
 * SourceJoinType#LEFT} for lookup-friendly joins. Each end carries a crow's-foot multiplicity
 * ({@link SourceRelationshipMultiplicity}).
 */
@Getter
@Setter
public class SourceRelationship extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;
  @HopMetadataProperty private String childTableName;
  @HopMetadataProperty private String parentTableName;

  @HopMetadataProperty(key = "child_column", groupKey = "child_columns")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<String> childColumns = new ArrayList<>();

  @HopMetadataProperty(key = "parent_column", groupKey = "parent_columns")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<String> parentColumns = new ArrayList<>();

  @HopMetadataProperty(storeWithCode = true)
  private SourceJoinType defaultJoinType = SourceJoinType.LEFT;

  /**
   * Legacy free-text cardinality (e.g. {@code N:1}). Prefer {@link #childMultiplicity} / {@link
   * #parentMultiplicity}. Kept for backward-compatible load of older {@code .hsm} files.
   */
  @HopMetadataProperty private String cardinality;

  @HopMetadataProperty(storeWithCode = true)
  private SourceRelationshipMultiplicity childMultiplicity = SourceRelationshipMultiplicity.UNKNOWN;

  @HopMetadataProperty(storeWithCode = true)
  private SourceRelationshipMultiplicity parentMultiplicity = SourceRelationshipMultiplicity.ONE;

  public SourceRelationship() {}

  public SourceRelationship(String name) {
    setName(name);
  }

  public @NonNull List<String> getChildColumns() {
    if (childColumns == null) {
      childColumns = new ArrayList<>();
    }
    return childColumns;
  }

  public void setChildColumns(List<String> childColumns) {
    this.childColumns = childColumns != null ? new ArrayList<>(childColumns) : new ArrayList<>();
  }

  public @NonNull List<String> getParentColumns() {
    if (parentColumns == null) {
      parentColumns = new ArrayList<>();
    }
    return parentColumns;
  }

  public void setParentColumns(List<String> parentColumns) {
    this.parentColumns = parentColumns != null ? new ArrayList<>(parentColumns) : new ArrayList<>();
  }

  public SourceJoinType resolveDefaultJoinType() {
    return defaultJoinType != null ? defaultJoinType : SourceJoinType.LEFT;
  }

  public SourceRelationshipMultiplicity resolveChildMultiplicity() {
    migrateLegacyCardinalityIfNeeded();
    return childMultiplicity != null ? childMultiplicity : SourceRelationshipMultiplicity.UNKNOWN;
  }

  public SourceRelationshipMultiplicity resolveParentMultiplicity() {
    migrateLegacyCardinalityIfNeeded();
    return parentMultiplicity != null ? parentMultiplicity : SourceRelationshipMultiplicity.ONE;
  }

  /**
   * When only legacy {@link #cardinality} is set, map it into the two multiplicity fields. Applied
   * when child multiplicity is still {@link SourceRelationshipMultiplicity#UNKNOWN} (typical for
   * older {@code .hsm} files that only stored free-text cardinality).
   */
  public void migrateLegacyCardinalityIfNeeded() {
    if (childMultiplicity == null) {
      childMultiplicity = SourceRelationshipMultiplicity.UNKNOWN;
    }
    if (parentMultiplicity == null) {
      parentMultiplicity = SourceRelationshipMultiplicity.ONE;
    }
    if (Utils.isEmpty(cardinality) || childMultiplicity != SourceRelationshipMultiplicity.UNKNOWN) {
      return;
    }
    SourceRelationshipMultiplicity.MultiplicityPair pair =
        SourceRelationshipMultiplicity.parseLegacyCardinality(cardinality);
    if (pair == null) {
      return;
    }
    childMultiplicity = pair.child();
    parentMultiplicity = pair.parent();
  }

  public String compactMultiplicityLabel() {
    return resolveChildMultiplicity().compactLabel()
        + " \u2014 "
        + resolveParentMultiplicity().compactLabel();
  }

  public boolean isValid() {
    return !Utils.isEmpty(childTableName)
        && !Utils.isEmpty(parentTableName)
        && !getChildColumns().isEmpty()
        && getChildColumns().size() == getParentColumns().size();
  }
}
