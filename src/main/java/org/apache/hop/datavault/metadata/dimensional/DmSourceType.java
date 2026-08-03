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

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** Staging source type for a dimensional table load pipeline. */
@Getter
public enum DmSourceType implements IEnumHasCodeAndDescription {
  SQL("SQL", BaseMessages.getString(DmSourceType.class, "DmSourceType.Sql")),
  PIPELINE("PIPELINE", BaseMessages.getString(DmSourceType.class, "DmSourceType.Pipeline")),
  RECORD_DEFINITION(
      "RECORD_DEFINITION",
      BaseMessages.getString(DmSourceType.class, "DmSourceType.RecordDefinition")),
  FACT_TABLE("FACT_TABLE", BaseMessages.getString(DmSourceType.class, "DmSourceType.FactTable"));

  private final String code;
  private final String description;

  DmSourceType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DmSourceType.class);
  }

  public static DmSourceType lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(DmSourceType.class, description, SQL);
  }

  public static DmSourceType lookupCode(String code) {
    return IEnumHasCode.lookupCode(DmSourceType.class, code, SQL);
  }
}
