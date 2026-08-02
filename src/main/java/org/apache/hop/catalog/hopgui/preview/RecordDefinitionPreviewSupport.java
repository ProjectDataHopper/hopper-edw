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
 *
 */

package org.apache.hop.catalog.hopgui.preview;

import org.apache.hop.catalog.hopgui.perspective.DataCatalogPerspective;
import org.apache.hop.catalog.model.DvCsvFormatRecord;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.PhysicalFileRef;
import org.apache.hop.catalog.model.PhysicalIcebergTableRef;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.CatalogDvSourceMapper;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSourcePreviewInputSupport;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Builds preview pipelines from catalog record definitions. */
public final class RecordDefinitionPreviewSupport {

  private static final Class<?> PKG = DataCatalogPerspective.class;

  private RecordDefinitionPreviewSupport() {}

  public static boolean supportsPreview(RecordDefinition definition) {
    if (definition == null) {
      return false;
    }
    if (definition.getType() == RecordDefinitionType.DV_SOURCE) {
      return hasDvSourcePreviewLocation(definition);
    }
    return hasPhysicalTable(definition.getPhysicalTable());
  }

  public static DvSourcePreviewInputSupport.PreviewPipeline buildPreviewPipeline(
      RecordDefinition definition,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    if (definition == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionPreviewSupport.Error.NoDefinition"));
    }
    if (!supportsPreview(definition)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionPreviewSupport.Error.Unsupported"));
    }

    if (definition.getType() == RecordDefinitionType.DV_SOURCE) {
      DataVaultSource recordSource = CatalogDvSourceMapper.toDataVaultSource(definition, variables);
      IDvSource dvSource = recordSource.getSource();
      if (dvSource == null) {
        throw new HopException(
            BaseMessages.getString(PKG, "RecordDefinitionPreviewSupport.Error.MissingDvSource"));
      }
      return DvSourcePreviewInputSupport.buildPreviewPipeline(
          recordSource, dvSource, variables, metadataProvider, rowLimit);
    }

    DvDatabaseSource databaseSource = toPhysicalTableSource(definition, variables);
    return DvSourcePreviewInputSupport.buildDatabasePreview(
        databaseSource, variables, metadataProvider, rowLimit);
  }

  private static DvDatabaseSource toPhysicalTableSource(
      RecordDefinition definition, IVariables variables) throws HopException {
    PhysicalTableRef physicalTable = definition.getPhysicalTable();
    if (!hasPhysicalTable(physicalTable)) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionPreviewSupport.Error.MissingPhysicalTable"));
    }

    DvDatabaseSource databaseSource = new DvDatabaseSource();
    databaseSource.setDatabaseName(physicalTable.getDatabaseMetaName());
    databaseSource.setSchemaName(physicalTable.getSchemaName());
    databaseSource.setTableName(physicalTable.getTableName());
    databaseSource.setDescription(definition.getDescription());

    if (definition.getFields() != null && !definition.getFields().isEmpty()) {
      databaseSource.setFields(DvSourceFieldSupport.fromRowMeta(definition.getFields()));
    } else if (definition.getDvSource() != null
        && definition.getDvSource().getFields() != null
        && !definition.getDvSource().getFields().isEmpty()) {
      databaseSource.setFields(
          DvSourceFieldSupport.fromCatalogFields(definition.getDvSource().getFields()));
    }
    return databaseSource;
  }

  private static boolean hasPhysicalTable(PhysicalTableRef physicalTable) {
    return physicalTable != null
        && !Utils.isEmpty(physicalTable.getDatabaseMetaName())
        && !Utils.isEmpty(physicalTable.getTableName());
  }

  private static boolean hasDvSourcePreviewLocation(RecordDefinition definition) {
    DvSourceRecord dvSource = definition.getDvSource();
    String sourceType = dvSource != null ? dvSource.getSourceType() : null;
    if (Utils.isEmpty(sourceType)) {
      return hasPhysicalTable(definition.getPhysicalTable());
    }

    DvSourceType parsed = parseSourceType(sourceType);
    return switch (parsed) {
      case DATABASE -> hasPhysicalTable(definition.getPhysicalTable());
      case CSV -> hasCsvPreviewLocation(definition);
      case PARQUET -> hasParquetPreviewLocation(definition);
      case ICEBERG -> hasIcebergPreviewLocation(definition);
      case COMPOSITE ->
          definition.getDvSource() != null
              && !Utils.isEmpty(definition.getDvSource().getCompositeSourceModelFilename())
              && !Utils.isEmpty(definition.getDvSource().getCompositeSourceQueryName());
    };
  }

  private static boolean hasCsvPreviewLocation(RecordDefinition definition) {
    DvCsvFormatRecord csvFormat =
        definition.getDvSource() != null ? definition.getDvSource().getCsvFormat() : null;
    if (csvFormat != null && !Utils.isEmpty(csvFormat.getSingleFilename())) {
      return true;
    }
    PhysicalFileRef physicalFile = definition.getPhysicalFile();
    return physicalFile != null && !Utils.isEmpty(physicalFile.getFolder());
  }

  private static boolean hasParquetPreviewLocation(RecordDefinition definition) {
    PhysicalFileRef physicalFile = definition.getPhysicalFile();
    return physicalFile != null && !Utils.isEmpty(physicalFile.getFolder());
  }

  private static boolean hasIcebergPreviewLocation(RecordDefinition definition) {
    PhysicalIcebergTableRef physicalIcebergTable = definition.getPhysicalIcebergTable();
    return physicalIcebergTable != null
        && !Utils.isEmpty(physicalIcebergTable.getCatalogUri())
        && !Utils.isEmpty(physicalIcebergTable.getNamespace())
        && !Utils.isEmpty(physicalIcebergTable.getTableName());
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
}