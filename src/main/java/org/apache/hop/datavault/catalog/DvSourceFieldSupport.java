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
package org.apache.hop.datavault.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.catalog.model.CatalogSourceField;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvDataTypeSupport;
import org.apache.hop.datavault.metadata.SourceField;

/**
 * Converts between catalog structured field lists and Data Vault {@link SourceField} / {@link
 * IRowMeta}.
 *
 * <h2>Field layout contract</h2>
 *
 * <p>Authoritative persisted layout is always a structured {@link CatalogSourceField} list:
 *
 * <ul>
 *   <li><b>{@code DV_SOURCE}:</b> {@code dvSource.fields} (types, PK, FK, CSV options). Physical
 *       location refs stay location-only.
 *   <li><b>Vault / BV / DM / physical targets:</b> {@code physicalTable.fields}.
 *   <li><b>Derived view:</b> {@link RecordDefinition#getFields()} ({@link IRowMeta}) is rebuilt in
 *       memory for Hop APIs and is <em>never</em> persisted as {@code rowMetaXml}.
 * </ul>
 *
 * <p>Use {@link #sourceFieldsFromDefinition}, {@link #applyLayoutToDefinition}, and {@link
 * #synchronizeLayoutAfterLoad} / {@link #prepareForPersistence} instead of reading or writing
 * structured fields and row meta independently.
 */
public final class DvSourceFieldSupport {

  private DvSourceFieldSupport() {}

  /**
   * Single read path for catalog field layout (hub/sat import, DDL, preview, validation).
   *
   * <p>Policy: prefer the authoritative structured list for the record type; fill missing hop types
   * / length / precision from the transient row meta only as a migration aid. If structured fields
   * are empty, fall back to transient row meta (legacy documents mid-load).
   */
  public static List<SourceField> sourceFieldsFromDefinition(RecordDefinition definition)
      throws HopException {
    if (definition == null) {
      return List.of();
    }

    List<CatalogSourceField> nested = authoritativeCatalogFields(definition);
    IRowMeta rowMeta = definition.getFields();

    if (nested != null && !nested.isEmpty()) {
      List<SourceField> fields = fromCatalogFields(nested);
      enrichMissingLayoutFromRowMeta(fields, rowMeta);
      return fields;
    }

    if (rowMeta != null && !rowMeta.isEmpty()) {
      return fromRowMeta(rowMeta);
    }
    return List.of();
  }

  /**
   * Apply an {@link IRowMeta} layout (e.g. published table target layout) into the authoritative
   * structured home for the definition type.
   */
  public static void applyRowMetaLayoutToDefinition(
      RecordDefinition definition, IRowMeta layout, IVariables variables) throws HopException {
    applyLayoutToDefinition(definition, fromRowMeta(layout), variables);
  }

  /**
   * Single write path: apply one field list to a definition.
   *
   * <p>Writes structured fields to the authoritative home for the record type ({@code
   * dvSource.fields} or {@code physicalTable.fields}), preserving FK/CSV extras when present, and
   * derives the transient {@link RecordDefinition#setFields IRowMeta} only.
   */
  public static void applyLayoutToDefinition(
      RecordDefinition definition, List<SourceField> fields, IVariables variables)
      throws HopException {
    if (definition == null) {
      return;
    }
    List<SourceField> layout = fields != null ? fields : List.of();
    normalizeHopTypes(layout);

    if (isDvSourceLayout(definition)) {
      DvSourceRecord dvSource = definition.getDvSource();
      if (dvSource == null) {
        dvSource = new DvSourceRecord();
        definition.setDvSource(dvSource);
      }
      Map<String, CatalogSourceField> previous = indexCatalogFieldsByName(dvSource.getFields());
      List<CatalogSourceField> next = toCatalogFields(layout);
      preserveExtras(previous, next);
      dvSource.setFields(next);
      definition.setFields(toRowMeta(fromCatalogFields(next), variables));
      return;
    }

    if (isPhysicalTableLayout(definition)) {
      PhysicalTableRef physicalTable = definition.getPhysicalTable();
      if (physicalTable == null) {
        physicalTable = new PhysicalTableRef();
        definition.setPhysicalTable(physicalTable);
      }
      Map<String, CatalogSourceField> previous =
          indexCatalogFieldsByName(physicalTable.getFields());
      List<CatalogSourceField> next = toCatalogFields(layout);
      preserveExtras(previous, next);
      physicalTable.setFields(next);
      definition.setFields(toRowMeta(fromCatalogFields(next), variables));
      return;
    }

    // Model index types and unknowns with no layout home: keep transient row meta only.
    definition.setFields(toRowMeta(layout, variables));
  }

