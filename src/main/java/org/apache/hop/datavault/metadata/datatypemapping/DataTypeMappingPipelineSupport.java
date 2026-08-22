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
package org.apache.hop.datavault.metadata.datatypemapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.SourceFieldInputOptions;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;

/**
 * Builds and injects Select Values transforms that apply the effective data type mapping layout.
 */
public final class DataTypeMappingPipelineSupport {

  public static final String TRANSFORM_NAME = "apply data type mappings";

  private DataTypeMappingPipelineSupport() {}

  /**
   * @return true when any effective field differs from its physical baseline in a way that needs
   *     Select Values.
   */
  public static boolean needsSelectValues(List<EffectiveSourceField> effectiveFields) {
    if (effectiveFields == null) {
      return false;
    }
    for (EffectiveSourceField field : effectiveFields) {
      if (field != null && field.isMapped()) {
        return true;
      }
    }
    return false;
  }

  public static SelectValuesMeta buildSelectValuesMeta(List<EffectiveSourceField> effectiveFields) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();
    List<SelectMetadataChange> metaChanges = selectMeta.getSelectOption().getMeta();

    if (effectiveFields == null) {
      return selectMeta;
    }

    boolean anyRename = false;
    for (EffectiveSourceField field : effectiveFields) {
      if (field == null || Utils.isEmpty(field.getSourceFieldName())) {
        continue;
      }
      SelectField selectField = new SelectField();
      selectField.setName(field.getSourceFieldName());
      if (field.isRenamed()
          && !Utils.isEmpty(field.getEffectiveFieldName())
          && !field.getSourceFieldName().equals(field.getEffectiveFieldName())) {
        selectField.setRename(field.getEffectiveFieldName());
        anyRename = true;
      }
      selectFields.add(selectField);

      if (field.isTypeChanged()
          || field.isLengthChanged()
          || field.isConversionChanged()
          || field.isRenamed()) {
        SelectMetadataChange change = toMetadataChange(field);
        metaChanges.add(change);
      }
    }

