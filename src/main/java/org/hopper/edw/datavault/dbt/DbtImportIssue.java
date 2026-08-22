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
package org.hopper.edw.datavault.dbt;

public record DbtImportIssue(
    DbtImportSeverity severity, String modelName, String code, String message) {

  public static DbtImportIssue warn(String modelName, String code, String message) {
    return new DbtImportIssue(DbtImportSeverity.WARN, modelName, code, message);
  }

  public static DbtImportIssue error(String modelName, String code, String message) {
    return new DbtImportIssue(DbtImportSeverity.ERROR, modelName, code, message);
  }

  public static DbtImportIssue info(String modelName, String code, String message) {
    return new DbtImportIssue(DbtImportSeverity.INFO, modelName, code, message);
  }

  public String summary() {
    return code + ": " + message;
  }
}
