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
package org.hopper.edw.datavault.presentation.fact;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/** Grain-level star-join SQL plus result aliases for each selected editor field. */
@Getter
public final class FactCrosstabQuery {

  private final String sql;
  private final List<SelectedColumn> columns;

  public FactCrosstabQuery(String sql, List<SelectedColumn> columns) {
    this.sql = sql;
    this.columns = columns == null ? List.of() : List.copyOf(columns);
  }

  public String aliasFor(FactCrosstabField field) {
    if (field == null) {
      return null;
    }
    for (SelectedColumn column : columns) {
      if (column.field.sameSource(field)) {
        return column.resultAlias;
      }
    }
    return null;
  }

  public List<SelectedColumn> matching(List<FactCrosstabField> fields) {
    List<SelectedColumn> matched = new ArrayList<>();
    if (fields == null) {
      return matched;
    }
    for (FactCrosstabField field : fields) {
      for (SelectedColumn column : columns) {
        if (column.field.sameSource(field)) {
          matched.add(column);
          break;
        }
      }
    }
    return matched;
  }

  @Getter
  public static final class SelectedColumn {
    private final FactCrosstabField field;
    private final String resultAlias;

    public SelectedColumn(FactCrosstabField field, String resultAlias) {
      this.field = field;
      this.resultAlias = resultAlias;
    }
  }
}
