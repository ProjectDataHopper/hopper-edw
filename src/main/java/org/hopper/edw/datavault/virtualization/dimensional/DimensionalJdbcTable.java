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
package org.hopper.edw.datavault.virtualization.dimensional;

import java.util.List;

/** Logical dimensional table and the physical warehouse table it reads. */
public record DimensionalJdbcTable(
    String logicalName,
    String physicalSchema,
    String physicalTable,
    String remarks,
    List<DimensionalJdbcColumn> columns) {

  public DimensionalJdbcColumn column(int ordinal) {
    if (columns == null || ordinal < 0 || ordinal >= columns.size()) {
      return null;
    }
    return columns.get(ordinal);
  }
}
