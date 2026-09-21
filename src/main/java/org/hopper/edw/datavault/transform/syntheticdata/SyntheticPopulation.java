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

/** One id population for {@code COMBINE} cardinality. A count of 0 is skipped. */
@Getter
@Setter
public class SyntheticPopulation {

  /** {@code RANGE} (start + count) or {@code SAMPLE} (from..to, count unique ids). */
  @HopMetadataProperty(key = "kind")
  private String kind = "RANGE";

  @HopMetadataProperty(key = "start")
  private String start = "1";

  @HopMetadataProperty(key = "count")
  private String count = "0";

  @HopMetadataProperty(key = "from")
  private String from = "1";

  @HopMetadataProperty(key = "to")
  private String to = "1";

  public SyntheticPopulation() {}

  public SyntheticPopulation(SyntheticPopulation other) {
    this.kind = other.kind;
    this.start = other.start;
    this.count = other.count;
    this.from = other.from;
    this.to = other.to;
  }
}
