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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationshipMultiplicity;

/** Outcome of profiling a source relationship. */
@Getter
@Setter
public class SourceRelationshipProfileResult {

  public enum Confidence {
    HIGH,
    MEDIUM,
    LOW
  }

  private SourceRelationshipMultiplicity childMultiplicity = SourceRelationshipMultiplicity.UNKNOWN;
  private SourceRelationshipMultiplicity parentMultiplicity =
      SourceRelationshipMultiplicity.UNKNOWN;
  private SourceRelationshipProfileStrategy strategyUsed;
  private Confidence confidence = Confidence.LOW;
  private long childRowEstimate = -1;
  private long parentRowEstimate = -1;
  private boolean childRowCountExact;
  private boolean parentRowCountExact;
  private long maxChildrenPerParent = -1;
  private long childNullKeyCount = -1;
  private long childOrphanCount = -1;
  private long parentWithoutChildren = -1;
  private final List<String> messages = new ArrayList<>();

  public void addMessage(String message) {
    if (message != null && !message.isBlank()) {
      messages.add(message);
    }
  }
}
