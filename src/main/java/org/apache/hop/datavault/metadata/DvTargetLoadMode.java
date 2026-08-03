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

/** How generated DV update pipelines write new rows to target tables. */
@Getter
public enum DvTargetLoadMode implements IEnumHasCodeAndDescription {
  TABLE_OUTPUT(
      "TABLE_OUTPUT",
      BaseMessages.getString(DvTargetLoadMode.class, "DvTargetLoadMode.TableOutput")),
  NATIVE_BULK(
      "NATIVE_BULK", BaseMessages.getString(DvTargetLoadMode.class, "DvTargetLoadMode.NativeBulk")),
  STAGING_FILE(
      "STAGING_FILE",
      BaseMessages.getString(DvTargetLoadMode.class, "DvTargetLoadMode.StagingFile"));

  private final String code;
  private final String description;

  DvTargetLoadMode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DvTargetLoadMode.class);
  }

  public static DvTargetLoadMode lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DvTargetLoadMode.class, description, TABLE_OUTPUT);
  }

  public static DvTargetLoadMode lookupCode(String code) {
    return IEnumHasCode.lookupCode(DvTargetLoadMode.class, code, TABLE_OUTPUT);
  }
}
