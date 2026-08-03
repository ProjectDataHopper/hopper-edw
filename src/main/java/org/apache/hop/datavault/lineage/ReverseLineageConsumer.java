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

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** One downstream consumer of an upstream source field (table or field level path). */
@Getter
@Builder
public final class ReverseLineageConsumer {

  private final LineageLayer layer;
  private final String modelName;
  private final String modelFilename;
  private final String tableName;
  private final String tableType;
  private final String targetField;
  private final String transform;
  private final List<String> reasonCodes;

  /** Human-readable path, e.g. {@code E2E.demo.segment → sat_customer_demo.segment}. */
  private final String pathSummary;

  /** Hop count from the queried source (1 = direct, 2 = via intermediate DV table, …). */
  private final int hopCount;

  public List<String> getReasonCodes() {
    return reasonCodes != null ? List.copyOf(reasonCodes) : List.of();
  }
}
