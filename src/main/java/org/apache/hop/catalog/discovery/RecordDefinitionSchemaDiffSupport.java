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
package org.apache.hop.catalog.discovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.SourceFieldPrimaryKeySupport;
import org.apache.hop.i18n.BaseMessages;

/** Compares stored catalog field contracts against freshly discovered source fields. */
public final class RecordDefinitionSchemaDiffSupport {

  private static final Class<?> PKG = RecordDefinitionSchemaDiffSupport.class;

  private RecordDefinitionSchemaDiffSupport() {}

  public enum ChangeKind {
    ADDED,
    REMOVED,
    CHANGED,
    PRIMARY_KEY_CHANGED
  }

  public record FieldChange(ChangeKind kind, String fieldName, String details) {}

  public record SchemaDiff(List<FieldChange> changes) {

    public boolean hasChanges() {
      return changes != null && !changes.isEmpty();
    }

    public boolean isInSync() {
      return !hasChanges();
    }
  }

  public static SchemaDiff diff(
      List<SourceField> storedFields, List<SourceField> discoveredFields) {
    return diff(storedFields, discoveredFields, false);
  }

  public static SchemaDiff diffTypesOnly(
      List<SourceField> storedFields, List<SourceField> discoveredFields) {
    return diff(storedFields, discoveredFields, true);
  }

  private static SchemaDiff diff(
      List<SourceField> storedFields, List<SourceField> discoveredFields, boolean typesOnly) {
    Map<String, SourceField> storedByName = indexByName(storedFields);
    Map<String, SourceField> discoveredByName = indexByName(discoveredFields);
    List<FieldChange> changes = new ArrayList<>();

    for (Map.Entry<String, SourceField> entry : storedByName.entrySet()) {
      String name = entry.getKey();
      SourceField stored = entry.getValue();
      SourceField discovered = discoveredByName.get(name);
      if (discovered == null) {
        changes.add(new FieldChange(ChangeKind.REMOVED, name, null));
        continue;
      }
      String details =
          typesOnly
              ? describeTypeDifference(stored, discovered)
              : describeDifference(stored, discovered);
      if (!Utils.isEmpty(details)) {
        changes.add(new FieldChange(ChangeKind.CHANGED, name, details));
      }
    }

    for (Map.Entry<String, SourceField> entry : discoveredByName.entrySet()) {
      if (!storedByName.containsKey(entry.getKey())) {
        changes.add(new FieldChange(ChangeKind.ADDED, entry.getKey(), null));
      }
    }

    if (!typesOnly) {
      String primaryKeyDetails =
          SourceFieldPrimaryKeySupport.describePrimaryKeyCompositionDifference(
              storedFields, discoveredFields);
      if (!Utils.isEmpty(primaryKeyDetails)) {
        changes.add(new FieldChange(ChangeKind.PRIMARY_KEY_CHANGED, null, primaryKeyDetails));
      }
    }

    return new SchemaDiff(changes);
  }

  public static String formatDiff(SchemaDiff diff) {
    if (diff == null || !diff.hasChanges()) {
      return BaseMessages.getString(PKG, "RecordDefinitionSchemaDiffSupport.Summary.InSync");
    }

    StringBuilder builder = new StringBuilder();
    builder
        .append(BaseMessages.getString(PKG, "RecordDefinitionSchemaDiffSupport.Summary.Changed"))
        .append('\n');
    for (FieldChange change : diff.changes()) {
      builder.append("  ");
      switch (change.kind()) {
        case ADDED ->
            builder.append(
                BaseMessages.getString(
                    PKG, "RecordDefinitionSchemaDiffSupport.Change.Added", change.fieldName()));
        case REMOVED ->
            builder.append(
                BaseMessages.getString(
                    PKG, "RecordDefinitionSchemaDiffSupport.Change.Removed", change.fieldName()));
        case CHANGED ->
            builder.append(
                BaseMessages.getString(
                    PKG,
                    "RecordDefinitionSchemaDiffSupport.Change.Changed",
                    change.fieldName(),
                    Const.NVL(change.details(), "")));
        case PRIMARY_KEY_CHANGED ->
            builder.append(
                BaseMessages.getString(
                    PKG,
                    "RecordDefinitionSchemaDiffSupport.Change.PrimaryKeyChanged",
                    Const.NVL(change.details(), "")));
        default -> builder.append(change.fieldName());
      }
      builder.append('\n');
    }
    return builder.toString().trim();
  }

  private static Map<String, SourceField> indexByName(List<SourceField> fields) {
    Map<String, SourceField> indexed = new LinkedHashMap<>();
    if (fields == null) {
      return indexed;
    }
    for (SourceField field : fields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      // Case-insensitive: SingleStore/MySQL JDBC names may differ in case from catalog.
      indexed.putIfAbsent(field.getName().trim().toLowerCase(Locale.ROOT), field);
    }
    return indexed;
  }

  private static String describeTypeDifference(SourceField stored, SourceField discovered) {
    return SourceFieldMetadataEquivalenceSupport.describeTypeDifference(stored, discovered);
  }

  private static String describeDifference(SourceField stored, SourceField discovered) {
    List<String> parts = new ArrayList<>();
    String typeDetails =
        SourceFieldMetadataEquivalenceSupport.describeTypeDifference(stored, discovered);
    if (!Utils.isEmpty(typeDetails)) {
      parts.add(typeDetails);
    }
    String dimensionDetails =
        SourceFieldMetadataEquivalenceSupport.describeDimensionDifference(stored, discovered);
    if (!Utils.isEmpty(dimensionDetails)) {
      parts.add(dimensionDetails);
    }
    return parts.isEmpty() ? null : String.join("; ", parts);
  }
}
