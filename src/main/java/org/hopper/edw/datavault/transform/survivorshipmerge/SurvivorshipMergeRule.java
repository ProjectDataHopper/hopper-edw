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
package org.hopper.edw.datavault.transform.survivorshipmerge;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** One source's rank and null policy for one output field. */
@Getter
@Setter
@NoArgsConstructor
public class SurvivorshipMergeRule {

  @HopMetadataProperty(key = "field")
  private String fieldName;

  @HopMetadataProperty(key = "source")
  private String sourceId;

  @HopMetadataProperty(key = "rank")
  private String rank;

  @HopMetadataProperty(key = "null_policy")
  private String nullPolicy;

  @HopMetadataProperty(key = "operation")
  private String operation;

  @HopMetadataProperty(key = "present_flag")
  private String presentFlagField;

  public SurvivorshipMergeRule(
      String fieldName, String sourceId, String rank, String nullPolicy, String operation) {
    this(fieldName, sourceId, rank, nullPolicy, operation, null);
  }

  public SurvivorshipMergeRule(
      String fieldName,
      String sourceId,
      String rank,
      String nullPolicy,
      String operation,
      String presentFlagField) {
    this.fieldName = fieldName;
    this.sourceId = sourceId;
    this.rank = rank;
    this.nullPolicy = nullPolicy;
    this.operation = operation;
    this.presentFlagField = presentFlagField;
  }
}
