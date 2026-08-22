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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/**
 * Resolves link hub source business key fields, including implicit hub-name fallback and composite
 * multi-part mappings.
 *
 * <p>Links do not store hub business-key columns. Resolved source fields are hash inputs only
 * (ordered parts for composite hub BKs). Composition/hash rules come from the hub + configuration.
 */
public final class DvLinkHubSourceKeyFieldSupport {

  private static final Class<?> PKG = DvLinkHubSourceKeyFieldSupport.class;

  private DvLinkHubSourceKeyFieldSupport() {}

  /**
   * One hash-input field on the link feed for a hub vault BK (composite BKs yield multiple rows
   * with the same {@code businessKeyField}).
   */
  public static final class ResolvedBusinessKeySource {
    private final String businessKeyField;
    private final String sourceFieldName;
    private final boolean compositePart;
    private final int partIndex;

    public ResolvedBusinessKeySource(String businessKeyField, String sourceFieldName) {
      this(businessKeyField, sourceFieldName, false, 0);
    }

    public ResolvedBusinessKeySource(
        String businessKeyField, String sourceFieldName, boolean compositePart, int partIndex) {
      this.businessKeyField = businessKeyField;
      this.sourceFieldName = sourceFieldName;
      this.compositePart = compositePart;
      this.partIndex = partIndex;
    }

    public String getBusinessKeyField() {
      return businessKeyField;
    }

    public String getSourceFieldName() {
      return sourceFieldName;
    }

    public boolean isCompositePart() {
      return compositePart;
    }

    public int getPartIndex() {
      return partIndex;
    }
  }

