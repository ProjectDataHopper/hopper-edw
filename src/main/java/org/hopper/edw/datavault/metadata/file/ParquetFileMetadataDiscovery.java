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
package org.hopper.edw.datavault.metadata.file;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.fileinput.FileTypeFilter;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.anon.AnonymousPipelineResults;
import org.apache.hop.pipeline.anon.AnonymousPipelineRunner;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.apache.hop.pipeline.transforms.getfilenames.FileItem;
import org.apache.hop.pipeline.transforms.getfilenames.FilterItem;
import org.apache.hop.pipeline.transforms.getfilenames.GetFileNamesMeta;
import org.apache.hop.pipeline.transforms.metastructure.TransformMetaStructureMeta;
import org.hopper.edw.datavault.metadata.SourceField;

/** Discovers Parquet file layout by running an anonymous Hop pipeline in the Parquet plugin. */
public final class ParquetFileMetadataDiscovery {

  private static final Class<?> PKG = ParquetFileMetadataDiscovery.class;

  private static final String GET_FILE_NAMES_TRANSFORM_NAME = "List parquet file";
  private static final String FILENAME_FIELD = "filename";
  private static final String PARQUET_INPUT_TRANSFORM_NAME = "Parquet file input";
  private static final String META_STRUCTURE_TRANSFORM_NAME = "Metadata structure of stream";
  private static final String CAPTURE_TRANSFORM_NAME = "Capture layout";

  private static final String FIELD_NAME_COLUMN = "Fieldname";
  private static final String TYPE_COLUMN = "Type";
  private static final String LENGTH_COLUMN = "Length";
  private static final String PRECISION_COLUMN = "Precision";

  private ParquetFileMetadataDiscovery() {}

  public record DiscoveryResult(List<SourceField> fields) {}

  public static DiscoveryResult discover(
      String filePath, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(filePath)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ParquetFileMetadataDiscovery.Error.MissingFile"));
    }

    PipelineMeta pipelineMeta = buildDiscoveryPipeline(filePath);
    AnonymousPipelineResults results =
        AnonymousPipelineRunner.executePipeline(
            pipelineMeta, variables, metadataProvider, CAPTURE_TRANSFORM_NAME);

    if (results.getResult() != null && results.getResult().getNrErrors() > 0) {
      throw new HopException(
          BaseMessages.getString(PKG, "ParquetFileMetadataDiscovery.Error.PipelineFailed"));
    }

