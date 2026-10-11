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
package org.hopper.edw.datavault.opsanalytics;

import java.util.List;

/**
 * Physical OPS table as a source-model card. {@link #name()} is the canvas name. {@link
 * #physicalTable()} is the table loaded from the OPS schema.
 */
public record OpsAnalyticsTable(
    String name, String physicalTable, boolean executionInfo, List<OpsAnalyticsColumn> columns) {

  public OpsAnalyticsTable {
    columns = columns == null ? List.of() : List.copyOf(columns);
  }
}
