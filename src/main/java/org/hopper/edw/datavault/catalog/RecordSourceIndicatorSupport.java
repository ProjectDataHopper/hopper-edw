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
package org.hopper.edw.datavault.catalog;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.SourceField;

/** Applies record source indicator settings to {@link DataVaultSource} instances. */
public final class RecordSourceIndicatorSupport {

  private static final List<String> PREFERRED_FIELD_NAMES =
      List.of("x_record_source", "record_source", "RECORD_SOURCE");

  private RecordSourceIndicatorSupport() {}

  public static String suggestRecordSourceField(List<String> fieldNames) {
    if (fieldNames == null || fieldNames.isEmpty()) {
      return null;
    }
    List<String> normalized = new ArrayList<>();
    for (String fieldName : fieldNames) {
      if (!Utils.isEmpty(fieldName)) {
        normalized.add(fieldName);
      }
    }
    for (String preferred : PREFERRED_FIELD_NAMES) {
      for (String fieldName : normalized) {
        if (preferred.equalsIgnoreCase(fieldName)) {
          return fieldName;
        }
      }
    }
    return null;
  }

  public static List<String> fieldNamesFromSourceFields(List<SourceField> fields) {
    List<String> names = new ArrayList<>();
    if (fields == null) {
      return names;
    }
    for (SourceField field : fields) {
      if (field != null && !Utils.isEmpty(field.getName())) {
        names.add(field.getName());
      }
    }
    return names;
  }

  public static void applyRecordSource(
      DataVaultSource source, RecordSourceIndicatorOptions options) {
    if (source == null || options == null) {
      return;
    }
    if (options.getMode() == RecordSourceIndicatorOptions.Mode.FIELD_OR_STATIC) {
      options = resolveForTable(options, List.of(), options.getStaticValue());
    }
    applyResolved(source, trimToNull(options.getStaticValue()), trimToNull(options.getFieldName()));
  }

  public static RecordSourceIndicatorOptions resolveForTable(
      RecordSourceIndicatorOptions options, List<SourceField> fields, String defaultStaticValue) {
    if (options == null) {
      return RecordSourceIndicatorOptions.staticValue(defaultStaticValue);
    }
    String staticFallback =
        !Utils.isEmpty(options.getStaticValue()) ? options.getStaticValue() : defaultStaticValue;

    return switch (options.getMode()) {
      case STATIC -> RecordSourceIndicatorOptions.staticValue(staticFallback);
      case FIELD -> {
        String fieldName = options.getFieldName();
        if (Utils.isEmpty(fieldName)) {
          fieldName = suggestRecordSourceField(fieldNamesFromSourceFields(fields));
        }
        if (Utils.isEmpty(fieldName)) {
          yield RecordSourceIndicatorOptions.staticValue(staticFallback);
        }
        yield RecordSourceIndicatorOptions.fieldName(fieldName);
      }
      case FIELD_OR_STATIC -> {
        String fieldName = suggestRecordSourceField(fieldNamesFromSourceFields(fields));
        if (Utils.isEmpty(fieldName)) {
          yield RecordSourceIndicatorOptions.staticValue(staticFallback);
        }
        yield RecordSourceIndicatorOptions.fieldName(fieldName);
      }
    };
  }

  /** Normalizes mutually exclusive static vs field values from catalog editor widgets. */
  public static void applyFromEditorValues(
      DataVaultSource source, String sourceIndicator, String sourceIndicatorField) {
    if (source == null) {
      return;
    }
    String staticValue = trimToNull(sourceIndicator);
    String fieldName = trimToNull(sourceIndicatorField);
    if (staticValue != null) {
      applyResolved(source, staticValue, null);
    } else if (fieldName != null) {
      applyResolved(source, null, fieldName);
    } else {
      applyResolved(source, null, null);
    }
  }

  public static void applyToDvSourceRecord(
      org.hopper.edw.catalog.model.DvSourceRecord dvSource,
      String sourceIndicator,
      String sourceIndicatorField) {
    if (dvSource == null) {
      return;
    }
    String staticValue = trimToNull(sourceIndicator);
    String fieldName = trimToNull(sourceIndicatorField);
    if (staticValue != null) {
      dvSource.setSourceIndicator(staticValue);
      dvSource.setSourceIndicatorField(null);
    } else if (fieldName != null) {
      dvSource.setSourceIndicator(null);
      dvSource.setSourceIndicatorField(fieldName);
    } else {
      dvSource.setSourceIndicator(null);
      dvSource.setSourceIndicatorField(null);
    }
  }

  public static String deliveryTypeLabel(
      org.hopper.edw.datavault.metadata.DvSourceDeliveryType deliveryType) {
    if (deliveryType == null) {
      return "";
    }
    return deliveryType.getDescription();
  }

  /**
   * Parses a delivery type from a stored code ({@code CHANGES_ONLY}, {@code FULL_SNAPSHOT}), enum
   * name, or UI description label.
   *
   * <p>Important: do not use {@link
   * org.hopper.edw.datavault.metadata.DvSourceDeliveryType#lookupDescription(String)} alone for
   * unknown values — that API returns a default of {@code CHANGES_ONLY} instead of null, which
   * would swallow valid codes such as {@code FULL_SNAPSHOT}.
   */
  public static org.hopper.edw.datavault.metadata.DvSourceDeliveryType parseDeliveryType(
      String value) {
    if (Utils.isEmpty(value)) {
      return org.hopper.edw.datavault.metadata.DvSourceDeliveryType.CHANGES_ONLY;
    }
    String trimmed = value.trim();
    for (org.hopper.edw.datavault.metadata.DvSourceDeliveryType type :
        org.hopper.edw.datavault.metadata.DvSourceDeliveryType.values()) {
      if (type.getCode().equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed)) {
        return type;
      }
      if (type.getDescription() != null && type.getDescription().equalsIgnoreCase(trimmed)) {
        return type;
      }
    }
    return org.hopper.edw.datavault.metadata.DvSourceDeliveryType.CHANGES_ONLY;
  }

  private static void applyResolved(DataVaultSource source, String staticValue, String fieldName) {
    source.setSourceIndicator(staticValue);
    source.setSourceIndicatorField(fieldName);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
