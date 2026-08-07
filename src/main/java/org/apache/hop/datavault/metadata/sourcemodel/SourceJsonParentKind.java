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
 * Kind of parent object a {@link SourceJson} extracts from on a source model canvas.
 *
 * <p>Distinguishes table / query / json source names so parent references stay unambiguous when
 * names could otherwise collide across object kinds.
 */
@Getter
public enum SourceJsonParentKind implements IEnumHasCodeAndDescription {
  TABLE("TABLE", BaseMessages.getString(SourceJsonParentKind.class, "SourceJsonParentKind.Table")),
  QUERY("QUERY", BaseMessages.getString(SourceJsonParentKind.class, "SourceJsonParentKind.Query")),
  JSON("JSON", BaseMessages.getString(SourceJsonParentKind.class, "SourceJsonParentKind.Json"));

  private final String code;
  private final String description;

  SourceJsonParentKind(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(SourceJsonParentKind.class);
  }

  public static SourceJsonParentKind lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        SourceJsonParentKind.class, description, TABLE);
  }

  public static SourceJsonParentKind lookupCode(String code) {
    return IEnumHasCode.lookupCode(SourceJsonParentKind.class, code, TABLE);
  }
}