    // Meta-only path when there are no renames: keep all stream fields and only change metadata.
    if (!anyRename && !selectFields.isEmpty()) {
      selectMeta.getSelectOption().setSelectFields(new ArrayList<>());
      selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
    }
    return selectMeta;
  }

  public static SelectMetadataChange toMetadataChange(EffectiveSourceField field) {
    SelectMetadataChange change = new SelectMetadataChange();
    // Prefer source name for lookup; set rename when the effective name differs.
    change.setName(field.getSourceFieldName());
    if (field.isRenamed()
        && !Utils.isEmpty(field.getEffectiveFieldName())
        && !field.getSourceFieldName().equals(field.getEffectiveFieldName())) {
      change.setRename(field.getEffectiveFieldName());
    }
    if (field.effectiveHopType() > IValueMeta.TYPE_NONE) {
      String typeName = DataTypeMappingPatternSupport.hopTypeName(field.effectiveHopType());
      if (Utils.isEmpty(typeName) || "-".equals(typeName)) {
        try {
          typeName = ValueMetaFactory.getValueMetaName(field.effectiveHopType());
        } catch (Exception e) {
          typeName = "String";
        }
      }
      if (Utils.isEmpty(typeName) || "-".equals(typeName)) {
        typeName = "String";
      }
      change.setType(typeName);
    }
    int length = parseInt(field.getLength(), -1);
    int precision = parseInt(field.getPrecision(), -1);
    change.setLength(length);
    change.setPrecision(precision);

    FieldConversionOptions conv = field.getConversion();
    if (conv != null) {
      if (!Utils.isEmpty(conv.getConversionMask())) {
        change.setConversionMask(conv.getConversionMask());
      }
      if (!Utils.isEmpty(conv.getDecimalSymbol())) {
        change.setDecimalSymbol(conv.getDecimalSymbol());
      }
      if (!Utils.isEmpty(conv.getGroupingSymbol())) {
        change.setGroupingSymbol(conv.getGroupingSymbol());
      }
      if (!Utils.isEmpty(conv.getCurrencySymbol())) {
        change.setCurrencySymbol(conv.getCurrencySymbol());
      }
      if (!Utils.isEmpty(conv.getDateFormatLocale())) {
        change.setDateFormatLocale(conv.getDateFormatLocale());
      }
      if (!Utils.isEmpty(conv.getDateFormatTimeZone())) {
        change.setDateFormatTimeZone(conv.getDateFormatTimeZone());
      }
      change.setDateFormatLenient(conv.isDateFormatLenient());
      change.setLenientStringToNumber(conv.isLenientStringToNumber());
      if (!Utils.isEmpty(conv.getEncoding())) {
        change.setEncoding(conv.getEncoding());
      }
      if (!Utils.isEmpty(conv.getRoundingType())) {
        change.setRoundingType(conv.getRoundingType());
      }
      if (!Utils.isEmpty(conv.getStorageType())) {
        change.setStorageType(conv.getStorageType());
      }
    }
    return change;
  }

  /**
   * Appends a Select Values transform after {@code predecessor} when mappings need applying.
   *
   * @return the new transform, or {@code predecessor} when no change is needed
   */
  public static TransformMeta injectAfter(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<EffectiveSourceField> effectiveFields,
      Point location) {
    if (pipelineMeta == null || predecessor == null || !needsSelectValues(effectiveFields)) {
      return predecessor;
    }
    return injectSelectValues(
        pipelineMeta, predecessor, buildSelectValuesMeta(effectiveFields), location);
  }

  /**
   * Inject Select Values from catalog / record-source field layout (effective types, lengths,
   * conversion masks, renames via {@link SourceField#getSourceStreamName()}).
   */
  public static TransformMeta injectFromSourceFields(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<SourceField> sourceFields,
      Point location) {
    return injectFromSourceFields(pipelineMeta, predecessor, sourceFields, location, null);
  }

  /**
   * Same as {@link #injectFromSourceFields(PipelineMeta, TransformMeta, List, Point)} but ignores
   * catalog fields that are not on the incoming stream. Hub/link loads typically {@code SELECT}
   * only business keys; applying metadata to unused catalog columns (for example {@code load_date})
   * makes Select Values fail at runtime.
   *
   * @param streamFieldNames incoming field names; {@code null} means do not filter
   */
  public static TransformMeta injectFromSourceFields(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<SourceField> sourceFields,
      Point location,
      Collection<String> streamFieldNames) {
    List<SourceField> fields = filterToStreamFields(sourceFields, streamFieldNames);
    if (pipelineMeta == null || predecessor == null || !needsSelectValuesFromSourceFields(fields)) {
      return predecessor;
    }
    return injectSelectValues(
        pipelineMeta, predecessor, buildSelectValuesMetaFromSourceFields(fields), location);
  }

  /**
   * Load fields from the record source and inject Select Values when conversion/rename/type meta is
   * present.
   */
  public static TransformMeta injectFromRecordSource(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DataVaultSource recordSource,
      IHopMetadataProvider metadataProvider,
      Point location)
      throws HopException {
    return injectFromRecordSource(
        pipelineMeta, predecessor, recordSource, metadataProvider, location, null);
  }

  /**
   * Load fields from the record source and inject Select Values for those present on the incoming
   * stream.
   *
   * @param streamFieldNames incoming field names; {@code null} means do not filter
   */
  public static TransformMeta injectFromRecordSource(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DataVaultSource recordSource,
      IHopMetadataProvider metadataProvider,
      Point location,
      Collection<String> streamFieldNames)
      throws HopException {
    if (recordSource == null) {
      return predecessor;
    }
    List<SourceField> fields = recordSource.getFields(metadataProvider);
    return injectFromSourceFields(pipelineMeta, predecessor, fields, location, streamFieldNames);
  }

  /**
   * Hub/link source SQL aliases the source-indicator column to the vault record-source field
   * ({@code record_source AS x_record_source}). Catalog mappings still use the physical name.
   * Rewrite the lookup name to the alias and do not rename the stream back.
   */
  public static List<SourceField> rewriteSourceIndicatorLookup(
      List<SourceField> sourceFields, String sourceIndicatorField, String targetRecordSourceField) {
    if (sourceFields == null || sourceFields.isEmpty()) {
      return sourceFields == null ? List.of() : sourceFields;
    }
    if (Utils.isEmpty(sourceIndicatorField)
        || Utils.isEmpty(targetRecordSourceField)
        || sourceIndicatorField.equalsIgnoreCase(targetRecordSourceField)) {
      return sourceFields;
    }
    List<SourceField> rewritten = new ArrayList<>();
    for (SourceField field : sourceFields) {
      if (field == null) {
        continue;
      }
      if (sourceIndicatorField.equalsIgnoreCase(field.getName())
          || sourceIndicatorField.equalsIgnoreCase(field.resolveStreamName())) {
        SourceField copy = new SourceField(field);
        copy.setName(targetRecordSourceField);
        copy.setSourceStreamName(null);
        rewritten.add(copy);
      } else {
        rewritten.add(field);
      }
    }
    return rewritten;
  }

  /**
   * Keeps catalog fields whose stream name or effective name appears in {@code streamFieldNames}.
   * {@code null} stream names means no filtering. Comparison is case-insensitive.
   */
  public static List<SourceField> filterToStreamFields(
      List<SourceField> sourceFields, Collection<String> streamFieldNames) {
    if (sourceFields == null || sourceFields.isEmpty() || streamFieldNames == null) {
      return sourceFields == null ? List.of() : sourceFields;
    }
    Set<String> present = new LinkedHashSet<>();
    for (String name : streamFieldNames) {
      if (!Utils.isEmpty(name)) {
        present.add(name.trim().toLowerCase(Locale.ROOT));
      }
    }
    if (present.isEmpty()) {
      return List.of();
    }
    List<SourceField> filtered = new ArrayList<>();
    for (SourceField field : sourceFields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      if (present.contains(field.getName().trim().toLowerCase(Locale.ROOT))
          || present.contains(field.resolveStreamName().trim().toLowerCase(Locale.ROOT))) {
        filtered.add(field);
      }
    }
    return filtered;
  }

  /**
   * True when catalog fields carry pre-model conversion, renames, or explicit length/precision that
   * should be forced on the stream. Plain type-only imports without length/conversion do not inject
   * (avoids an extra Select Values on every JDBC source).
   */
  public static boolean needsSelectValuesFromSourceFields(List<SourceField> sourceFields) {
    if (sourceFields == null || sourceFields.isEmpty()) {
      return false;
    }
    for (SourceField field : sourceFields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      if (field.isRenamedFromStream()) {
        return true;
      }
      if (hasConversion(field)) {
        return true;
      }
      // Explicit length/precision (e.g. String→2000) is the common pre-model fix for TEXT/CLOB.
      if (!Utils.isEmpty(field.getLength()) || !Utils.isEmpty(field.getPrecision())) {
        return true;
      }
    }
    return false;
  }

  public static SelectValuesMeta buildSelectValuesMetaFromSourceFields(
      List<SourceField> sourceFields) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
    List<SelectMetadataChange> metaChanges = selectMeta.getSelectOption().getMeta();
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();

    if (sourceFields == null) {
      return selectMeta;
    }

    boolean anyRename = false;
    for (SourceField field : sourceFields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      String streamName = field.resolveStreamName();
      if (field.isRenamedFromStream()) {
        SelectField selectField = new SelectField();
        selectField.setName(streamName);
        selectField.setRename(field.getName());
        selectFields.add(selectField);
        anyRename = true;
      }
      if (needsMetadataChange(field)) {
        metaChanges.add(toMetadataChange(field));
      }
    }

    if (anyRename) {
      selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
      // Ensure every field is listed when renames are present so order stays stable.
      if (selectFields.size() < countNamed(sourceFields)) {
        selectFields.clear();
        for (SourceField field : sourceFields) {
          if (field == null || Utils.isEmpty(field.getName())) {
            continue;
          }
          SelectField selectField = new SelectField();
          selectField.setName(field.resolveStreamName());
          if (field.isRenamedFromStream()) {
            selectField.setRename(field.getName());
          }
          selectFields.add(selectField);
        }
      }
    } else {
      selectMeta.getSelectOption().setSelectFields(new ArrayList<>());
      selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
    }
    return selectMeta;
  }

  public static SelectMetadataChange toMetadataChange(SourceField field) {
    SelectMetadataChange change = new SelectMetadataChange();
    change.setName(field.resolveStreamName());
    if (field.isRenamedFromStream()) {
      change.setRename(field.getName());
    }
    if (field.getHopType() > IValueMeta.TYPE_NONE) {
      String typeName = DataTypeMappingPatternSupport.hopTypeName(field.getHopType());
      if (Utils.isEmpty(typeName) || "-".equals(typeName)) {
        try {
          typeName = ValueMetaFactory.getValueMetaName(field.getHopType());
        } catch (Exception e) {
          typeName = "String";
        }
      }
      if (Utils.isEmpty(typeName) || "-".equals(typeName)) {
        typeName = "String";
      }
      change.setType(typeName);
    }
    change.setLength(parseInt(field.getLength(), -1));
    change.setPrecision(parseInt(field.getPrecision(), -1));
    SourceFieldInputOptions inputOptions = field.getInputOptions();
    if (inputOptions != null && inputOptions.getConversion() != null) {
      applyConversion(change, inputOptions.getConversion());
    }
    return change;
  }

  private static void applyConversion(SelectMetadataChange change, FieldConversionOptions conv) {
    if (conv == null) {
      return;
    }
    if (!Utils.isEmpty(conv.getConversionMask())) {
      change.setConversionMask(conv.getConversionMask());
    }
    if (!Utils.isEmpty(conv.getDecimalSymbol())) {
      change.setDecimalSymbol(conv.getDecimalSymbol());
    }
    if (!Utils.isEmpty(conv.getGroupingSymbol())) {
      change.setGroupingSymbol(conv.getGroupingSymbol());
    }
    if (!Utils.isEmpty(conv.getCurrencySymbol())) {
      change.setCurrencySymbol(conv.getCurrencySymbol());
    }
    if (!Utils.isEmpty(conv.getDateFormatLocale())) {
      change.setDateFormatLocale(conv.getDateFormatLocale());
    }
    if (!Utils.isEmpty(conv.getDateFormatTimeZone())) {
      change.setDateFormatTimeZone(conv.getDateFormatTimeZone());
    }
    change.setDateFormatLenient(conv.isDateFormatLenient());
    change.setLenientStringToNumber(conv.isLenientStringToNumber());
    if (!Utils.isEmpty(conv.getEncoding())) {
      change.setEncoding(conv.getEncoding());
    }
    if (!Utils.isEmpty(conv.getRoundingType())) {
      change.setRoundingType(conv.getRoundingType());
    }
    if (!Utils.isEmpty(conv.getStorageType())) {
      change.setStorageType(conv.getStorageType());
    }
  }

  /**
   * True when Select Values must rewrite this field. A hop type alone is not enough: catalog
   * layouts always carry types, including columns the generated source SQL does not read.
   */
  static boolean needsMetadataChange(SourceField field) {
    if (field == null || Utils.isEmpty(field.getName())) {
      return false;
    }
    return field.isRenamedFromStream()
        || hasConversion(field)
        || !Utils.isEmpty(field.getLength())
        || !Utils.isEmpty(field.getPrecision());
  }

  private static boolean hasConversion(SourceField field) {
    SourceFieldInputOptions inputOptions = field.getInputOptions();
    return inputOptions != null
        && inputOptions.getConversion() != null
        && inputOptions.getConversion().hasAnyAttribute();
  }

  private static int countNamed(List<SourceField> fields) {
    int n = 0;
    for (SourceField field : fields) {
      if (field != null && !Utils.isEmpty(field.getName())) {
        n++;
      }
    }
    return n;
  }

  private static TransformMeta injectSelectValues(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      SelectValuesMeta selectMeta,
      Point location) {
    TransformMeta transform = new TransformMeta("SelectValues", TRANSFORM_NAME, selectMeta);
    if (location != null) {
      transform.setLocation(location.x, location.y);
    } else if (predecessor.getLocation() != null) {
      transform.setLocation(predecessor.getLocation().x + 200, predecessor.getLocation().y);
    } else {
      transform.setLocation(300, 100);
    }
    pipelineMeta.addTransform(transform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, transform));
    return transform;
  }

  private static int parseInt(String value, int defaultValue) {
    if (Utils.isEmpty(value)) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
