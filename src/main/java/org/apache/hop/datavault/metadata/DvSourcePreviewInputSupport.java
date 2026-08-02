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

package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.TextFileInputField;
import org.apache.hop.core.fileinput.InputFile;
import org.apache.hop.core.fileinput.FileTypeFilter;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.composite.DvCompositeSource;
import org.apache.hop.datavault.metadata.composite.DvCompositeSourceResolver;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourcePreviewSupport;
import org.apache.hop.datavault.metadata.file.DvCsvSource;
import org.apache.hop.datavault.metadata.file.DvParquetPluginSupport;
import org.apache.hop.datavault.metadata.file.DvParquetSource;
import org.apache.hop.datavault.metadata.file.DvTextFileInputFieldSupport;
import org.apache.hop.datavault.metadata.file.IDvFileBasedSource;
import org.apache.hop.datavault.metadata.iceberg.DvIcebergSource;
import org.apache.hop.datavault.transform.iceberginput.IcebergTableInputField;
import org.apache.hop.datavault.transform.iceberginput.IcebergTableInputMeta;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.PipelinePreviewFactory;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.csvinput.CsvInputField;
import org.apache.hop.pipeline.transforms.csvinput.CsvInputMeta;
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.apache.hop.pipeline.transforms.fileinput.text.TextFileInputMeta;
import org.apache.hop.pipeline.transforms.getfilenames.FileItem;
import org.apache.hop.pipeline.transforms.getfilenames.FilterItem;
import org.apache.hop.pipeline.transforms.getfilenames.GetFileNamesMeta;
import org.apache.hop.pipeline.transforms.selectvalues.DeleteField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;

/** Builds preview pipelines with the correct input transform for a Data Vault source. */
public final class DvSourcePreviewInputSupport {

  private static final Class<?> PKG = DvSourcePreviewInputSupport.class;

  private static final String PARQUET_FILENAME_FIELD = "filename";
  private static final String SELECT_FILENAME_TRANSFORM_NAME = "filename";
  private static final String REMOVE_FILENAME_TRANSFORM_NAME = "remove filename";
  private static final String DUMMY_TRANSFORM_NAME = "dummy";
  private static final int TRANSFORM_SPACING_X = 128;

  private DvSourcePreviewInputSupport() {}

  public record PreviewPipeline(PipelineMeta pipelineMeta, String previewTransformName) {}

  public static PreviewPipeline buildPreviewPipeline(
      DataVaultSource recordSource,
      IDvSource dvSource,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    if (recordSource == null || dvSource == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingSource"));
    }
    DvSourceType sourceType = dvSource.getSourceType();
    if (sourceType == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.UnsupportedSourceType"));
    }
    return switch (sourceType) {
      case DATABASE -> buildDatabasePreview(recordSource, (DvDatabaseSource) dvSource, variables, metadataProvider, rowLimit);
      case CSV -> buildCsvPreview(recordSource, (DvCsvSource) dvSource, variables, metadataProvider, rowLimit);
      case PARQUET -> buildParquetPreview(recordSource, (DvParquetSource) dvSource, variables, metadataProvider, rowLimit);
      case ICEBERG ->
          buildIcebergPreview(recordSource, (DvIcebergSource) dvSource, variables, metadataProvider, rowLimit);
      case COMPOSITE ->
          buildCompositePreview(
              recordSource, (DvCompositeSource) dvSource, variables, metadataProvider, rowLimit);
    };
  }

