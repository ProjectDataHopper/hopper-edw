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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/**
 * Resolves parent hub business-key <em>values</em> for hub satellite loads.
 *
 * <p>The hub defines vault business keys and, for composite keys, ordered source parts. Satellites
 * do not store hub BK columns; they only map feed columns that supply hash inputs (one field per
 * multipartite vault BK, or N fields per composite vault BK). Hash rules (delimiter, suffix,
 * casing) come from {@link DataVaultConfiguration} via the hub relationship.
 *
 * <p>{@link DvSatellite#getParentKeySourceFields()} is an ordered list of source columns aligned to
 * the hub's <em>hash-input part order</em> (total part count). Empty list defaults to hub vault
 * names for multipartite keys, or to the hub's source parts for the satellite's record source when
 * the hub is composite.
 */
public final class DvSatelliteParentKeySupport {

  private static final Class<?> PKG = DvSatelliteParentKeySupport.class;

  private DvSatelliteParentKeySupport() {}

  /**
   * Runtime projection: stream field name used as a hash input (after optional rename) and column
   * on the satellite feed. Not stored as a name→name map on the satellite.
   *
   * <p>For multipartite vault BKs, {@code businessKeyName} is the vault column name. For composite
   * hub BKs, each part uses the source field name as the stream/hash field (no composed BK column).
   */
  public static final class ParentKeyField {
    private final String businessKeyName;
    private final String sourceFieldName;
    private final String vaultBusinessKeyName;
    private final boolean compositePart;

    public ParentKeyField(String businessKeyName, String sourceFieldName) {
      this(businessKeyName, sourceFieldName, businessKeyName, false);
    }

    public ParentKeyField(
        String businessKeyName,
        String sourceFieldName,
        String vaultBusinessKeyName,
        boolean compositePart) {
      this.businessKeyName = businessKeyName;
      this.sourceFieldName = sourceFieldName;
      this.vaultBusinessKeyName = vaultBusinessKeyName;
      this.compositePart = compositePart;
    }

    /** Stream field name after optional rename; also DvHashKey input name. */
    public String getBusinessKeyName() {
      return businessKeyName;
    }

    public String getSourceFieldName() {
      return sourceFieldName;
    }

    /** Hub vault BK column name this part belongs to (for type lookup / messages). */
    public String getVaultBusinessKeyName() {
      return vaultBusinessKeyName != null ? vaultBusinessKeyName : businessKeyName;
    }

    public boolean isCompositePart() {
      return compositePart;
    }

    public boolean requiresRename() {
      return !Objects.equals(businessKeyName, sourceFieldName);
    }
  }

