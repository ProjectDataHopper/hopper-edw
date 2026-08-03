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
package org.apache.hop.datavault.openlineage;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/** Outcome of an OpenLineage export run. */
@Getter
public class OpenLineageExportResult {

  private final String exportRunId;
  private int eventCount;
  private int filesWritten;
  private int httpPosted;
  private int httpFailed;
  private String summaryPath;
  private final List<String> warnings = new ArrayList<>();
  private final List<String> errors = new ArrayList<>();
  private final List<String> writtenPaths = new ArrayList<>();

  public OpenLineageExportResult(String exportRunId) {
    this.exportRunId = exportRunId;
  }

  public void addWarning(String message) {
    if (message != null && !message.isBlank()) {
      warnings.add(message);
    }
  }

  public void addError(String message) {
    if (message != null && !message.isBlank()) {
      errors.add(message);
    }
  }

  public void incrementEventCount() {
    eventCount++;
  }

  public void incrementFilesWritten() {
    filesWritten++;
  }

  public void incrementHttpPosted() {
    httpPosted++;
  }

  public void incrementHttpFailed() {
    httpFailed++;
  }

  public void addWrittenPath(String path) {
    if (path != null && !path.isBlank()) {
      writtenPaths.add(path);
    }
  }

  public void setSummaryPath(String summaryPath) {
    this.summaryPath = summaryPath;
  }

  public boolean hasErrors() {
    return !errors.isEmpty() || httpFailed > 0;
  }
}
