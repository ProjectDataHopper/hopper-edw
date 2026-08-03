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

import java.util.Date;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.catalog.model.DvCsvFormatRecord;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.catalog.CatalogModelRegistrySupport;
import org.apache.hop.datavault.catalog.DvSourceCatalogMapper;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSourceDeliveryType;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.apache.hop.datavault.metadata.file.CsvFileMetadataDiscovery;
import org.apache.hop.datavault.metadata.file.DvCsvInputMode;
import org.apache.hop.datavault.metadata.file.DvCsvSource;
import org.apache.hop.datavault.metadata.file.DvFileLocationSupport;
import org.apache.hop.datavault.metadata.file.DvParquetSource;
import org.apache.hop.datavault.metadata.file.IDvFileBasedSource;
import org.apache.hop.datavault.metadata.iceberg.DvIcebergSource;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Persists discovered record definitions to a data catalog connection. */
public final class RecordDefinitionCatalogWriter {

  private static final Class<?> PKG = RecordDefinitionCatalogWriter.class;

  private RecordDefinitionCatalogWriter() {}

  public static void upsert(
      RecordDefinitionWriteRequest request,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    validateRequest(request);

    RecordDefinitionType recordType =
        request.getRecordType() != null ? request.getRecordType() : RecordDefinitionType.DV_SOURCE;

    RecordDefinition definition;
    if (recordType == RecordDefinitionType.DV_SOURCE) {
      DataVaultSource source = buildDataVaultSource(request, variables);
      definition =
          DvSourceCatalogMapper.toRecordDefinition(
              source,
              request.getNamespace(),
              recordType,
              request.getModel(),
              variables,
              metadataProvider,
              request.getUpdatedAt() != null ? request.getUpdatedAt() : new Date(),
              request.getWorkflowName(),
              request.getPipelineName());
    } else {
      definition = buildGenericRecordDefinition(request, variables, recordType);
    }

    if (!Utils.isEmpty(request.getDescription())) {
      definition.setDescription(request.getDescription());
    }

    RecordDefinitionRegistry.getInstance()
        .upsert(request.getCatalogConnectionName(), definition, variables, metadataProvider);
  }

  public static void upsertDataVaultSource(
      DataVaultSource source,
      String catalogConnectionName,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    upsertDataVaultSource(
        source, catalogConnectionName, model, variables, metadataProvider, new Date(), null, null);
  }

  public static void upsertDataVaultSource(
      DataVaultSource source,
      String catalogConnectionName,
      DataVaultModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt,
      String workflowName,
      String pipelineName)
      throws HopException {
    if (source == null || Utils.isEmpty(source.getName())) {
      throw new HopException("Data Vault source is missing a name");
    }
    if (Utils.isEmpty(catalogConnectionName)) {
      throw new HopException("Catalog connection name is required");
    }
    String namespace =
        org.apache.hop.datavault.catalog.DvSourceCatalogService.projectSourcesNamespace(variables);
    RecordDefinition definition =
        DvSourceCatalogMapper.toRecordDefinition(
            source,
            namespace,
            RecordDefinitionType.DV_SOURCE,
            model,
            variables,
            metadataProvider,
            updatedAt != null ? updatedAt : new Date(),
            workflowName,
            pipelineName);
    RecordDefinitionRegistry.getInstance()
        .upsert(catalogConnectionName, definition, variables, metadataProvider);
  }

