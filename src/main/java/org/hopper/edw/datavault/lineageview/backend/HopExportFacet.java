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
package org.hopper.edw.datavault.lineageview.backend;

import lombok.Builder;
import lombok.Value;

/** Parsed {@code hop_export} run facet. */
@Value
@Builder(toBuilder = true)
public class HopExportFacet {
  /** {@code DV} / {@code BV} / {@code DM} ({@code LineageLayer.name()}). */
  String modelLayer;

  String modelName;
  String exportRunId;
  String modelFilename;
  String tableType;
  String logicalName;
  String projectKey;
  String resourceGroup;
  String catalogConnection;
  String physicalTableName;
  String targetDatabase;
}
