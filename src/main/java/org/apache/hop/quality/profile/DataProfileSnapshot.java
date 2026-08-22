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
package org.apache.hop.quality.profile;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.quality.model.QualityEvaluationMode;
import org.apache.hop.quality.model.QualityLifecycle;

/** Dataset-level profile facts produced by measure (ephemeral in Phase 1). */
@Getter
@Setter
public class DataProfileSnapshot {

  private String subjectKey;
  private Instant capturedAt = Instant.now();
  private QualityLifecycle lifecycle = QualityLifecycle.AD_HOC;
  private QualityEvaluationMode evaluationMode = QualityEvaluationMode.SAMPLE;
  private long rowCount;
  private boolean rowCountExact = true;
  private final Map<String, FieldProfile> fields = new LinkedHashMap<>();

  public FieldProfile field(String name) {
    return fields.computeIfAbsent(name, FieldProfile::new);
  }

  public FieldProfile findField(String name) {
    if (name == null) {
      return null;
    }
    FieldProfile direct = fields.get(name);
    if (direct != null) {
      return direct;
    }
    for (Map.Entry<String, FieldProfile> entry : fields.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
        return entry.getValue();
      }
    }
    return null;
  }
}