  /**
   * Preview pipeline for a composite (source-query) feed: Table Input with SQL resolved from the
   * live {@code .hsm} query when possible, otherwise cached SQL.
   */
  private static PreviewPipeline buildCompositePreview(
      DataVaultSource recordSource,
      DvCompositeSource source,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    if (source == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingSource"));
    }
    DvCompositeSourceResolver.ResolvedComposite resolved =
        DvCompositeSourceResolver.resolveForSql(source, variables, metadataProvider);
    DatabaseMeta databaseMeta = resolved.databaseMeta();
    if (databaseMeta == null && !Utils.isEmpty(resolved.sharedDatabaseName())) {
      String connectionName =
          variables != null
              ? variables.resolve(resolved.sharedDatabaseName())
              : resolved.sharedDatabaseName();
      try {
        databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
      } catch (Exception e) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "DvSourcePreviewInputSupport.Error.LoadDatabaseConnection", connectionName),
            e);
      }
    }
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.CompositeNoConnection"));
    }
    if (Utils.isEmpty(resolved.sql())) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.CompositeNoSql"));
    }

    String transformName =
        calculateCompositeTransformName(recordSource, source);
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(databaseMeta.getName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, resolved.sql());
    if (rowLimit > 0) {
      tableInputMeta.setRowLimit(Integer.toString(rowLimit));
    }

    PipelineMeta previewMeta =
        PipelinePreviewFactory.generatePreviewPipeline(
            metadataProvider, tableInputMeta, transformName);
    return new PreviewPipeline(previewMeta, transformName);
  }

  private static String calculateCompositeTransformName(
      DataVaultSource recordSource, DvCompositeSource source) {
    String feed =
        recordSource != null && !Utils.isEmpty(recordSource.getName())
            ? recordSource.getName()
            : Const.NVL(source.getSourceQueryName(), "composite");
    return "composite " + feed;
  }

  public static PreviewPipeline buildDatabasePreview(
      DvDatabaseSource source,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    return buildDatabasePreview(null, source, variables, metadataProvider, rowLimit);
  }

  private static PreviewPipeline buildDatabasePreview(
      DataVaultSource recordSource,
      DvDatabaseSource source,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    String connectionName = Const.NVL(source.getDatabaseName(), "").trim();
    if (Utils.isEmpty(connectionName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingDatabaseConnection"));
    }
    String tableName = Const.NVL(source.getTableName(), "").trim();
    if (Utils.isEmpty(tableName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingTableName"));
    }

    DatabaseMeta databaseMeta;
    try {
      databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(connectionName);
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvSourcePreviewInputSupport.Error.LoadDatabaseConnection", connectionName),
          e);
    }
    if (databaseMeta == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvSourcePreviewInputSupport.Error.DatabaseConnectionNotFound", connectionName));
    }

    String transformName = calculateDatabaseTransformName(source);
    String previewSql = DvDatabaseSourcePreviewSupport.buildPreviewSql(databaseMeta, variables, source);
    if (previewSql == null) {
      String qualifiedTable =
          databaseMeta.getQuotedSchemaTableCombination(
              variables, source.getSchemaName(), source.getTableName());
      previewSql = "SELECT * FROM " + qualifiedTable;
    }

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(databaseMeta.getName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, previewSql);
    if (rowLimit > 0) {
      tableInputMeta.setRowLimit(Integer.toString(rowLimit));
    }

    PipelineMeta previewMeta =
        PipelinePreviewFactory.generatePreviewPipeline(metadataProvider, tableInputMeta, transformName);
    return new PreviewPipeline(previewMeta, transformName);
  }

  private static PreviewPipeline buildCsvPreview(
      DataVaultSource recordSource,
      DvCsvSource csvSource,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    String transformName = calculateFileTransformName(recordSource, csvSource);
    ITransformMeta inputMeta;

    if (csvSource.usesSingleFile()) {
      String filename = variables.resolve(csvSource.getSingleFilename());
      if (Utils.isEmpty(filename)) {
        throw new HopException(
            BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingCsvFilename"));
      }
      CsvInputMeta csvMeta = new CsvInputMeta();
      csvMeta.setFilename(filename);
      csvMeta.setDelimiter(variables.resolve(csvSource.getDelimiter()));
      if (csvSource.getEnclosure() != null) {
        csvMeta.setEnclosure(csvSource.getEnclosure());
      }
      if (!Utils.isEmpty(csvSource.getEncoding())) {
        csvMeta.setEncoding(variables.resolve(csvSource.getEncoding()));
      }
      csvMeta.setHeaderPresent(csvSource.isHeaderPresent());
      csvMeta.setInputFields(buildCsvInputFields(csvSource, variables));
      inputMeta = csvMeta;
    } else {
      String folder = variables.resolve(csvSource.getFolder());
      if (Utils.isEmpty(folder)) {
        throw new HopException(
            BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingCsvFolder"));
      }
      TextFileInputMeta textMeta = new TextFileInputMeta();
      TextFileInputMeta.Content content = textMeta.getContent();
      content.setFileType("CSV");
      content.setSeparator(variables.resolve(csvSource.getDelimiter()));
      if (csvSource.getEnclosure() != null) {
        content.setEnclosure(csvSource.getEnclosure());
      }
      if (!Utils.isEmpty(csvSource.getEscapeCharacter())) {
        content.setEscapeCharacter(variables.resolve(csvSource.getEscapeCharacter()));
      }
      if (!Utils.isEmpty(csvSource.getEncoding())) {
        content.setEncoding(variables.resolve(csvSource.getEncoding()));
      }
      content.setHeader(csvSource.isHeaderPresent());
      content.setNrHeaderLines(csvSource.getHeaderLines());
      textMeta.setInputFields(buildTextFileInputFields(csvSource, variables));

      InputFile inputFile = new InputFile();
      inputFile.setFileName(folder);
      inputFile.setFileMask(variables.resolve(csvSource.getIncludeFileMask()));
      inputFile.setExcludeFileMask(variables.resolve(csvSource.getExcludeFileMask()));
      inputFile.setIncludeSubFolders(csvSource.isIncludeSubfolders());
      textMeta.getInputFiles().add(inputFile);
      inputMeta = textMeta;
    }

    PipelineMeta previewMeta =
        PipelinePreviewFactory.generatePreviewPipeline(metadataProvider, inputMeta, transformName);
    return new PreviewPipeline(previewMeta, transformName);
  }

  private static PreviewPipeline buildIcebergPreview(
      DataVaultSource recordSource,
      DvIcebergSource icebergSource,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    String transformName = calculateIcebergTransformName(recordSource, icebergSource, variables);

    IcebergTableInputMeta meta = new IcebergTableInputMeta();
    meta.setCatalogUri(icebergSource.getCatalogUri());
    meta.setWarehouse(icebergSource.getWarehouse());
    meta.setNamespace(icebergSource.getNamespace());
    meta.setTableName(icebergSource.getTableName());
    meta.setSnapshotId(icebergSource.getSnapshotId());
    meta.setBranch(icebergSource.getBranch());
    meta.setS3Endpoint(icebergSource.getS3Endpoint());
    meta.setS3AccessKey(icebergSource.getS3AccessKey());
    meta.setS3SecretKey(icebergSource.getS3SecretKey());
    meta.setFields(buildIcebergInputFields(icebergSource, variables));

    PipelineMeta previewMeta =
        PipelinePreviewFactory.generatePreviewPipeline(metadataProvider, meta, transformName);
    return new PreviewPipeline(previewMeta, transformName);
  }

  private static PreviewPipeline buildParquetPreview(
      DataVaultSource recordSource,
      DvParquetSource parquetSource,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit)
      throws HopException {
    String transformName = calculateFileTransformName(recordSource, parquetSource);

    if (parquetSource.usesSingleFile()) {
      String filename = variables.resolve(parquetSource.getSingleFilename());
      if (Utils.isEmpty(filename)) {
        throw new HopException(
            BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingParquetFilename"));
      }
      ITransformMeta parquetMeta = DvParquetPluginSupport.loadParquetInputMeta();
      DvParquetPluginSupport.configureForMetadataFile(parquetMeta, filename);
      DvParquetPluginSupport.setParquetFields(
          parquetMeta, buildParquetFields(parquetMeta, parquetSource, variables));

      PipelineMeta previewMeta =
          PipelinePreviewFactory.generatePreviewPipeline(metadataProvider, parquetMeta, transformName);
      return new PreviewPipeline(previewMeta, transformName);
    }

    String folder = variables.resolve(parquetSource.getFolder());
    if (Utils.isEmpty(folder)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingParquetFolder"));
    }

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setMetadataProvider(metadataProvider);
    pipelineMeta.setName("Preview pipeline for " + transformName);

    TransformMeta getFileNames = createGetFileNames(parquetSource, variables);
    pipelineMeta.addTransform(getFileNames);

    TransformMeta selectFilename =
        createSelectFilenameOnlyTransform(getFileNames);
    pipelineMeta.addTransform(selectFilename);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(getFileNames, selectFilename));

    ITransformMeta parquetMeta = DvParquetPluginSupport.loadParquetInputMeta();
    DvParquetPluginSupport.configureForFilenameField(
        parquetMeta, PARQUET_FILENAME_FIELD, resolveParquetMetadataFilename(parquetSource, variables));
    DvParquetPluginSupport.setParquetFields(
        parquetMeta, buildParquetFields(parquetMeta, parquetSource, variables));
    TransformMeta parquetTransform =
        DvParquetPluginSupport.newParquetInputTransform(transformName, parquetMeta);
    parquetTransform.setLocation(
        selectFilename.getLocation().x + TRANSFORM_SPACING_X, selectFilename.getLocation().y);
    pipelineMeta.addTransform(parquetTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(selectFilename, parquetTransform));

    TransformMeta removeFilename = createRemoveFilenameTransform(parquetTransform);
    pipelineMeta.addTransform(removeFilename);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(parquetTransform, removeFilename));

    TransformMeta dummyTransform = createDummyTransform(removeFilename);
    pipelineMeta.addTransform(dummyTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(removeFilename, dummyTransform));

    return new PreviewPipeline(pipelineMeta, REMOVE_FILENAME_TRANSFORM_NAME);
  }

  private static TransformMeta createSelectFilenameOnlyTransform(TransformMeta predecessor) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    SelectField selectField = new SelectField();
    selectField.setName(PARQUET_FILENAME_FIELD);
    selectMeta.getSelectOption().getSelectFields().add(selectField);

    TransformMeta transformMeta =
        new TransformMeta("SelectValues", SELECT_FILENAME_TRANSFORM_NAME, selectMeta);
    transformMeta.setLocation(
        predecessor.getLocation().x + TRANSFORM_SPACING_X, predecessor.getLocation().y);
    return transformMeta;
  }

  private static TransformMeta createRemoveFilenameTransform(TransformMeta predecessor) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    DeleteField deleteField = new DeleteField();
    deleteField.setName(PARQUET_FILENAME_FIELD);
    selectMeta.getSelectOption().getDeleteName().add(deleteField);

    TransformMeta transformMeta =
        new TransformMeta("SelectValues", REMOVE_FILENAME_TRANSFORM_NAME, selectMeta);
    transformMeta.setLocation(
        predecessor.getLocation().x + TRANSFORM_SPACING_X, predecessor.getLocation().y);
    return transformMeta;
  }

  private static TransformMeta createDummyTransform(TransformMeta predecessor) throws HopException {
    DummyMeta dummyMeta = new DummyMeta();
    TransformMeta transformMeta =
        new TransformMeta(
            PluginRegistry.getInstance().getPluginId(TransformPluginType.class, dummyMeta),
            DUMMY_TRANSFORM_NAME,
            dummyMeta);
    transformMeta.setLocation(
        predecessor.getLocation().x + TRANSFORM_SPACING_X, predecessor.getLocation().y);
    return transformMeta;
  }

  private static TransformMeta createGetFileNames(DvParquetSource parquetSource, IVariables variables)
      throws HopException {
    GetFileNamesMeta getFileNamesMeta = new GetFileNamesMeta();
    getFileNamesMeta.setDefault();
    getFileNamesMeta.getFilterItemList().clear();
    getFileNamesMeta
        .getFilterItemList()
        .add(new FilterItem(FileTypeFilter.ONLY_FILES.toString()));

    List<FileItem> filesList = new ArrayList<>();
    filesList.add(
        new FileItem(
            variables.resolve(parquetSource.getFolder()),
            variables.resolve(parquetSource.getIncludeFileMask()),
            variables.resolve(parquetSource.getExcludeFileMask()),
            parquetSource.isIncludeSubfolders() ? "Y" : "N",
            "Y"));
    getFileNamesMeta.setFilesList(filesList);

    TransformMeta transformMeta =
        new TransformMeta("GetFileNames", "list parquet files", getFileNamesMeta);
    transformMeta.setLocation(50, 50);
    return transformMeta;
  }

  private static String resolveParquetMetadataFilename(
      DvParquetSource parquetSource, IVariables variables) {
    String folder = variables.resolve(parquetSource.getFolder());
    String mask = variables.resolve(parquetSource.getIncludeFileMask());
    if (!Utils.isEmpty(folder) && !Utils.isEmpty(mask)) {
      String separator = folder.endsWith("/") ? "" : "/";
      return folder + separator + mask.replace("\\.", ".");
    }
    return "";
  }

  private static List<CsvInputField> buildCsvInputFields(DvCsvSource csvSource, IVariables variables)
      throws HopException {
    List<CsvInputField> fields = new ArrayList<>();
    List<SourceField> catalogFields = csvSource.getFields();
    if (catalogFields == null || catalogFields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingSourceFields"));
    }
    for (SourceField sourceField : catalogFields) {
      if (sourceField == null || Utils.isEmpty(sourceField.getName())) {
        continue;
      }
      CsvInputField field = new CsvInputField(variables.resolve(sourceField.getName()));
      DvTextFileInputFieldSupport.applySourceField(field, sourceField);
      fields.add(field);
    }
    if (fields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingSourceFields"));
    }
    return fields;
  }

  private static List<TextFileInputField> buildTextFileInputFields(
      DvCsvSource csvSource, IVariables variables) throws HopException {
    List<TextFileInputField> fields = new ArrayList<>();
    List<SourceField> catalogFields = csvSource.getFields();
    if (catalogFields == null || catalogFields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingSourceFields"));
    }
    for (SourceField sourceField : catalogFields) {
      if (sourceField == null || Utils.isEmpty(sourceField.getName())) {
        continue;
      }
      TextFileInputField field = new TextFileInputField(variables.resolve(sourceField.getName()));
      if (csvSource.isHeaderPresent()) {
        field.setPosition(-1);
      }
      DvTextFileInputFieldSupport.applySourceField(field, sourceField);
      fields.add(field);
    }
    if (fields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingSourceFields"));
    }
    return fields;
  }

  private static List<Object> buildParquetFields(
      ITransformMeta parquetMeta, DvParquetSource parquetSource, IVariables variables)
      throws HopException {
    List<Object> fields = new ArrayList<>();
    List<SourceField> catalogFields = parquetSource.getFields();
    if (catalogFields == null || catalogFields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingSourceFields"));
    }
    for (SourceField sourceField : catalogFields) {
      if (sourceField == null || Utils.isEmpty(sourceField.getName())) {
        continue;
      }
      String name = variables.resolve(sourceField.getName());
      fields.add(toParquetField(parquetMeta, sourceField, name));
    }
    if (fields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingSourceFields"));
    }
    return fields;
  }

  private static Object toParquetField(
      ITransformMeta parquetMeta, SourceField sourceField, String name) throws HopException {
    String typeDesc =
        sourceField.getHopType() > 0
            ? ValueMetaFactory.getValueMetaName(sourceField.getHopType())
            : (!Utils.isEmpty(sourceField.getSourceDataType())
                ? sourceField.getSourceDataType()
                : "String");
    String format = "";
    if (sourceField.getHopType() == IValueMeta.TYPE_DATE) {
      format = "yyyy-MM-dd";
    } else if (sourceField.getHopType() == IValueMeta.TYPE_TIMESTAMP) {
      format = "yyyy-MM-dd HH:mm:ss";
    }
    return DvParquetPluginSupport.createParquetField(
        parquetMeta,
        name,
        name,
        typeDesc,
        format,
        Const.NVL(sourceField.getLength(), ""),
        Const.NVL(sourceField.getPrecision(), ""));
  }

  private static String calculateDatabaseTransformName(DvDatabaseSource source) {
    StringBuilder name = new StringBuilder("source ");
    name.append(source.getDatabaseName());
    name.append(".");
    if (!Utils.isEmpty(source.getSchemaName())) {
      name.append(source.getSchemaName());
      name.append(".");
    }
    name.append(source.getTableName());
    return name.toString();
  }

  private static List<IcebergTableInputField> buildIcebergInputFields(
      DvIcebergSource icebergSource, IVariables variables) throws HopException {
    List<IcebergTableInputField> fields = new ArrayList<>();
    List<SourceField> catalogFields = icebergSource.getFields();
    if (catalogFields == null || catalogFields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingSourceFields"));
    }
    for (SourceField sourceField : catalogFields) {
      if (sourceField == null || Utils.isEmpty(sourceField.getName())) {
        continue;
      }
      int hopType =
          sourceField.getHopType() > 0 ? sourceField.getHopType() : IValueMeta.TYPE_STRING;
      IcebergTableInputField field =
          new IcebergTableInputField(variables.resolve(sourceField.getName()), hopType);
      if (sourceField.getLength() != null) {
        field.setLength(sourceField.getLength());
      }
      if (sourceField.getPrecision() != null) {
        field.setPrecision(sourceField.getPrecision());
      }
      fields.add(field);
    }
    if (fields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvSourcePreviewInputSupport.Error.MissingSourceFields"));
    }
    return fields;
  }

  private static String calculateIcebergTransformName(
      DataVaultSource recordSource, DvIcebergSource icebergSource, IVariables variables) {
    String namespace = variables.resolve(icebergSource.getNamespace());
    String tableName = variables.resolve(icebergSource.getTableName());
    if (!Utils.isEmpty(namespace) && !Utils.isEmpty(tableName)) {
      return "source " + namespace + "." + tableName;
    }
    return "source " + (recordSource != null ? recordSource.getName() : "iceberg");
  }

  private static String calculateFileTransformName(
      DataVaultSource recordSource, IDvFileBasedSource fileSource) {
    if (!Utils.isEmpty(fileSource.getSingleFilename())) {
      return "source " + fileSource.getSingleFilename();
    }
    if (!Utils.isEmpty(fileSource.getFolder())) {
      return "source " + fileSource.getFolder();
    }
    return "source " + (recordSource != null ? recordSource.getName() : "file");
  }
}