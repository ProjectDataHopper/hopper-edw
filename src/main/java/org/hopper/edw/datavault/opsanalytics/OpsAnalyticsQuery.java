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
 * Conformed business key that is not its own OPS table. Each {@link Branch} is one SELECT DISTINCT
 * arm of the generated union.
 */
public record OpsAnalyticsQuery(String name, List<OpsAnalyticsColumn> keys, List<Branch> branches) {

  public OpsAnalyticsQuery {
    keys = keys == null ? List.of() : List.copyOf(keys);
    branches = branches == null ? List.of() : List.copyOf(branches);
  }

  /** One physical table contributing rows to a conformed key. */
  public record Branch(String physicalTable, List<String> columns) {
    public Branch {
      columns = columns == null ? List.of() : List.copyOf(columns);
    }
  }
}