  /**
   * After loading a document from the catalog: promote legacy transient row meta into the
   * structured home when needed, repair hop types, then re-derive transient row meta from
   * structured fields only.
   */
  public static void synchronizeLayoutAfterLoad(RecordDefinition definition) throws HopException {
    if (definition == null || !hasStructuredLayoutHome(definition)) {
      // Still rebuild nothing for model index entries.
      if (definition != null
          && definition.getFields() != null
          && !definition.getFields().isEmpty()
          && !hasStructuredLayoutHome(definition)) {
        // Leave transient fields for callers that only had rowMetaXml on UNKNOWN without physical.
      }
      return;
    }
    List<CatalogSourceField> nested = authoritativeCatalogFields(definition);
    IRowMeta rowMeta = definition.getFields();

    if (nested == null || nested.isEmpty()) {
      // Promote transient/legacy row meta into structured fields when only rowMetaXml was present.
      if (rowMeta != null && !rowMeta.isEmpty()) {
        applyLayoutToDefinition(definition, fromRowMeta(rowMeta), null);
      } else {
        definition.setFields(new RowMeta());
      }
      return;
    }

    List<SourceField> fields = fromCatalogFields(nested);
    enrichMissingLayoutFromRowMeta(fields, rowMeta);
    // Preserve FKs via applyLayout merge of previous nested extras.
    applyLayoutToDefinition(definition, fields, null);
  }

  /**
   * Before persisting a document: structured fields are authority; regenerate derived transient row
   * meta. Does not emit {@code rowMetaXml}.
   */
  public static void prepareForPersistence(RecordDefinition definition) throws HopException {
    if (definition == null || !hasStructuredLayoutHome(definition)) {
      return;
    }
    List<CatalogSourceField> nested = authoritativeCatalogFields(definition);
    if (nested == null || nested.isEmpty()) {
      IRowMeta rowMeta = definition.getFields();
      if (rowMeta != null && !rowMeta.isEmpty()) {
        applyLayoutToDefinition(definition, fromRowMeta(rowMeta), null);
      }
      return;
    }
    List<SourceField> fields = fromCatalogFields(nested);
    normalizeHopTypes(fields);
    applyLayoutToDefinition(definition, fields, null);
  }

  /** True when this definition stores layout on {@code dvSource.fields}. */
  public static boolean isDvSourceLayout(RecordDefinition definition) {
    if (definition == null) {
      return false;
    }
    RecordDefinitionType type = definition.getType();
    if (type == RecordDefinitionType.DV_SOURCE) {
      return true;
    }
    // Explicit non-source types never use dvSource.fields for layout.
    if (type != null && type != RecordDefinitionType.UNKNOWN) {
      return false;
    }
    // Type unset/unknown while building a source: prefer dvSource when present.
    return definition.getDvSource() != null;
  }

