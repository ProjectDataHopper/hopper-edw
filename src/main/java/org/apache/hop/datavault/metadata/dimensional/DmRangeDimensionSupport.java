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
package org.apache.hop.datavault.metadata.dimensional;

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Resolves range dimension tables on a dimensional model canvas. */
public final class DmRangeDimensionSupport {

  private DmRangeDimensionSupport() {}

  public static DmRangeDimension resolveRangeDimension(
      DimensionalModel model, String tableName, IVariables variables) {
    if (model == null || Utils.isEmpty(tableName)) {
      return null;
    }
    String resolvedName = variables != null ? variables.resolve(tableName) : tableName;
    IDmTable table = model.findTable(resolvedName);
    if (table instanceof DmRangeDimension rangeDimension) {
      return rangeDimension;
    }
    return null;
  }

  public static String[] listRangeDimensionNames(DimensionalModel model, String excludeName) {
    if (model == null || model.getTables() == null) {
      return new String[0];
    }
    return model.getTables().stream()
        .filter(table -> table instanceof DmRangeDimension)
        .map(IDmTable::getName)
        .filter(name -> !Utils.isEmpty(name))
        .filter(name -> excludeName == null || !excludeName.equals(name))
        .toArray(String[]::new);
  }
}