    List<Object[]> rows = results.getResultRows();
    if (rows == null || rows.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "ParquetFileMetadataDiscovery.Error.NoFields"));
    }

    return new DiscoveryResult(parseRows(results.getResultRowMeta(), rows));
  }

  private static PipelineMeta buildDiscoveryPipeline(String filePath) throws HopException {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("Discover Parquet file metadata");

    GetFileNamesMeta getFileNamesMeta = new GetFileNamesMeta();
    getFileNamesMeta.setDefault();
    getFileNamesMeta.getFilterItemList().clear();
    getFileNamesMeta.getFilterItemList().add(new FilterItem(FileTypeFilter.ONLY_FILES.toString()));
    List<FileItem> filesList = new ArrayList<>();
    filesList.add(new FileItem(filePath, "", "", "N", "Y"));
    getFileNamesMeta.setFilesList(filesList);
    TransformMeta getFileNamesTransform =
        new TransformMeta(GET_FILE_NAMES_TRANSFORM_NAME, getFileNamesMeta);
    getFileNamesTransform.setLocation(50, 100);
    pipelineMeta.addTransform(getFileNamesTransform);

    ITransformMeta parquetMeta = DvParquetPluginSupport.loadParquetInputMeta();
    DvParquetPluginSupport.configureForFilenameField(parquetMeta, FILENAME_FIELD, filePath);
    TransformMeta parquetTransform =
        DvParquetPluginSupport.newParquetInputTransform(PARQUET_INPUT_TRANSFORM_NAME, parquetMeta);
    parquetTransform.setLocation(200, 100);
    pipelineMeta.addTransform(parquetTransform);

    TransformMetaStructureMeta metaStructureMeta = new TransformMetaStructureMeta();
    metaStructureMeta.setDefault();
    metaStructureMeta.setIncludeFieldNameField(true);
    metaStructureMeta.setFieldFieldName(FIELD_NAME_COLUMN);
    metaStructureMeta.setIncludeTypeField(true);
    metaStructureMeta.setTypeFieldName(TYPE_COLUMN);
    metaStructureMeta.setIncludeLengthField(true);
    metaStructureMeta.setLengthFieldName(LENGTH_COLUMN);
    metaStructureMeta.setIncludePrecisionField(true);
    metaStructureMeta.setPrecisionFieldName(PRECISION_COLUMN);
    TransformMeta metaStructureTransform =
        new TransformMeta(META_STRUCTURE_TRANSFORM_NAME, metaStructureMeta);
    metaStructureTransform.setLocation(350, 100);
    pipelineMeta.addTransform(metaStructureTransform);

    DummyMeta dummyMeta = new DummyMeta();
    TransformMeta captureTransform = new TransformMeta(CAPTURE_TRANSFORM_NAME, dummyMeta);
    captureTransform.setLocation(500, 100);
    pipelineMeta.addTransform(captureTransform);

    pipelineMeta.addPipelineHop(new PipelineHopMeta(getFileNamesTransform, parquetTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(parquetTransform, metaStructureTransform));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(metaStructureTransform, captureTransform));
    return pipelineMeta;
  }

  private static List<SourceField> parseRows(IRowMeta rowMeta, List<Object[]> rows)
      throws HopException {
    if (rowMeta == null || rowMeta.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "ParquetFileMetadataDiscovery.Error.NoFields"));
    }

    int nameIndex = rowMeta.indexOfValue(FIELD_NAME_COLUMN);
    int typeIndex = rowMeta.indexOfValue(TYPE_COLUMN);
    int lengthIndex = rowMeta.indexOfValue(LENGTH_COLUMN);
    int precisionIndex = rowMeta.indexOfValue(PRECISION_COLUMN);
    if (nameIndex < 0 || typeIndex < 0) {
      throw new HopException(
          BaseMessages.getString(PKG, "ParquetFileMetadataDiscovery.Error.NoFields"));
    }

    List<SourceField> fields = new ArrayList<>();
    for (Object[] row : rows) {
      String name = asString(row[nameIndex]);
      if (Utils.isEmpty(name) || FILENAME_FIELD.equalsIgnoreCase(name)) {
        continue;
      }
      String typeDesc = asString(row[typeIndex]);
      SourceField field = new SourceField(name);
      field.setDescription("");
      field.setSourceDataType(typeDesc);
      field.setHopType(resolveHopType(typeDesc));
      if (lengthIndex >= 0) {
        field.setLength(formatDimension(asLong(row[lengthIndex])));
      }
      if (precisionIndex >= 0) {
        field.setPrecision(formatDimension(asLong(row[precisionIndex])));
      }
      fields.add(field);
    }

    if (fields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "ParquetFileMetadataDiscovery.Error.NoFields"));
    }
    return fields;
  }

  private static int resolveHopType(String typeDesc) {
    if (Utils.isEmpty(typeDesc)) {
      return IValueMeta.TYPE_STRING;
    }
    try {
      return ValueMetaFactory.getIdForValueMeta(typeDesc);
    } catch (Exception e) {
      return IValueMeta.TYPE_STRING;
    }
  }

  private static String formatDimension(Long value) {
    return value != null && value >= 0 ? Long.toString(value) : "";
  }

  private static Long asLong(Object value) {
    if (value instanceof Long l) {
      return l;
    }
    if (value instanceof Integer i) {
      return i.longValue();
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value != null && !Utils.isEmpty(value.toString())) {
      try {
        return Long.parseLong(value.toString().trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static String asString(Object value) {
    return value != null ? value.toString() : "";
  }
}
