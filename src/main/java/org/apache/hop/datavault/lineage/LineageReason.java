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
package org.apache.hop.datavault.lineage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/** Structured explanation for a table or field contribution. */
@Getter
public final class LineageReason {

  private final LineageReasonCode code;
  private final String message;
  private final LineageConfidence confidence;
  private final Map<String, String> evidence;

  public LineageReason(
      LineageReasonCode code,
      String message,
      LineageConfidence confidence,
      Map<String, String> evidence) {
    this.code = code;
    this.message = message;
    this.confidence = confidence != null ? confidence : LineageConfidence.DERIVED;
    if (evidence == null || evidence.isEmpty()) {
      this.evidence = Map.of();
    } else {
      this.evidence = Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
    }
  }

  public static LineageReason of(
      LineageReasonCode code, String message, LineageConfidence confidence) {
    return new LineageReason(code, message, confidence, Map.of());
  }

  public static LineageReason of(
      LineageReasonCode code,
      String message,
      LineageConfidence confidence,
      String evidenceKey,
      String evidenceValue) {
    Map<String, String> evidence = new LinkedHashMap<>();
    if (evidenceKey != null) {
      evidence.put(evidenceKey, evidenceValue != null ? evidenceValue : "");
    }
    return new LineageReason(code, message, confidence, evidence);
  }
}
