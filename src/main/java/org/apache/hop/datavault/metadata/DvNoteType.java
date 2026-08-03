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
 * Semantic note type for annotations on a Data Vault model canvas. The type determines the fixed
 * visual style (colors, border, icon); users do not pick arbitrary fonts or colors.
 */
@Getter
public enum DvNoteType implements IEnumHasCodeAndDescription {
  GENERAL("GENERAL", BaseMessages.getString(DvNoteType.class, "DvNoteType.General")),
  IMPORTANT("IMPORTANT", BaseMessages.getString(DvNoteType.class, "DvNoteType.Important")),
  WARNING("WARNING", BaseMessages.getString(DvNoteType.class, "DvNoteType.Warning")),
  INFORMATION("INFORMATION", BaseMessages.getString(DvNoteType.class, "DvNoteType.Information"));

  private final String code;
  private final String description;

  DvNoteType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DvNoteType.class);
  }

  public static DvNoteType lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(DvNoteType.class, description, GENERAL);
  }

  public static DvNoteType lookupCode(String code) {
    return IEnumHasCode.lookupCode(DvNoteType.class, code, GENERAL);
  }
}