  public static DvLink.HubSourceKeyField findHubSourceKeyField(
      DvLink.DvLinkHubSource linkHubSource, String hubName) throws HopException {
    DvLink.HubSourceKeyField field = findHubSourceKeyFieldOrNull(linkHubSource, hubName);
    if (field == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DvLinkHubSourceKeyFieldSupport.Error.MissingHubMapping",
              hubName,
              linkHubSource.getSourceName()));
    }
    return field;
  }

  public static DvLink.HubSourceKeyField findHubSourceKeyFieldOrNull(
      DvLink.DvLinkHubSource linkHubSource, String hubName) {
    if (linkHubSource == null
        || Utils.isEmpty(hubName)
        || linkHubSource.getHubSourceKeyFields() == null) {
      return null;
    }
    for (DvLink.HubSourceKeyField hubSourceKeyField : linkHubSource.getHubSourceKeyFields()) {
      if (hubSourceKeyField != null && hubName.equals(hubSourceKeyField.getHubName())) {
        return hubSourceKeyField;
      }
    }
    return null;
  }

  public static List<ResolvedBusinessKeySource> resolveBusinessKeySources(
      DvHub hub, DvLink.HubSourceKeyField hubSourceKeyField, IVariables variables) {
    List<ResolvedBusinessKeySource> resolved = new ArrayList<>();
    if (hub == null || hub.getBusinessKeys() == null) {
      return resolved;
    }

    Map<String, BusinessKeySource> explicitByBusinessKey = new LinkedHashMap<>();
    if (hubSourceKeyField != null && hubSourceKeyField.getSourceBusinessKeyFields() != null) {
      for (BusinessKeySource businessKeySource : hubSourceKeyField.getSourceBusinessKeyFields()) {
        if (businessKeySource == null || Utils.isEmpty(businessKeySource.getBusinessKeyField())) {
          continue;
        }
        explicitByBusinessKey.putIfAbsent(
            businessKeySource.getBusinessKeyField(), businessKeySource);
      }
    }

    for (BusinessKey businessKey : hub.getDistinctBusinessKeys()) {
      if (businessKey == null || Utils.isEmpty(businessKey.getName())) {
        continue;
      }
      String businessKeyName = resolve(variables, businessKey.getName());
      BusinessKeySource explicit = explicitByBusinessKey.get(businessKey.getName());
      if (explicit == null) {
        // Also try resolved name key
        explicit = explicitByBusinessKey.get(businessKeyName);
      }

      if (businessKey.isComposite()) {
        List<String> parts =
            explicit != null ? resolveList(variables, explicit.resolveSourceParts()) : List.of();
        if (parts.isEmpty()) {
          // Fall back to hub definition parts (first-seen mapping) for dialog/load defaults
          parts = resolveList(variables, businessKey.resolveSourceParts());
        }
        if (parts.isEmpty()) {
          // Degenerate: treat vault name as single source field
          resolved.add(new ResolvedBusinessKeySource(businessKeyName, businessKeyName, true, 0));
        } else {
          for (int i = 0; i < parts.size(); i++) {
            if (!Utils.isEmpty(parts.get(i))) {
              resolved.add(new ResolvedBusinessKeySource(businessKeyName, parts.get(i), true, i));
            }
          }
        }
      } else {
        String sourceFieldName;
        if (explicit != null) {
          List<String> parts = resolveList(variables, explicit.resolveSourceParts());
          sourceFieldName = parts.isEmpty() ? businessKeyName : parts.get(0);
        } else {
          sourceFieldName = businessKeyName;
        }
        if (!Utils.isEmpty(sourceFieldName)) {
          resolved.add(new ResolvedBusinessKeySource(businessKeyName, sourceFieldName));
        }
      }
    }
    return resolved;
  }

  public static List<String> resolveSourceFieldNames(
      DvHub hub, DvLink.HubSourceKeyField hubSourceKeyField, IVariables variables) {
    List<String> sourceFieldNames = new ArrayList<>();
    for (ResolvedBusinessKeySource resolved :
        resolveBusinessKeySources(hub, hubSourceKeyField, variables)) {
      sourceFieldNames.add(resolved.getSourceFieldName());
    }
    return sourceFieldNames;
  }

  public static List<String> resolveSourceFieldNames(
      DvLink.DvLinkHubSource linkHubSource, String hubName, DvHub hub, IVariables variables)
      throws HopException {
    DvLink.HubSourceKeyField hubSourceKeyField = findHubSourceKeyField(linkHubSource, hubName);
    List<String> sourceFieldNames = resolveSourceFieldNames(hub, hubSourceKeyField, variables);
    if (sourceFieldNames.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DvLinkHubSourceKeyFieldSupport.Error.NoResolvableSourceFields",
              hubName,
              linkHubSource.getSourceName()));
    }
    return sourceFieldNames;
  }

  /**
   * Validates that each composite hub BK has the expected number of mapped source parts on the link
   * feed. Returns empty when OK; otherwise human-readable error messages.
   */
  public static List<String> findCompositePartCountMismatches(
      DvHub hub, DvLink.HubSourceKeyField hubSourceKeyField, IVariables variables) {
    List<String> errors = new ArrayList<>();
    if (hub == null) {
      return errors;
    }
    Map<String, BusinessKeySource> explicitByBusinessKey = new LinkedHashMap<>();
    if (hubSourceKeyField != null && hubSourceKeyField.getSourceBusinessKeyFields() != null) {
      for (BusinessKeySource businessKeySource : hubSourceKeyField.getSourceBusinessKeyFields()) {
        if (businessKeySource == null || Utils.isEmpty(businessKeySource.getBusinessKeyField())) {
          continue;
        }
        explicitByBusinessKey.putIfAbsent(
            businessKeySource.getBusinessKeyField(), businessKeySource);
      }
    }
    for (DvBusinessKeyPartSupport.VaultBusinessKey vaultKey :
        DvBusinessKeyPartSupport.resolveVaultBusinessKeys(hub)) {
      if (!vaultKey.composite()) {
        continue;
      }
      BusinessKeySource explicit = explicitByBusinessKey.get(vaultKey.vaultFieldName());
      if (explicit == null) {
        continue; // fallback parts used; existence checked elsewhere
      }
      int mapped = explicit.sourcePartCount();
      int expected = vaultKey.partCount();
      if (mapped > 0 && mapped != expected) {
        errors.add(
            "Composite hub business key '"
                + vaultKey.vaultFieldName()
                + "' expects "
                + expected
                + " source field(s) on the link feed but mapping has "
                + mapped);
      }
    }
    return errors;
  }

  private static List<String> resolveList(IVariables variables, List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    List<String> resolved = new ArrayList<>(values.size());
    for (String value : values) {
      resolved.add(resolve(variables, value));
    }
    return resolved;
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
