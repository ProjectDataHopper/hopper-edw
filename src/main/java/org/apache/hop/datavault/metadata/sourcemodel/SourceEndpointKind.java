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
package org.apache.hop.datavault.metadata.sourcemodel;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/**
 * Kind of canvas node that can participate in a {@link SourceRelationship} or as a Source JSON
 * parent: physical table, multi-table query, or JSON extraction.
 */
@Getter
public enum SourceEndpointKind implements IEnumHasCodeAndDescription {
  TABLE("TABLE", BaseMessages.getString(SourceEndpointKind.class, "SourceEndpointKind.Table")),
  QUERY("QUERY", BaseMessages.getString(SourceEndpointKind.class, "SourceEndpointKind.Query")),
  JSON("JSON", BaseMessages.getString(SourceEndpointKind.class, "SourceEndpointKind.Json"));

  private final String code;
  private final String description;

  SourceEndpointKind(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(SourceEndpointKind.class);
  }

  public static SourceEndpointKind lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        SourceEndpointKind.class, description, TABLE);
  }

  public static SourceEndpointKind lookupCode(String code) {
    return IEnumHasCode.lookupCode(SourceEndpointKind.class, code, TABLE);
  }
}
