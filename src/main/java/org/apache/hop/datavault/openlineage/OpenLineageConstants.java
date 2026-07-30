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

/** Shared OpenLineage constants for model lineage export and execution-map export. */
public final class OpenLineageConstants {

  public static final String PRODUCER = "https://github.com/mattcasters/hop-data-vault";

  public static final String DEFAULT_JOB_NAMESPACE = "hop-data-vault";

  public static final String SCHEMA_FACET_URL =
      "https://openlineage.io/spec/facets/1-1-1/SchemaDatasetFacet.json";

  public static final String COLUMN_LINEAGE_FACET_URL =
      "https://openlineage.io/spec/facets/1-2-0/ColumnLineageDatasetFacet.json";

  public static final String DATA_SOURCE_FACET_URL =
      "https://openlineage.io/spec/facets/1-0-0/DatasourceDatasetFacet.json";

  private OpenLineageConstants() {}
}
