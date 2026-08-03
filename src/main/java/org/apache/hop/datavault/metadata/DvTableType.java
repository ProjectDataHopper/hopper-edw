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
package org.apache.hop.datavault.metadata;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/**
 * Enumeration of the core Data Vault 2.0 table types. Used to identify whether a table definition
 * is a Hub, Link or Satellite.
 */
@Getter
public enum DvTableType implements IEnumHasCodeAndDescription {
  HUB("HUB", BaseMessages.getString(DvTableType.class, "DvTableType.Hub")),
  SATELLITE("SATELLITE", BaseMessages.getString(DvTableType.class, "DvTableType.Satellite")),
  LINK("LINK", BaseMessages.getString(DvTableType.class, "DvTableType.Link")),
  TABLE_REFERENCE(
      "TABLE_REFERENCE", BaseMessages.getString(DvTableType.class, "DvTableType.TableReference"));

  private final String code;
  private final String description;

  DvTableType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DvTableType.class);
  }

  public static DvTableType lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(DvTableType.class, description, HUB);
  }

  public static DvTableType lookupCode(String code) {
    return IEnumHasCode.lookupCode(DvTableType.class, code, HUB);
  }
}
