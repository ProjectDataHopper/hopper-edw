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
package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/**
 * Enumeration of Data Vault table types on a model canvas: Hub, Link, Satellite, Reference (code /
 * catalog tables without hash keys), and Linked table (cross-model pointer / hub alias).
 */
@Getter
public enum DvTableType implements IEnumHasCodeAndDescription {
  HUB("HUB", BaseMessages.getString(DvTableType.class, "DvTableType.Hub")),
  SATELLITE("SATELLITE", BaseMessages.getString(DvTableType.class, "DvTableType.Satellite")),
  LINK("LINK", BaseMessages.getString(DvTableType.class, "DvTableType.Link")),
  /**
   * Physical reference / code table in the vault (natural keys + attributes + load metadata). Not
   * the same as {@link #LINKED_TABLE}.
   */
  REFERENCE("REFERENCE", BaseMessages.getString(DvTableType.class, "DvTableType.Reference")),
  /**
   * Read-only canvas card pointing at a Hub, Link, Satellite, or Reference table (cross-model), or
   * a same-model hub alias for role-playing. Not a loadable physical object by itself.
   */
  LINKED_TABLE(
      "LINKED_TABLE", BaseMessages.getString(DvTableType.class, "DvTableType.LinkedTable")),
  /**
   * Legacy name for {@link #LINKED_TABLE}. Kept so Hop can {@code Enum.valueOf} old {@code .hdv}
   * files that still store {@code <tableType>TABLE_REFERENCE</tableType>}. Callers should treat
   * this as {@link #LINKED_TABLE} (see {@link #normalize(DvTableType)} / {@link
   * #parsePersisted(String)}).
   *
   * @deprecated Use {@link #LINKED_TABLE}. New writes always use {@code LINKED_TABLE}.
   */
  @Deprecated
  TABLE_REFERENCE(
      "TABLE_REFERENCE", BaseMessages.getString(DvTableType.class, "DvTableType.LinkedTable"));

  /** Legacy persisted code written before the linked-table rename. */
  public static final String LEGACY_TABLE_REFERENCE_CODE = "TABLE_REFERENCE";

  private final String code;
  private final String description;

  DvTableType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    // Exclude deprecated TABLE_REFERENCE so UI dropdowns only show Linked table once.
    DvTableType[] all = values();
    List<String> descriptions = new ArrayList<>(all.length);
    for (DvTableType type : all) {
      if (type == TABLE_REFERENCE) {
        continue;
      }
      descriptions.add(type.getDescription());
    }
    return descriptions.toArray(new String[0]);
  }

  public static DvTableType lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(DvTableType.class, description, HUB);
  }

  public static DvTableType lookupCode(String code) {
    return normalize(IEnumHasCode.lookupCode(DvTableType.class, code, HUB));
  }

  /**
   * Maps legacy {@link #TABLE_REFERENCE} to {@link #LINKED_TABLE}; other values pass through
   * (including {@code null}).
   */
  public static DvTableType normalize(DvTableType type) {
    if (type == TABLE_REFERENCE) {
      return LINKED_TABLE;
    }
    return type;
  }

  /**
   * Resolves a type from persisted XML/JSON. Maps legacy {@code TABLE_REFERENCE} to {@link
   * #LINKED_TABLE}.
   */
  public static DvTableType parsePersisted(String raw) {
    if (Utils.isEmpty(raw)) {
      return HUB;
    }
    String code = raw.trim();
    if (LEGACY_TABLE_REFERENCE_CODE.equals(code) || LINKED_TABLE.name().equals(code)) {
      return LINKED_TABLE;
    }
    return lookupCode(code);
  }

  /** True for the canvas pointer/alias type (including legacy name checks). */
  public static boolean isLinkedTableCode(String raw) {
    if (Utils.isEmpty(raw)) {
      return false;
    }
    String code = raw.trim();
    return LINKED_TABLE.name().equals(code) || LEGACY_TABLE_REFERENCE_CODE.equals(code);
  }

  /** True when this type is a linked table (current or legacy enum constant). */
  public boolean isLinkedTable() {
    return this == LINKED_TABLE || this == TABLE_REFERENCE;
  }
}
