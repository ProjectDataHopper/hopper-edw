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
package org.hopper.edw.datavault.metadata.sourcemodel;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** SQL join type used on source relationships and source-query joins. */
@Getter
public enum SourceJoinType implements IEnumHasCodeAndDescription {
  INNER("INNER", BaseMessages.getString(SourceJoinType.class, "SourceJoinType.Inner")),
  LEFT("LEFT", BaseMessages.getString(SourceJoinType.class, "SourceJoinType.Left")),
  RIGHT("RIGHT", BaseMessages.getString(SourceJoinType.class, "SourceJoinType.Right")),
  FULL("FULL", BaseMessages.getString(SourceJoinType.class, "SourceJoinType.Full"));

  private final String code;
  private final String description;

  SourceJoinType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(SourceJoinType.class);
  }

  public static SourceJoinType lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(SourceJoinType.class, description, LEFT);
  }

  public static SourceJoinType lookupCode(String code) {
    return IEnumHasCode.lookupCode(SourceJoinType.class, code, LEFT);
  }
}
