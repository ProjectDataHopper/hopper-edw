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
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.fileinput.FileTypeFilter;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.IDvSource;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.SourceField;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.getfilenames.FileItem;
import org.apache.hop.pipeline.transforms.getfilenames.FilterItem;
import org.apache.hop.pipeline.transforms.getfilenames.GetFileNamesMeta;

/** Base pipeline builder for Parquet file Data Vault sources. */
public abstract class DvParquetFileSourcePipelineBuilder extends DvFileSourcePipelineBuilder {

  private static final String FILENAME_FIELD = "filename";

  protected DvParquetSource parquetSource;

  protected DvParquetFileSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      IDvTable dvTable,
      Point startPoint) {
    super(
        variables,
        metadataProvider,
        model,
        pipelineMeta,
        recordSource,
        dvSource,
        dvTable,
        startPoint);
    parquetSource = (DvParquetSource) dvSource;
  }

  @Override
  protected TransformMeta createFileInput(
      String transformName, Point location, ColumnMapping mapping) throws HopException {
    TransformMeta getFileNames = createGetFileNames(location);
    pipelineMeta.addTransform(getFileNames);

    TransformMeta parquetInput = createParquetInput(transformName, location, mapping);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(getFileNames, parquetInput));
    return parquetInput;
  }

  private TransformMeta createGetFileNames(Point location) throws HopException {
    String folder = variables.resolve(parquetSource.getFolder());
    if (Utils.isEmpty(folder)) {
      throw new HopException(
          "Please specify a folder for Parquet source " + recordSource.getName());
    }

    GetFileNamesMeta getFileNamesMeta = new GetFileNamesMeta();
    getFileNamesMeta.setDefault();
    getFileNamesMeta.getFilterItemList().clear();
    getFileNamesMeta.getFilterItemList().add(new FilterItem(FileTypeFilter.ONLY_FILES.toString()));

    List<FileItem> filesList = new ArrayList<>();
    filesList.add(
        new FileItem(
            folder,
            variables.resolve(parquetSource.getIncludeFileMask()),
            variables.resolve(parquetSource.getExcludeFileMask()),
            parquetSource.isIncludeSubfolders() ? "Y" : "N",
            "Y"));
    getFileNamesMeta.setFilesList(filesList);

    TransformMeta transformMeta =
        new TransformMeta("GetFileNames", "list parquet files", getFileNamesMeta);
    transformMeta.setLocation(location.x, location.y);
    return transformMeta;
  }

  private TransformMeta createParquetInput(
      String transformName, Point location, ColumnMapping mapping) throws HopException {
    ITransformMeta parquetMeta = DvParquetPluginSupport.loadParquetInputMeta();
    DvParquetPluginSupport.configureForFilenameField(
        parquetMeta, FILENAME_FIELD, resolveMetadataFilename());
    DvParquetPluginSupport.setParquetFields(parquetMeta, buildParquetFields(parquetMeta, mapping));

    TransformMeta transformMeta =
        DvParquetPluginSupport.newParquetInputTransform(transformName, parquetMeta);
    transformMeta.setLocation(location.x + TRANSFORM_SPACING_X, location.y);
    return transformMeta;
  }

  private String resolveMetadataFilename() {
    if (!Utils.isEmpty(parquetSource.getSingleFilename())) {
      return variables.resolve(parquetSource.getSingleFilename());
    }
    String folder = variables.resolve(parquetSource.getFolder());
    String mask = variables.resolve(parquetSource.getIncludeFileMask());
    if (!Utils.isEmpty(folder) && !Utils.isEmpty(mask)) {
      String separator = folder.endsWith("/") ? "" : "/";
      return folder + separator + mask.replace("\\.", ".");
    }
    return "";
  }

  private List<Object> buildParquetFields(ITransformMeta parquetMeta, ColumnMapping mapping)
      throws HopException {
    List<Object> fields = new ArrayList<>();
    Set<String> mappedSourceNames = mappedSourceNames(mapping);
    List<SourceField> catalogFields = parquetSource.getFields();
    if (catalogFields != null && !catalogFields.isEmpty()) {
      for (SourceField sourceField : catalogFields) {
        if (sourceField == null || Utils.isEmpty(sourceField.getName())) {
          continue;
        }
        String name = variables.resolve(sourceField.getName());
        if (!mappedSourceNames.contains(name)) {
          continue;
        }
        fields.add(toParquetField(parquetMeta, sourceField, name));
      }
    } else {
      for (ColumnRename rename : mapping.renames()) {
        SourceField sourceField = findSourceField(rename.sourceName());
        if (sourceField != null) {
          fields.add(toParquetField(parquetMeta, sourceField, rename.sourceName()));
        }
      }
    }
    if (fields.isEmpty()) {
      throw new HopException(
          "Please define at least one mapped source column for Parquet source "
              + recordSource.getName());
    }
    return fields;
  }

  private Object toParquetField(ITransformMeta parquetMeta, SourceField sourceField, String name)
      throws HopException {
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
        ConstOrEmpty(sourceField.getLength()),
        ConstOrEmpty(sourceField.getPrecision()));
  }

  private static String ConstOrEmpty(String value) {
    return value != null ? value : "";
  }
}
