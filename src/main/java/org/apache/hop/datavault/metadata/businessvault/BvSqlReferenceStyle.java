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
package org.apache.hop.datavault.metadata.businessvault;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/**
 * How SQL templates identify model and source dependencies in a Business Vault business table.
 *
 * <p>MVP supports dbt-core style {@code {{ ref(...) }}} and {@code {{ source(...) }}} only.
 */
@Getter
public enum BvSqlReferenceStyle implements IEnumHasCodeAndDescription {
  DBT("DBT", BaseMessages.getString(BvSqlReferenceStyle.class, "BvSqlReferenceStyle.Dbt"));

  private final String code;
  private final String description;

  BvSqlReferenceStyle(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(BvSqlReferenceStyle.class);
  }

  public static BvSqlReferenceStyle lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        BvSqlReferenceStyle.class, description, DBT);
  }

  public static BvSqlReferenceStyle lookupCode(String code) {
    return IEnumHasCode.lookupCode(BvSqlReferenceStyle.class, code, DBT);
  }
}