  /**
   * True when this definition stores layout on {@code physicalTable.fields} (vault targets, BV/DM
   * tables, generic physical/ops tables).
   */
  public static boolean isPhysicalTableLayout(RecordDefinition definition) {
    if (definition == null || isDvSourceLayout(definition)) {
      return false;
    }
    RecordDefinitionType type = definition.getType();
    if (type == null) {
      return definition.getPhysicalTable() != null;
    }
    return switch (type) {
      case DV_HUB,
              DV_LINK,
              DV_SATELLITE,
              DV_REFERENCE,
              BV_TABLE,
              DIM_TABLE,
              FACT_TABLE,
              PHYSICAL_TABLE,
              VIEW ->
          true;
      case UNKNOWN -> definition.getPhysicalTable() != null;
      default -> false;
    };
  }

  public static boolean hasStructuredLayoutHome(RecordDefinition definition) {
    return isDvSourceLayout(definition) || isPhysicalTableLayout(definition);
  }

  /** Authoritative structured field list for this definition, or null when none. */
  public static List<CatalogSourceField> authoritativeCatalogFields(RecordDefinition definition) {
    if (definition == null) {
      return null;
    }
    if (isDvSourceLayout(definition)) {
      return definition.getDvSource() != null ? definition.getDvSource().getFields() : null;
    }
    if (isPhysicalTableLayout(definition) && definition.getPhysicalTable() != null) {
      return definition.getPhysicalTable().getFields();
    }
    return null;
  }

