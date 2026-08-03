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
package org.apache.hop.quality.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.apache.hop.quality.profile.DataProfileSnapshot;

/**
 * Ephemeral result of measuring data quality. Rule findings never imply infrastructure failure;
 * disposition policies decide workflow outcome.
 */
@Getter
public class DataQualityReport {

  private final String runId;
  private final Instant measuredAt;
  private final QualityLifecycle lifecycle;
  private final List<String> subjectKeys = new ArrayList<>();
  private final List<DataQualityFinding> findings = new ArrayList<>();
  private final Map<String, DataProfileSnapshot> profilesBySubject = new LinkedHashMap<>();
  private final List<String> infraErrors = new ArrayList<>();

  public DataQualityReport(QualityLifecycle lifecycle) {
    this.runId = UUID.randomUUID().toString();
    this.measuredAt = Instant.now();
    this.lifecycle = lifecycle != null ? lifecycle : QualityLifecycle.AD_HOC;
  }

  public void addSubjectKey(String key) {
    if (key != null && !key.isBlank() && !subjectKeys.contains(key)) {
      subjectKeys.add(key);
    }
  }

  public void addFinding(DataQualityFinding finding) {
    if (finding != null) {
      findings.add(finding);
    }
  }

  public void putProfile(String subjectKey, DataProfileSnapshot profile) {
    if (subjectKey != null && profile != null) {
      profilesBySubject.put(subjectKey, profile);
    }
  }

  public void addInfraError(String message) {
    if (message != null && !message.isBlank()) {
      infraErrors.add(message);
    }
  }

  public boolean hasBlockingFindings() {
    return findings.stream().anyMatch(f -> f.getSeverity() == QualitySeverity.BLOCKING);
  }

  public boolean hasWarnings() {
    return findings.stream().anyMatch(f -> f.getSeverity() == QualitySeverity.WARNING);
  }

  public boolean hasInfraErrors() {
    return !infraErrors.isEmpty();
  }

  public int getFindingCount() {
    return findings.size();
  }

  public long countBySeverity(QualitySeverity severity) {
    return findings.stream().filter(f -> f.getSeverity() == severity).count();
  }
}
