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
 *
 */

package org.apache.hop.datavault.openlineage;

import lombok.Builder;
import lombok.Getter;

/** Options controlling model-derived OpenLineage export. */
@Getter
@Builder
public class OpenLineageExportOptions {

  @Builder.Default private final boolean includeDv = true;
  @Builder.Default private final boolean includeBv = true;
  @Builder.Default private final boolean includeDm = true;
  @Builder.Default private final boolean includeColumnLineage = true;
  @Builder.Default private final boolean includeOperationalMetrics = false;
  @Builder.Default private final OpenLineageDestinationMode destinationMode = OpenLineageDestinationMode.FILE;

  private final String outputFolder;
  private final String httpUrl;
  private final String httpApiKeyHeader;
  private final String httpApiKey;
  private final String jobNamespace;
  /** When set, overrides Hop connection / catalog dataset namespaces for all datasets. */
  private final String datasetNamespace;

  private final String opsDatabase;
  private final String opsSchema;

  @Builder.Default private final boolean failOnHttpError = true;
  @Builder.Default private final int timeoutMs = 30_000;
}