  private static void validateRequest(RecordDefinitionWriteRequest request) throws HopException {
    if (request == null) {
      throw new HopException("Record definition write request is required");
    }
    if (Utils.isEmpty(request.getCatalogConnectionName())) {
      throw new HopException("Catalog connection name is required");
    }
    if (Utils.isEmpty(request.getNamespace())) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionCatalogWriter.Error.MissingNamespace"));
    }
    if (Utils.isEmpty(request.getName())) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionCatalogWriter.Error.MissingName"));
    }
    if (request.getFields() == null || request.getFields().isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "RecordDefinitionDiscoveryService.Error.NoFields"));
    }
  }

  private static DataVaultSource buildDataVaultSource(
      RecordDefinitionWriteRequest request, IVariables variables) throws HopException {
    IDvSource dvSource = buildDvSource(request, variables);
    dvSource.setFields(request.getFields());

    DataVaultSource source = new DataVaultSource(request.getName());
    source.setSource(dvSource);
    source.setSourceIndicator(request.getSourceIndicator());
    source.setSourceIndicatorField(request.getSourceIndicatorField());
    source.setGroup(request.getGroup());
    if (request.getDeliveryType() != null) {
      source.setDeliveryType(request.getDeliveryType());
    } else {
      source.setDeliveryType(DvSourceDeliveryType.CHANGES_ONLY);
    }
    if (!Utils.isEmpty(request.getDescription())
        && dvSource instanceof org.apache.hop.datavault.metadata.DvSourceBase dvSourceBase) {
      dvSourceBase.setDescription(request.getDescription());
    }
    return source;
  }

  private static IDvSource buildDvSource(RecordDefinitionWriteRequest request, IVariables variables)
      throws HopException {
    DvSourceType sourceType = request.getSourceType();
    PhysicalSourceRef physicalRef = request.getPhysicalRef();
    if (sourceType == null || physicalRef == null) {
      throw new HopException("Source type and physical reference are required.");
    }

    return switch (sourceType) {
      case DATABASE ->
          DvDatabaseSourceImportSupport.createDatabaseSource(
              physicalRef.getDatabaseConnectionName(),
              physicalRef.getSchemaName(),
              physicalRef.getTableName(),
              request.getFields());
      case CSV -> buildCsvSource(request, variables);
      case PARQUET -> buildParquetSource(request, variables);
      case ICEBERG -> buildIcebergSource(request, variables);
      case COMPOSITE ->
          throw new HopException(
              "Composite sources are published from a .hsm source model, not via discovery write requests.");
    };
  }

  private static DvCsvSource buildCsvSource(
      RecordDefinitionWriteRequest request, IVariables variables) throws HopException {
    PhysicalSourceRef physicalRef = request.getPhysicalRef();
    CsvFileMetadataDiscovery.DiscoveryResult csvDiscovery = request.getCsvDiscovery();

    if (!Utils.isEmpty(physicalRef.getFilePath())) {
      String resolvedFile = variables.resolve(physicalRef.getFilePath());
      if (csvDiscovery != null) {
        return createCsvSourceFromFile(resolvedFile, csvDiscovery, variables);
      }
      CsvFileMetadataDiscovery.DiscoveryResult discovery =
          CsvFileMetadataDiscovery.discover(resolvedFile, variables, null);
      return createCsvSourceFromFile(resolvedFile, discovery, variables);
    }

    DvCsvSource csvSource = new DvCsvSource();
    csvSource.setFolder(physicalRef.getFolder());
    csvSource.setIncludeFileMask(physicalRef.getIncludeFileMask());
    csvSource.setExcludeFileMask(physicalRef.getExcludeFileMask());
    csvSource.setIncludeSubfolders(physicalRef.isIncludeSubfolders());
    if (csvDiscovery != null) {
      csvSource.setDelimiter(
          Utils.isEmpty(csvDiscovery.delimiter()) ? "," : csvDiscovery.delimiter());
      csvSource.setEnclosure(csvDiscovery.enclosure() != null ? csvDiscovery.enclosure() : "\"");
      csvSource.setEncoding(csvDiscovery.charset());
      csvSource.setHeaderPresent(csvDiscovery.headerPresent());
      csvSource.setHeaderLines(csvDiscovery.headerLines());
    }
    return csvSource;
  }

  private static DvCsvSource createCsvSourceFromFile(
      String filePath, CsvFileMetadataDiscovery.DiscoveryResult discovery, IVariables variables)
      throws HopException {
    String folder;
    String baseName;
    try {
      FileObject fileObject = HopVfs.getFileObject(filePath, variables);
      FileObject parent = fileObject.getParent();
      if (parent == null) {
        throw new HopException("Unable to resolve parent folder for file: " + filePath);
      }
      folder = HopVfs.getFilename(parent);
      baseName = fileObject.getName().getBaseName();
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to resolve CSV file location: " + filePath, e);
    }

    DvCsvSource csvSource = new DvCsvSource();
    csvSource.setFolder(folder);
    csvSource.setIncludeFileMask(DvFileLocationSupport.toIncludeFileMask(baseName));
    csvSource.setExcludeFileMask("");
    csvSource.setIncludeSubfolders(false);
    csvSource.setDelimiter(Utils.isEmpty(discovery.delimiter()) ? "," : discovery.delimiter());
    csvSource.setEnclosure(discovery.enclosure() != null ? discovery.enclosure() : "\"");
    csvSource.setEncoding(discovery.charset());
    csvSource.setHeaderPresent(discovery.headerPresent());
    csvSource.setHeaderLines(discovery.headerLines());
    return csvSource;
  }

  private static DvParquetSource buildParquetSource(
      RecordDefinitionWriteRequest request, IVariables variables) throws HopException {
    PhysicalSourceRef physicalRef = request.getPhysicalRef();
    if (!Utils.isEmpty(physicalRef.getFilePath())) {
      return createParquetSourceFromFile(variables.resolve(physicalRef.getFilePath()), variables);
    }
    DvParquetSource parquetSource = new DvParquetSource();
    parquetSource.setFolder(physicalRef.getFolder());
    parquetSource.setIncludeFileMask(physicalRef.getIncludeFileMask());
    parquetSource.setExcludeFileMask(physicalRef.getExcludeFileMask());
    parquetSource.setIncludeSubfolders(physicalRef.isIncludeSubfolders());
    return parquetSource;
  }

  private static DvIcebergSource buildIcebergSource(
      RecordDefinitionWriteRequest request, IVariables variables) {
    PhysicalSourceRef physicalRef = request.getPhysicalRef();
    DvIcebergSource icebergSource = new DvIcebergSource();
    icebergSource.setCatalogUri(physicalRef.getCatalogUri());
    icebergSource.setWarehouse(physicalRef.getWarehouse());
    icebergSource.setNamespace(physicalRef.getIcebergNamespace());
    icebergSource.setTableName(physicalRef.getIcebergTableName());
    icebergSource.setSnapshotId(physicalRef.getSnapshotId());
    icebergSource.setBranch(physicalRef.getBranch());
    icebergSource.setS3Endpoint(physicalRef.getS3Endpoint());
    icebergSource.setS3AccessKey(physicalRef.getS3AccessKey());
    icebergSource.setS3SecretKey(physicalRef.getS3SecretKey());
    return icebergSource;
  }

  private static DvParquetSource createParquetSourceFromFile(String filePath, IVariables variables)
      throws HopException {
    try {
      FileObject fileObject = HopVfs.getFileObject(filePath, variables);
      FileObject parent = fileObject.getParent();
      if (parent == null) {
        throw new HopException("Unable to resolve parent folder for file: " + filePath);
      }
      DvParquetSource parquetSource = new DvParquetSource();
      parquetSource.setFolder(HopVfs.getFilename(parent));
      parquetSource.setIncludeFileMask(
          DvFileLocationSupport.toIncludeFileMask(fileObject.getName().getBaseName()));
      parquetSource.setExcludeFileMask("");
      parquetSource.setIncludeSubfolders(false);
      return parquetSource;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to resolve Parquet file location: " + filePath, e);
    }
  }

  private static RecordDefinition buildGenericRecordDefinition(
      RecordDefinitionWriteRequest request, IVariables variables, RecordDefinitionType recordType)
      throws HopException {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(request.getNamespace(), request.getName()));
    definition.setType(recordType);
    definition.setDescription(request.getDescription());
    definition.setFields(DvSourceFieldSupport.toRowMeta(request.getFields(), variables));
    definition.setOrigin(buildGenericOrigin(request, variables));
    applyPhysicalLocation(definition, request, variables);
    definition.getTags().add(recordType.name());
    if (request.getSourceType() != null) {
      definition.getTags().add(request.getSourceType().name());
    }
    return definition;
  }

  private static RecordOrigin buildGenericOrigin(
      RecordDefinitionWriteRequest request, IVariables variables) {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType(
        request.getRecordType() != null ? request.getRecordType().name() : "RECORD");
    if (request.getModel() != null) {
      origin.setModelName(request.getModel().getName());
      origin.setModelFilename(
          CatalogModelRegistrySupport.portableModelPath(
              request.getModel().getFilename(), variables));
      origin.setHopProject(request.getModel().getName());
    }
    origin.setModelElementName(request.getName());
    Date updatedAt = request.getUpdatedAt() != null ? request.getUpdatedAt() : new Date();
    origin.setUpdatedAt(updatedAt);
    origin.setLastDiscoveredAt(updatedAt);
    origin.setLastWorkflow(request.getWorkflowName());
    origin.setLastPipeline(request.getPipelineName());
    return origin;
  }

  private static void applyPhysicalLocation(
      RecordDefinition definition, RecordDefinitionWriteRequest request, IVariables variables)
      throws HopException {
    DvSourceType sourceType = request.getSourceType();
    PhysicalSourceRef physicalRef = request.getPhysicalRef();
    if (sourceType == null || physicalRef == null) {
      return;
    }

    if (sourceType == DvSourceType.DATABASE) {
      PhysicalTableRef tableRef = new PhysicalTableRef();
      tableRef.setDatabaseMetaName(
          variables != null
              ? variables.resolve(physicalRef.getDatabaseConnectionName())
              : physicalRef.getDatabaseConnectionName());
      tableRef.setSchemaName(
          variables != null
              ? variables.resolve(physicalRef.getSchemaName())
              : physicalRef.getSchemaName());
      tableRef.setTableName(
          variables != null
              ? variables.resolve(physicalRef.getTableName())
              : physicalRef.getTableName());
      definition.setPhysicalTable(tableRef);
      definition.setPhysicalFile(null);
      definition.setPhysicalIcebergTable(null);
      return;
    }

    if (sourceType == DvSourceType.ICEBERG) {
      definition.setPhysicalIcebergTable(physicalRef.toPhysicalIcebergTableRef(variables));
      definition.setPhysicalTable(null);
      definition.setPhysicalFile(null);
      return;
    }

    IDvSource fileSource = buildDvSource(request, variables);
    if (fileSource instanceof IDvFileBasedSource fileBasedSource) {
      definition.setPhysicalFile(DvFileLocationSupport.toPhysicalFileRef(fileBasedSource));
      definition.setPhysicalTable(null);
      if (sourceType == DvSourceType.CSV && fileSource instanceof DvCsvSource csvSource) {
        DvCsvFormatRecord format = new DvCsvFormatRecord();
        format.setDelimiter(csvSource.getDelimiter());
        format.setEnclosure(csvSource.getEnclosure());
        format.setEscapeCharacter(csvSource.getEscapeCharacter());
        format.setEncoding(csvSource.getEncoding());
        format.setHeaderPresent(csvSource.isHeaderPresent());
        format.setHeaderLines(csvSource.getHeaderLines());
        format.setFileFormat("CSV");
        format.setInputTransform(
            csvSource.getInputMode() == DvCsvInputMode.CSV_INPUT ? "CSV_INPUT" : "TEXT_FILE_INPUT");
        format.setSingleFilename(csvSource.getSingleFilename());
      }
    }
  }
}
