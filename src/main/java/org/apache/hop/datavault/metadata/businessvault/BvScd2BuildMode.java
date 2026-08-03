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

/** Controls how a Business Vault SCD2 build pipeline reads history and writes the target table. */
@Getter
public enum BvScd2BuildMode implements IEnumHasCodeAndDescription {
  FULL_REBUILD(
      "FULL_REBUILD", BaseMessages.getString(BvScd2BuildMode.class, "BvScd2BuildMode.FullRebuild")),
  INCREMENTAL(
      "INCREMENTAL", BaseMessages.getString(BvScd2BuildMode.class, "BvScd2BuildMode.Incremental"));

  private final String code;
  private final String description;

  BvScd2BuildMode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(BvScd2BuildMode.class);
  }

  public static BvScd2BuildMode lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        BvScd2BuildMode.class, description, FULL_REBUILD);
  }

  public static BvScd2BuildMode lookupCode(String code) {
    return IEnumHasCode.lookupCode(BvScd2BuildMode.class, code, FULL_REBUILD);
  }
}
