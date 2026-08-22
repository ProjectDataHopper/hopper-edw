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
package org.hopper.edw.datavault.metadata.sourcemodel.profile;

import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationshipMultiplicity;

/** Pure mapping from relationship key metrics to crow's-foot multiplicities. */
public final class SourceRelationshipMultiplicityInference {

  private SourceRelationshipMultiplicityInference() {}

  /**
   * @param childNulls whether any child join keys are null (optional participation on child)
   * @param maxChildrenPerParent max number of child rows sharing one parent key (0 if unknown)
   * @param anyParentWithoutChild whether some parent keys have zero children
   * @param parentKeysUnique whether parent join keys are unique in the parent table
   */
  public static SourceRelationshipMultiplicity.MultiplicityPair fromMetrics(
      boolean childNulls,
      long maxChildrenPerParent,
      boolean anyParentWithoutChild,
      boolean parentKeysUnique) {
    boolean childMany = maxChildrenPerParent > 1;
    SourceRelationshipMultiplicity child;
    if (childMany) {
      child =
          childNulls
              ? SourceRelationshipMultiplicity.ZERO_OR_MANY
              : SourceRelationshipMultiplicity.ONE_OR_MANY;
    } else {
      child =
          childNulls
              ? SourceRelationshipMultiplicity.ZERO_OR_ONE
              : SourceRelationshipMultiplicity.ONE;
    }

    SourceRelationshipMultiplicity parent;
    if (!parentKeysUnique) {
      parent =
          anyParentWithoutChild
              ? SourceRelationshipMultiplicity.ZERO_OR_MANY
              : SourceRelationshipMultiplicity.ONE_OR_MANY;
    } else if (anyParentWithoutChild) {
      parent = SourceRelationshipMultiplicity.ZERO_OR_ONE;
    } else {
      parent = SourceRelationshipMultiplicity.ONE;
    }
    return new SourceRelationshipMultiplicity.MultiplicityPair(child, parent);
  }

  /** Weak heuristic when only sizes / PK flags are known. */
  public static SourceRelationshipMultiplicity.MultiplicityPair fromStatsOnly(
      boolean childFkLooksNonUnique, boolean parentLooksPrimaryKey) {
    SourceRelationshipMultiplicity child =
        childFkLooksNonUnique
            ? SourceRelationshipMultiplicity.ZERO_OR_MANY
            : SourceRelationshipMultiplicity.UNKNOWN;
    SourceRelationshipMultiplicity parent =
        parentLooksPrimaryKey
            ? SourceRelationshipMultiplicity.ONE
            : SourceRelationshipMultiplicity.UNKNOWN;
    return new SourceRelationshipMultiplicity.MultiplicityPair(child, parent);
  }
}
