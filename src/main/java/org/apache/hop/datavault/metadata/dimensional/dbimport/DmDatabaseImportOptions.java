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
package org.apache.hop.datavault.metadata.dimensional.dbimport;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionScdType;

/** Options for importing database tables into a dimensional model. */
@Getter
@Setter
public class DmDatabaseImportOptions {

  private String databaseName;
  private String schemaName = "";
  private DmDimensionScdType defaultDimensionScdType = DmDimensionScdType.TYPE1;
  private int layoutStartX = 64;
  private int layoutStartY = 64;
  private int layoutColumnWidth = 180;
  private int layoutRowHeight = 140;

  public static DmDatabaseImportOptions defaults() {
    return new DmDatabaseImportOptions();
  }
}