  /**
   * Resolve parent key fields for a hub satellite.
   *
   * @param hub parent hub (logical BK names, composite parts, order)
   * @param satellite hub satellite (optional ordered {@code parentKeySourceFields})
   * @param variables variable space (may be null)
   */
  public static List<ParentKeyField> resolveParentKeyFields(
      DvHub hub, DvSatellite satellite, IVariables variables) throws HopException {
    if (hub == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSatelliteParentKeySupport.Error.HubRequired"));
    }

    String satSourceName =
        satellite != null ? resolve(satellite.getRecordSourceName(), variables) : null;
    List<HashInputSlot> slots = buildHashInputSlots(hub, satSourceName, variables);
    if (slots.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvSatelliteParentKeySupport.Error.NoBusinessKeys", hub.getName()));
    }

    List<String> explicitSourceFields = nonEmptySourceFields(satellite, variables);
    if (!explicitSourceFields.isEmpty() && explicitSourceFields.size() != slots.size()) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DvSatelliteParentKeySupport.Error.SourceFieldCountMismatch",
              satellite != null ? satellite.getName() : "?",
              hub.getName(),
              String.valueOf(explicitSourceFields.size()),
              String.valueOf(slots.size())));
    }

    List<ParentKeyField> result = new ArrayList<>();
    for (int i = 0; i < slots.size(); i++) {
      HashInputSlot slot = slots.get(i);
      String sourceFieldName;
      if (!explicitSourceFields.isEmpty()) {
        sourceFieldName = explicitSourceFields.get(i);
      } else if (!Utils.isEmpty(slot.defaultSourceFieldName())) {
        sourceFieldName = slot.defaultSourceFieldName();
      } else {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "DvSatelliteParentKeySupport.Error.CompositePartsRequireMapping",
                satellite != null ? satellite.getName() : "?",
                hub.getName(),
                slot.vaultBusinessKeyName()));
      }

      // Multipartite: stream name = vault BK. Composite part: stream name = source field (hash
      // over parts; no composed burger_bk on the sat stream).
      String streamFieldName = slot.compositePart() ? sourceFieldName : slot.streamFieldName();
      if (Utils.isEmpty(streamFieldName)) {
        streamFieldName = sourceFieldName;
      }
      result.add(
          new ParentKeyField(
              streamFieldName, sourceFieldName, slot.vaultBusinessKeyName(), slot.compositePart()));
    }
    return result;
  }

  public static List<String> resolveSourceFieldNames(
      DvHub hub, DvSatellite satellite, IVariables variables) throws HopException {
    List<String> names = new ArrayList<>();
    for (ParentKeyField field : resolveParentKeyFields(hub, satellite, variables)) {
      names.add(field.getSourceFieldName());
    }
    return names;
  }

  public static List<String> resolveBusinessKeyNames(
      DvHub hub, DvSatellite satellite, IVariables variables) throws HopException {
    List<String> names = new ArrayList<>();
    for (ParentKeyField field : resolveParentKeyFields(hub, satellite, variables)) {
      names.add(field.getBusinessKeyName());
    }
    return names;
  }

  public static List<String> quotedSelectExpressions(
      DvHub hub, DvSatellite satellite, DatabaseMeta databaseMeta, IVariables variables)
      throws HopException {
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSatelliteParentKeySupport.Error.DatabaseMetaRequired"));
    }
    List<String> expressions = new ArrayList<>();
    for (ParentKeyField field : resolveParentKeyFields(hub, satellite, variables)) {
      String quotedSource = databaseMeta.quoteField(field.getSourceFieldName());
      if (field.requiresRename()) {
        expressions.add(
            quotedSource + " AS " + databaseMeta.quoteField(field.getBusinessKeyName()));
      } else {
        expressions.add(quotedSource);
      }
    }
    return expressions;
  }

  /**
   * Default parent-key source field list for dialogs: multipartite → vault BK names; composite →
   * hub source parts (preferring mappings for {@code recordSourceName} when provided).
   */
  public static List<String> defaultSourceFieldsFromHub(DvHub hub, IVariables variables) {
    return defaultSourceFieldsFromHub(hub, null, variables);
  }

  public static List<String> defaultSourceFieldsFromHub(
      DvHub hub, String preferredRecordSourceName, IVariables variables) {
    List<String> fields = new ArrayList<>();
    if (hub == null) {
      return fields;
    }
    for (HashInputSlot slot :
        buildHashInputSlots(hub, resolve(preferredRecordSourceName, variables), variables)) {
      if (!Utils.isEmpty(slot.defaultSourceFieldName())) {
        fields.add(slot.defaultSourceFieldName());
      } else if (!Utils.isEmpty(slot.streamFieldName())) {
        fields.add(slot.streamFieldName());
      }
    }
    return fields;
  }

  /** Human-readable hub hash-input order (dialog helper text). */
  public static String formatHubKeyOrder(DvHub hub, IVariables variables) {
    List<String> names = defaultSourceFieldsFromHub(hub, variables);
    return names.isEmpty() ? "" : String.join(", ", names);
  }

  /** Expected number of satellite parent-key source fields (hash-input part count on the hub). */
  public static int expectedParentKeySourceFieldCount(DvHub hub) {
    return DvBusinessKeyPartSupport.totalHashInputPartCount(hub);
  }

  private record HashInputSlot(
      String streamFieldName,
      String defaultSourceFieldName,
      String vaultBusinessKeyName,
      boolean compositePart) {}

  private static List<HashInputSlot> buildHashInputSlots(
      DvHub hub, String satRecordSourceName, IVariables variables) {
    List<HashInputSlot> slots = new ArrayList<>();
    for (DvBusinessKeyPartSupport.VaultBusinessKey vaultKey :
        DvBusinessKeyPartSupport.resolveVaultBusinessKeys(hub)) {
      String vaultName = resolve(vaultKey.vaultFieldName(), variables);
      if (!vaultKey.composite()) {
        slots.add(new HashInputSlot(vaultName, vaultName, vaultName, false));
        continue;
      }

      List<String> hubParts =
          DvBusinessKeyPartSupport.resolveSourcePartsForHubSource(
              hub, vaultKey.vaultFieldName(), satRecordSourceName, variables);
      if (hubParts.isEmpty() && vaultKey.definition() != null) {
        hubParts = resolveList(variables, vaultKey.definition().resolveSourceParts());
      }
      int partCount = Math.max(vaultKey.partCount(), 1);
      for (int i = 0; i < partCount; i++) {
        String defaultPart = i < hubParts.size() ? hubParts.get(i) : null;
        // Stream name filled at resolve time from source when composite.
        slots.add(new HashInputSlot(defaultPart, defaultPart, vaultName, true));
      }
    }
    return slots;
  }

  private static List<String> nonEmptySourceFields(DvSatellite satellite, IVariables variables) {
    List<String> result = new ArrayList<>();
    if (satellite == null || satellite.getParentKeySourceFields() == null) {
      return result;
    }
    for (String field : satellite.getParentKeySourceFields()) {
      if (Utils.isEmpty(field)) {
        continue;
      }
      result.add(resolve(field.trim(), variables));
    }
    return result;
  }

  private static List<String> resolveList(IVariables variables, List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    List<String> resolved = new ArrayList<>(values.size());
    for (String value : values) {
      resolved.add(resolve(value, variables));
    }
    return resolved;
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
