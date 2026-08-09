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

import java.util.List;
import org.apache.hop.catalog.model.DvCsvFormatRecord;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.composite.DvCompositeSource;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.datavault.metadata.file.DvCsvInputMode;
import org.apache.hop.datavault.metadata.file.DvCsvSource;
import org.apache.hop.datavault.metadata.file.DvFileLocationSupport;
import org.apache.hop.datavault.metadata.file.DvParquetSource;
import org.apache.hop.datavault.metadata.iceberg.DvIcebergLocationSupport;
import org.apache.hop.datavault.metadata.iceberg.DvIcebergSource;

/** Maps catalog {@link RecordDefinition} entries back to in-memory {@link DataVaultSource}. */
public final class CatalogDvSourceMapper {

  private CatalogDvSourceMapper() {}

  public static DataVaultSource toDataVaultSource(RecordDefinition definition, IVariables variables)
      throws HopException {
    if (definition == null || definition.getKey() == null) {
      throw new HopException("Catalog record definition is missing a key");
    }
    if (definition.getType() != RecordDefinitionType.DV_SOURCE) {
      throw new HopException(
          "Catalog record '"
              + definition.getKey()
              + "' is not a DV source (type="
              + definition.getType()
              + ")");
    }

    DataVaultSource source = new DataVaultSource();
    source.setName(definition.getKey().getName());

    DvSourceRecord dvSourceRecord = definition.getDvSource();
    if (dvSourceRecord != null) {
      source.setSourceIndicator(dvSourceRecord.getSourceIndicator());
      source.setSourceIndicatorField(dvSourceRecord.getSourceIndicatorField());
      source.setGroup(dvSourceRecord.getGroup());
      source.setDeliveryType(parseDeliveryType(dvSourceRecord.getDeliveryType()));
      List<SourceField> fields = DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
      source.setSource(buildDvSource(dvSourceRecord, definition, fields));
    } else {
      applyLegacyProperties(source, definition);
      source.setSource(buildLegacyDvSource(definition, variables));
    }

    return source;
  }

  private static void applyLegacyProperties(DataVaultSource source, RecordDefinition definition) {
    source.setSourceIndicator(getCustomProperty(definition, "sourceIndicator"));
    source.setSourceIndicatorField(getCustomProperty(definition, "sourceIndicatorField"));
    source.setGroup(getCustomProperty(definition, "group"));
    source.setDeliveryType(parseDeliveryType(getCustomProperty(definition, "deliveryType")));
  }

  private static IDvSource buildDvSource(
      DvSourceRecord dvSourceRecord, RecordDefinition definition, List<SourceField> fields) {
    DvSourceType sourceType =
        parseSourceType(dvSourceRecord != null ? dvSourceRecord.getSourceType() : null);
    if (sourceType == DvSourceType.DATABASE) {
      DvDatabaseSource dbSource = new DvDatabaseSource();
      dbSource.setDescription(definition.getDescription());
      dbSource.setFields(fields);
      applyPhysicalTable(dbSource, definition.getPhysicalTable());
      return dbSource;
    }
    if (sourceType == DvSourceType.CSV) {
      DvCsvSource csvSource = new DvCsvSource();
      csvSource.setDescription(definition.getDescription());
      csvSource.setFields(fields);
      DvFileLocationSupport.applyPhysicalFile(csvSource, definition.getPhysicalFile());
      applyCsvFormat(csvSource, dvSourceRecord.getCsvFormat());
      return csvSource;
    }
    if (sourceType == DvSourceType.PARQUET) {
      DvParquetSource parquetSource = new DvParquetSource();
      parquetSource.setDescription(definition.getDescription());
      parquetSource.setFields(fields);
      DvFileLocationSupport.applyPhysicalFile(parquetSource, definition.getPhysicalFile());
      return parquetSource;
    }
    if (sourceType == DvSourceType.ICEBERG) {
      DvIcebergSource icebergSource = new DvIcebergSource();
      icebergSource.setDescription(definition.getDescription());
      icebergSource.setFields(fields);
      DvIcebergLocationSupport.applyPhysicalIcebergTable(
          icebergSource, definition.getPhysicalIcebergTable());
      return icebergSource;
    }
    if (sourceType == DvSourceType.COMPOSITE) {
      DvCompositeSource compositeSource = new DvCompositeSource();
      compositeSource.setDescription(definition.getDescription());
      compositeSource.setFields(fields);
      compositeSource.setSourceModelFilename(dvSourceRecord.getCompositeSourceModelFilename());
      compositeSource.setSourceQueryName(dvSourceRecord.getCompositeSourceQueryName());
      compositeSource.setGeneratedSql(dvSourceRecord.getCompositeGeneratedSql());
      return compositeSource;
    }
    if (sourceType == DvSourceType.JSON) {
      org.apache.hop.datavault.metadata.json.DvJsonSource jsonSource =
          new org.apache.hop.datavault.metadata.json.DvJsonSource();
      jsonSource.setDescription(definition.getDescription());
      jsonSource.setFields(fields);
      jsonSource.setSourceModelFilename(dvSourceRecord.getJsonSourceModelFilename());
      jsonSource.setSourceJsonName(dvSourceRecord.getJsonSourceName());
      return jsonSource;
    }
    if (sourceType == DvSourceType.PIPELINE) {
      org.apache.hop.datavault.metadata.pipeline.DvPipelineSource pipelineSource =
          new org.apache.hop.datavault.metadata.pipeline.DvPipelineSource();
      pipelineSource.setDescription(definition.getDescription());
      pipelineSource.setFields(fields);
      pipelineSource.setPipelineFilename(dvSourceRecord.getPipelineFilename());
      pipelineSource.setOutputTransformName(dvSourceRecord.getPipelineTransformName());
      pipelineSource.setPipelineRunConfiguration(dvSourceRecord.getPipelineRunConfiguration());
      pipelineSource.setSourceModelFilename(dvSourceRecord.getPipelineSourceModelFilename());
      pipelineSource.setSourcePipelineName(dvSourceRecord.getPipelineSourceName());
      return pipelineSource;
    }
    DvDatabaseSource fallback = new DvDatabaseSource();
    fallback.setDescription(definition.getDescription());
    fallback.setFields(fields);
    return fallback;
  }