  private static void preserveExtras(
      Map<String, CatalogSourceField> previous, List<CatalogSourceField> next) {
    if (previous.isEmpty() || next == null) {
      return;
    }
    for (CatalogSourceField field : next) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      CatalogSourceField prev = previous.get(field.getName().trim());
      if (prev == null) {
        continue;
      }
      if (Utils.isEmpty(field.getFkConstraintName())
          && !Utils.isEmpty(prev.getFkConstraintName())) {
        field.setFkConstraintName(prev.getFkConstraintName());
        field.setFkPosition(prev.getFkPosition());
        field.setFkReferencedSchema(prev.getFkReferencedSchema());
        field.setFkReferencedTable(prev.getFkReferencedTable());
        field.setFkReferencedColumn(prev.getFkReferencedColumn());
      }
      if (field.getInputOptions() == null && prev.getInputOptions() != null) {
        field.setInputOptions(prev.getInputOptions());
      }
      if (Utils.isEmpty(field.getDescription()) && !Utils.isEmpty(prev.getDescription())) {
        field.setDescription(prev.getDescription());
      }
    }
  }

  private static Map<String, CatalogSourceField> indexCatalogFieldsByName(
      List<CatalogSourceField> fields) {
    Map<String, CatalogSourceField> byName = new LinkedHashMap<>();
    if (fields == null) {
      return byName;
    }
    for (CatalogSourceField field : fields) {
      if (field != null && !Utils.isEmpty(field.getName())) {
        byName.putIfAbsent(field.getName().trim(), field);
      }
    }
    return byName;
  }

  public static List<CatalogSourceField> toCatalogFields(List<SourceField> fields) {
    List<CatalogSourceField> result = new ArrayList<>();
    if (fields == null) {
      return result;
    }
    for (SourceField field : fields) {
      if (field == null) {
        continue;
      }
      CatalogSourceField catalogField = new CatalogSourceField();
      catalogField.setName(field.getName());
      catalogField.setDescription(field.getDescription());
      catalogField.setSourceDataType(field.getSourceDataType());
      catalogField.setLength(field.getLength());
      catalogField.setPrecision(field.getPrecision());
      int hopType = DvDataTypeSupport.resolveSourceFieldHopType(field);
      catalogField.setHopType(hopType);
      if (Utils.isEmpty(catalogField.getSourceDataType())) {
        try {
          catalogField.setSourceDataType(ValueMetaFactory.getValueMetaName(hopType));
        } catch (Exception ignored) {
          catalogField.setSourceDataType("String");
        }
      }
      catalogField.setPrimaryKeyPosition(field.getPrimaryKeyPosition());
      if (field.isRenamedFromStream()) {
        catalogField.setSourceStreamName(field.resolveStreamName());
      } else if (!Utils.isEmpty(field.getSourceStreamName())) {
        catalogField.setSourceStreamName(field.getSourceStreamName());
      }
      catalogField.setInputOptions(
          SourceFieldInputOptionsSupport.toCatalog(field.getInputOptions()));
      result.add(catalogField);
    }
    return result;
  }

  public static List<SourceField> fromRowMeta(IRowMeta rowMeta) throws HopException {
    List<SourceField> fields = new ArrayList<>();
    if (rowMeta == null || rowMeta.isEmpty()) {
      return fields;
    }
    for (IValueMeta vm : rowMeta.getValueMetaList()) {
      if (vm == null || Utils.isEmpty(vm.getName())) {
        continue;
      }
      SourceField sf = new SourceField(vm.getName());
      sf.setDescription("");
      String typeDesc = vm.getTypeDesc();
      if (Utils.isEmpty(typeDesc) || "-".equals(typeDesc)) {
        try {
          typeDesc = ValueMetaFactory.getValueMetaName(vm.getType());
        } catch (Exception ignored) {
          typeDesc = "String";
        }
      }
      sf.setSourceDataType(typeDesc);
      sf.setLength(vm.getLength() > 0 ? String.valueOf(vm.getLength()) : "");
      sf.setPrecision(vm.getPrecision() >= 0 ? String.valueOf(vm.getPrecision()) : "");
      int hopType = vm.getType();
      if (hopType <= 0) {
        hopType = IValueMeta.TYPE_STRING;
      }
      sf.setHopType(hopType);
      fields.add(sf);
    }
    return fields;
  }

  public static List<SourceField> fromCatalogFields(List<CatalogSourceField> fields) {
    List<SourceField> result = new ArrayList<>();
    if (fields == null) {
      return result;
    }
    for (CatalogSourceField field : fields) {
      if (field == null) {
        continue;
      }
      SourceField sourceField = new SourceField();
      sourceField.setName(field.getName());
      sourceField.setDescription(field.getDescription());
      sourceField.setSourceDataType(field.getSourceDataType());
      sourceField.setLength(field.getLength());
      sourceField.setPrecision(field.getPrecision());
      sourceField.setHopType(field.getHopType());
      sourceField.setPrimaryKeyPosition(field.getPrimaryKeyPosition());
      sourceField.setSourceStreamName(field.getSourceStreamName());
      sourceField.setInputOptions(
          SourceFieldInputOptionsSupport.fromCatalog(field.getInputOptions()));
      result.add(sourceField);
    }
    return result;
  }

  /**
   * @deprecated use {@link #sourceFieldsFromDefinition(RecordDefinition)}
   */
  @Deprecated
  public static List<SourceField> fromCatalogFieldsEnriched(
      List<CatalogSourceField> catalogFields, IRowMeta rowMeta) throws HopException {
    List<SourceField> fields = fromCatalogFields(catalogFields);
    if (fields.isEmpty() && rowMeta != null && !rowMeta.isEmpty()) {
      return fromRowMeta(rowMeta);
    }
    enrichMissingLayoutFromRowMeta(fields, rowMeta);
    return fields;
  }

  /**
   * When {@code hopType} is unset ({@code <= 0}), copy type/length/precision from matching row-meta
   * columns. Remaining unset types become {@link IValueMeta#TYPE_STRING}.
   */
  public static void enrichFromRowMeta(List<SourceField> fields, IRowMeta rowMeta) {
    enrichMissingLayoutFromRowMeta(fields, rowMeta);
  }

  private static void enrichMissingLayoutFromRowMeta(List<SourceField> fields, IRowMeta rowMeta) {
    if (fields == null || fields.isEmpty()) {
      return;
    }
    Map<String, IValueMeta> byName = indexRowMetaByName(rowMeta);
    for (SourceField field : fields) {
      if (field == null) {
        continue;
      }
      IValueMeta vm = !Utils.isEmpty(field.getName()) ? byName.get(field.getName().trim()) : null;
      if (field.getHopType() <= 0 && vm != null && vm.getType() > 0) {
        field.setHopType(vm.getType());
      }
      // Prefer sourceDataType (SQL / Hop name) over a blank hopType or a stale TYPE_STRING default.
      int resolved = DvDataTypeSupport.resolveSourceFieldHopType(field);
      if (field.getHopType() <= 0
          || (field.getHopType() == IValueMeta.TYPE_STRING && resolved != IValueMeta.TYPE_STRING)) {
        field.setHopType(resolved);
      }
      if (field.getHopType() <= 0) {
        field.setHopType(IValueMeta.TYPE_STRING);
      }
      if (Utils.isEmpty(field.getSourceDataType()) && vm != null) {
        String typeDesc = vm.getTypeDesc();
        if (!Utils.isEmpty(typeDesc) && !"-".equals(typeDesc)) {
          field.setSourceDataType(typeDesc);
        }
      }
      if (Utils.isEmpty(field.getSourceDataType())) {
        try {
          field.setSourceDataType(ValueMetaFactory.getValueMetaName(field.getHopType()));
        } catch (Exception ignored) {
          field.setSourceDataType("String");
        }
      }
      if (Utils.isEmpty(field.getLength()) && vm != null && vm.getLength() > 0) {
        field.setLength(String.valueOf(vm.getLength()));
      }
      if (Utils.isEmpty(field.getPrecision()) && vm != null && vm.getPrecision() >= 0) {
        field.setPrecision(String.valueOf(vm.getPrecision()));
      }
    }
  }

  private static void normalizeHopTypes(List<SourceField> fields) {
    if (fields == null) {
      return;
    }
    for (SourceField field : fields) {
      if (field == null) {
        continue;
      }
      int resolved = DvDataTypeSupport.resolveSourceFieldHopType(field);
      if (field.getHopType() <= 0
          || (field.getHopType() == IValueMeta.TYPE_STRING && resolved != IValueMeta.TYPE_STRING)) {
        field.setHopType(resolved);
      }
      if (field.getHopType() <= 0) {
        field.setHopType(IValueMeta.TYPE_STRING);
      }
    }
  }

  private static Map<String, IValueMeta> indexRowMetaByName(IRowMeta rowMeta) {
    Map<String, IValueMeta> byName = new LinkedHashMap<>();
    if (rowMeta == null || rowMeta.isEmpty()) {
      return byName;
    }
    for (IValueMeta vm : rowMeta.getValueMetaList()) {
      if (vm != null && !Utils.isEmpty(vm.getName())) {
        byName.putIfAbsent(vm.getName().trim(), vm);
      }
    }
    return byName;
  }

  public static IRowMeta toRowMeta(List<SourceField> fields, IVariables variables)
      throws HopException {
    RowMeta rowMeta = new RowMeta();
    if (fields == null) {
      return rowMeta;
    }
    for (SourceField field : fields) {
      try {
        rowMeta.addValueMeta(valueMetaFromSourceField(field, variables));
      } catch (HopPluginException e) {
        throw new HopException("Unable to map source field '" + field.getName() + "'", e);
      }
    }
    return rowMeta;
  }

  public static IRowMeta toRowMetaFromCatalog(List<CatalogSourceField> fields, IVariables variables)
      throws HopException {
    return toRowMeta(fromCatalogFields(fields), variables);
  }

  private static IValueMeta valueMetaFromSourceField(SourceField sf, IVariables variables)
      throws HopPluginException {
    String name = variables != null ? variables.resolve(sf.getName()) : sf.getName();
    int type = DvDataTypeSupport.resolveSourceFieldHopType(sf);
    return ValueMetaFactory.createValueMeta(
        name,
        type,
        Const.toInt(resolveVariable(variables, sf.getLength()), -1),
        Const.toInt(resolveVariable(variables, sf.getPrecision()), -1));
  }

  private static String resolveVariable(IVariables variables, String value) {
    return variables != null ? variables.resolve(value) : value;
  }
}