  private static IDvSource buildLegacyDvSource(RecordDefinition definition, IVariables variables)
      throws HopException {
    DvDatabaseSource dbSource = new DvDatabaseSource();
    dbSource.setDescription(definition.getDescription());
    applyPhysicalTable(dbSource, definition.getPhysicalTable());
    if (definition.getPhysicalTable() == null && definition.getCustomProperties() != null) {
      dbSource.setDatabaseName(getCustomProperty(definition, "databaseName"));
      dbSource.setSchemaName(getCustomProperty(definition, "schemaName"));
      dbSource.setTableName(getCustomProperty(definition, "tableName"));
    }
    if (definition.getFields() != null) {
      List<SourceField> fields = new java.util.ArrayList<>();
      for (int i = 0; i < definition.getFields().size(); i++) {
        SourceField field = new SourceField(definition.getFields().getValueMeta(i).getName());
        field.setHopType(definition.getFields().getValueMeta(i).getType());
        field.setLength(Integer.toString(definition.getFields().getValueMeta(i).getLength()));
        field.setPrecision(Integer.toString(definition.getFields().getValueMeta(i).getPrecision()));
        fields.add(field);
      }
      dbSource.setFields(fields);
    }
    return dbSource;
  }

  private static void applyPhysicalTable(
      DvDatabaseSource dbSource, PhysicalTableRef physicalTable) {
    if (physicalTable == null) {
      return;
    }
    dbSource.setDatabaseName(physicalTable.getDatabaseMetaName());
    dbSource.setSchemaName(physicalTable.getSchemaName());
    dbSource.setTableName(physicalTable.getTableName());
  }

  private static void applyCsvFormat(DvCsvSource csvSource, DvCsvFormatRecord csvFormat) {
    if (csvFormat == null) {
      return;
    }
    if (!Utils.isEmpty(csvFormat.getDelimiter())) {
      csvSource.setDelimiter(csvFormat.getDelimiter());
    }
    if (csvFormat.getEnclosure() != null) {
      csvSource.setEnclosure(csvFormat.getEnclosure());
    }
    csvSource.setEscapeCharacter(csvFormat.getEscapeCharacter());
    csvSource.setEncoding(csvFormat.getEncoding());
    csvSource.setHeaderPresent(csvFormat.isHeaderPresent());
    csvSource.setHeaderLines(csvFormat.getHeaderLines());
    if (!Utils.isEmpty(csvFormat.getSingleFilename())) {
      csvSource.setSingleFilename(csvFormat.getSingleFilename());
    }
    csvSource.setInputMode(parseInputMode(csvFormat.getInputTransform()));
  }

  private static DvCsvInputMode parseInputMode(String raw) {
    if (Utils.isEmpty(raw)) {
      return DvCsvInputMode.TEXT_FILE_INPUT;
    }
    if ("CSV_INPUT".equalsIgnoreCase(raw)) {
      return DvCsvInputMode.CSV_INPUT;
    }
    return DvCsvInputMode.TEXT_FILE_INPUT;
  }

  private static String getCustomProperty(RecordDefinition definition, String name) {
    if (definition.getCustomProperties() == null
        || !definition.getCustomProperties().containsKey(name)) {
      return null;
    }
    return definition.getCustomProperties().get(name).getValue();
  }

  private static DvSourceType parseSourceType(String raw) {
    if (Utils.isEmpty(raw)) {
      return DvSourceType.DATABASE;
    }
    try {
      return DvSourceType.valueOf(raw);
    } catch (IllegalArgumentException e) {
      return DvSourceType.DATABASE;
    }
  }

  private static DvSourceDeliveryType parseDeliveryType(String raw) {
    if (Utils.isEmpty(raw)) {
      return DvSourceDeliveryType.CHANGES_ONLY;
    }
    try {
      return DvSourceDeliveryType.valueOf(raw);
    } catch (IllegalArgumentException e) {
      return DvSourceDeliveryType.CHANGES_ONLY;
    }
  }
}
