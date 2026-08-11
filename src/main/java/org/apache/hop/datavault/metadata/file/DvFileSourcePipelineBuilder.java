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
package org.apache.hop.datavault.metadata.file;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.TextFileInputField;
import org.apache.hop.core.fileinput.InputFile;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvSortRowsSupport;
import org.apache.hop.datavault.metadata.DvSourceFieldMappingSupport;
import org.apache.hop.datavault.metadata.DvSourcePipelineBuilder;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.csvinput.CsvInputField;
import org.apache.hop.pipeline.transforms.csvinput.CsvInputMeta;
import org.apache.hop.pipeline.transforms.fileinput.text.TextFileInputMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.uniquerowsbyhashset.UniqueRowsByHashSetMeta;

/**
 * Base pipeline builder for CSV / delimited file Data Vault sources.
 *
 * <p>Reads files via Text File Input or CSV Input, then applies Select Values (column
 * select/rename), Constant (static record source), and optional Sort Rows / Unique Rows transforms
 * to mirror database SQL semantics.
 */
public abstract class DvFileSourcePipelineBuilder extends DvSourcePipelineBuilder {

  protected IDvSource source;

  protected DvFileSourcePipelineBuilder(
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
    source = dvSource;
  }

  @Override
  public void build() throws HopException {
    Point location = new Point(startPoint.x, startPoint.y);
    String sourceTransformName = calculateTransformName(source);
    ColumnMapping mapping = buildColumnMapping();
    TransformMeta fileInput = createFileInput(sourceTransformName, location, mapping);
    pipelineMeta.addTransform(fileInput);

    TransformMeta current = addSelectValues(fileInput, location, mapping);
    current = coerceMappedFieldTypes(current, location, mapping);
    current = addRecordSourceField(current, location, mapping);
    current = finishSourceChain(current, location, mapping);
    resultTransform = current;
  }

  /** Optional post-processing (sort, distinct, etc.) for hub/link/satellite specifics. */
  protected TransformMeta finishSourceChain(
      TransformMeta predecessor, Point location, ColumnMapping mapping) throws HopException {
    return predecessor;
  }

  protected abstract ColumnMapping buildColumnMapping() throws HopException;

  protected TransformMeta addSortRows(
      TransformMeta predecessor, Point location, List<String> fieldNames) {
    if (predecessor == null || fieldNames == null || fieldNames.isEmpty()) {
      return predecessor;
    }
    SortRowsMeta sortMeta = new SortRowsMeta();
    List<SortRowsField> sortFields = new ArrayList<>();
    for (String fieldName : fieldNames) {
      SortRowsField sortField = new SortRowsField();
      sortField.setFieldName(fieldName);
      sortField.setAscending(true);
      sortFields.add(sortField);
    }
    sortMeta.setSortFields(sortFields);
    DvSortRowsSupport.applyConfiguration(sortMeta, configuration, variables);

    TransformMeta sortTransform = new TransformMeta("SortRows", "sort source rows", sortMeta);
    sortTransform.setLocation(predecessor.getLocation().x + TRANSFORM_SPACING_X, location.y);
    pipelineMeta.addTransform(sortTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, sortTransform));
    return sortTransform;
  }

  protected TransformMeta addUniqueRows(
      TransformMeta predecessor, Point location, List<String> fieldNames) {
    if (predecessor == null || fieldNames == null || fieldNames.isEmpty()) {
      return predecessor;
    }
    UniqueRowsByHashSetMeta uniqueMeta = new UniqueRowsByHashSetMeta();
    List<UniqueRowsByHashSetMeta.CompareField> compareFields = new ArrayList<>();
    for (String fieldName : fieldNames) {
      UniqueRowsByHashSetMeta.CompareField compareField =
          new UniqueRowsByHashSetMeta.CompareField();
      compareField.setName(fieldName);
      compareFields.add(compareField);
    }
    uniqueMeta.setCompareFields(compareFields);

    TransformMeta uniqueTransform =
        new TransformMeta("UniqueRowsByHashSet", "distinct source rows", uniqueMeta);
    uniqueTransform.setLocation(predecessor.getLocation().x + TRANSFORM_SPACING_X, location.y);
    pipelineMeta.addTransform(uniqueTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, uniqueTransform));
    return uniqueTransform;
  }

  private TransformMeta coerceMappedFieldTypes(
      TransformMeta predecessor, Point location, ColumnMapping mapping) throws HopException {
    // Type-only coerce for early stream typing. Length/conversion/rename masks are applied later
    // by DvSourcePipelineBuilder.applyCatalogDataTypeMappings() from catalog SourceField layout.
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
    List<SelectMetadataChange> changes = selectMeta.getSelectOption().getMeta();
    boolean any = false;
    for (ColumnRename rename : mapping.renames()) {
      SourceField sourceField = findSourceField(rename.sourceName());
      if (sourceField == null || sourceField.getHopType() <= 0) {
        continue;
      }
      // Skip type-only when full mapping will re-apply (avoids duplicate meta for conversion fields).
      if (sourceField.getInputOptions() != null
          && sourceField.getInputOptions().getConversion() != null
          && sourceField.getInputOptions().getConversion().hasAnyAttribute()) {
        continue;
      }
      SelectMetadataChange change = new SelectMetadataChange();
      change.setName(rename.targetName());
      change.setType(ValueMetaFactory.getValueMetaName(sourceField.getHopType()));
      if (!Utils.isEmpty(sourceField.getLength())) {
        try {
          change.setLength(Integer.parseInt(sourceField.getLength().trim()));
        } catch (NumberFormatException ignored) {
          // leave default
        }
      }
      if (!Utils.isEmpty(sourceField.getPrecision())) {
        try {
          change.setPrecision(Integer.parseInt(sourceField.getPrecision().trim()));
        } catch (NumberFormatException ignored) {
          // leave default
        }
      }
      changes.add(change);
      any = true;
    }
    if (!any) {
      return predecessor;
    }

    TransformMeta transform = new TransformMeta("SelectValues", "coerce mapped types", selectMeta);
    transform.setLocation(predecessor.getLocation().x + TRANSFORM_SPACING_X, location.y);
    pipelineMeta.addTransform(transform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, transform));
    return transform;
  }

  private TransformMeta addSelectValues(
      TransformMeta predecessor, Point location, ColumnMapping mapping) {
    SelectValuesMeta selectMeta = new SelectValuesMeta();
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();
    for (ColumnRename rename : mapping.renames()) {
      SelectField selectField = new SelectField();
      selectField.setName(rename.sourceName());
      if (!rename.sourceName().equals(rename.targetName())) {
        selectField.setRename(rename.targetName());
      }
      selectFields.add(selectField);
    }

    TransformMeta selectTransform =
        new TransformMeta("SelectValues", "map source columns", selectMeta);
    selectTransform.setLocation(location.x + TRANSFORM_SPACING_X, location.y);
    pipelineMeta.addTransform(selectTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, selectTransform));
    return selectTransform;
  }

  private TransformMeta addRecordSourceField(
      TransformMeta predecessor, Point location, ColumnMapping mapping) throws HopException {
    if (!DvSourceFieldMappingSupport.shouldStoreRecordSource(dvTable)) {
      return predecessor;
    }
    String targetSourceFieldName =
        DvSourceFieldMappingSupport.findTargetSourceFieldName(configuration, recordSource, dvTable);
    String staticRecordSource = DvSourceFieldMappingSupport.resolveRecordSourceValue(recordSource);
    if (staticRecordSource == null) {
      String sourceFieldName = variables.resolve(recordSource.getSourceIndicatorField());
      if (!sourceFieldName.equals(targetSourceFieldName)
          && mapping.streamNames().contains(sourceFieldName)) {
        SelectValuesMeta selectMeta = new SelectValuesMeta();
        selectMeta.getSelectOption().setSelectingAndSortingUnspecifiedFields(true);
        SelectMetadataChange rename = new SelectMetadataChange();
        rename.setName(sourceFieldName);
        rename.setRename(targetSourceFieldName);
        selectMeta.getSelectOption().getMeta().add(rename);

        TransformMeta renameTransform =
            new TransformMeta("SelectValues", "rename record source", selectMeta);
        renameTransform.setLocation(predecessor.getLocation().x + TRANSFORM_SPACING_X, location.y);
        pipelineMeta.addTransform(renameTransform);
        pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, renameTransform));
        return renameTransform;
      }
      return predecessor;
    }

    ConstantMeta constantMeta = new ConstantMeta();
    ConstantField constantField =
        new ConstantField(targetSourceFieldName, "String", staticRecordSource);
    constantMeta.getFields().add(constantField);

    TransformMeta constantTransform =
        new TransformMeta("Constant", "add record source", constantMeta);
    constantTransform.setLocation(predecessor.getLocation().x + TRANSFORM_SPACING_X, location.y);
    pipelineMeta.addTransform(constantTransform);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, constantTransform));
    return constantTransform;
  }

  protected TransformMeta createFileInput(
      String transformName, Point location, ColumnMapping mapping) throws HopException {
    DvCsvSource csvSource = (DvCsvSource) source;
    if (csvSource.usesSingleFile()) {
      return createCsvInput(transformName, location, mapping, csvSource);
    }
    return createTextFileInput(transformName, location, mapping, csvSource);
  }

  private TransformMeta createCsvInput(
      String transformName, Point location, ColumnMapping mapping, DvCsvSource csvSource)
      throws HopException {
    String filename = variables.resolve(csvSource.getSingleFilename());
    if (Utils.isEmpty(filename)) {
      throw new HopException("Please specify a filename for CSV source " + recordSource.getName());
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
    csvMeta.setInputFields(buildCsvInputFields(mapping, csvSource));

    TransformMeta transformMeta = new TransformMeta("CSVInput", transformName, csvMeta);
    transformMeta.setLocation(location.x, location.y);
    return transformMeta;
  }

  private TransformMeta createTextFileInput(
      String transformName, Point location, ColumnMapping mapping, DvCsvSource csvSource)
      throws HopException {
    String folder = variables.resolve(csvSource.getFolder());
    if (Utils.isEmpty(folder)) {
      throw new HopException("Please specify a folder for CSV source " + recordSource.getName());
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
    textMeta.setInputFields(buildTextFileInputFields(mapping, csvSource));

    InputFile inputFile = new InputFile();
    inputFile.setFileName(folder);
    inputFile.setFileMask(variables.resolve(csvSource.getIncludeFileMask()));
    inputFile.setExcludeFileMask(variables.resolve(csvSource.getExcludeFileMask()));
    inputFile.setIncludeSubFolders(csvSource.isIncludeSubfolders());
    textMeta.getInputFiles().add(inputFile);

    TransformMeta transformMeta = new TransformMeta("TextFileInput2", transformName, textMeta);
    transformMeta.setLocation(location.x, location.y);
    return transformMeta;
  }

  private List<CsvInputField> buildCsvInputFields(ColumnMapping mapping, DvCsvSource csvSource)
      throws HopException {
    List<CsvInputField> fields = new ArrayList<>();
    Set<String> mappedSourceNames = mappedSourceNames(mapping);
    List<SourceField> catalogFields = csvSource.getFields();
    if (catalogFields != null && !catalogFields.isEmpty()) {
      for (SourceField sourceField : catalogFields) {
        if (sourceField == null || Utils.isEmpty(sourceField.getName())) {
          continue;
        }
        String name = variables.resolve(sourceField.getName());
        CsvInputField field = new CsvInputField(name);
        if (mappedSourceNames.contains(name)) {
          DvTextFileInputFieldSupport.applySourceField(field, sourceField);
        } else {
          field.setTypeWithString("String");
        }
        fields.add(field);
      }
    } else {
      for (ColumnRename rename : mapping.renames()) {
        SourceField sourceField = findSourceField(rename.sourceName());
        CsvInputField field = new CsvInputField(rename.sourceName());
        if (sourceField != null) {
          DvTextFileInputFieldSupport.applySourceField(field, sourceField);
        }
        fields.add(field);
      }
    }
    if (fields.isEmpty()) {
      throw new HopException(
          "Please define at least one mapped source column for CSV source "
              + recordSource.getName());
    }
    return fields;
  }

  private List<TextFileInputField> buildTextFileInputFields(
      ColumnMapping mapping, DvCsvSource csvSource) throws HopException {
    List<TextFileInputField> fields = new ArrayList<>();
    Set<String> mappedSourceNames = mappedSourceNames(mapping);
    List<SourceField> catalogFields = csvSource.getFields();
    if (catalogFields != null && !catalogFields.isEmpty()) {
      for (SourceField sourceField : catalogFields) {
        if (sourceField == null || Utils.isEmpty(sourceField.getName())) {
          continue;
        }
        String name = variables.resolve(sourceField.getName());
        TextFileInputField field = new TextFileInputField(name);
        if (csvSource.isHeaderPresent()) {
          field.setPosition(-1);
        }
        if (mappedSourceNames.contains(name)) {
          DvTextFileInputFieldSupport.applySourceField(field, sourceField);
        } else {
          field.setTypeWithString("String");
        }
        fields.add(field);
      }
    } else {
      for (ColumnRename rename : mapping.renames()) {
        SourceField sourceField = findSourceField(rename.sourceName());
        TextFileInputField field = new TextFileInputField(rename.sourceName());
        if (csvSource.isHeaderPresent()) {
          field.setPosition(-1);
        }
        if (sourceField != null) {
          DvTextFileInputFieldSupport.applySourceField(field, sourceField);
        }
        fields.add(field);
      }
    }
    if (fields.isEmpty()) {
      throw new HopException(
          "Please define at least one mapped source column for CSV source "
              + recordSource.getName());
    }
    return fields;
  }

  protected Set<String> mappedSourceNames(ColumnMapping mapping) {
    Set<String> mapped = new LinkedHashSet<>();
    for (ColumnRename rename : mapping.renames()) {
      mapped.add(rename.sourceName());
    }
    return mapped;
  }

  protected SourceField findSourceField(String name) {
    if (source.getFields() == null) {
      return null;
    }
    for (SourceField sourceField : source.getFields()) {
      if (sourceField != null && name.equals(variables.resolve(sourceField.getName()))) {
        return sourceField;
      }
    }
    return null;
  }

  protected String calculateTransformName(IDvSource dvSource) {
    if (dvSource instanceof IDvFileBasedSource fileSource) {
      String folder = variables.resolve(fileSource.getFolder());
      if (!Utils.isEmpty(fileSource.getSingleFilename())) {
        return "source " + variables.resolve(fileSource.getSingleFilename());
      }
      if (!Utils.isEmpty(folder)) {
        return "source " + folder;
      }
    }
    return "source " + recordSource.getName();
  }

  protected ColumnMapping columnMapping(
      Map<String, String> sourceToTarget, List<SourceField> catalogFields) throws HopException {
    Set<String> fileColumns = new LinkedHashSet<>();
    Map<String, String> renames = new LinkedHashMap<>();
    List<String> streamNames = new ArrayList<>();

    for (Map.Entry<String, String> entry : sourceToTarget.entrySet()) {
      String sourceName = variables.resolve(entry.getKey());
      String targetName = variables.resolve(entry.getValue());
      if (Utils.isEmpty(sourceName) || Utils.isEmpty(targetName)) {
        continue;
      }
      fileColumns.add(sourceName);
      if (!sourceName.equals(targetName)) {
        renames.put(sourceName, targetName);
      }
      if (!streamNames.contains(targetName)) {
        streamNames.add(targetName);
      }
    }

    List<ColumnRename> renameList = new ArrayList<>();
    for (String sourceName : fileColumns) {
      String targetName = renames.getOrDefault(sourceName, sourceName);
      renameList.add(new ColumnRename(sourceName, targetName));
    }

    return new ColumnMapping(renameList, streamNames);
  }

  protected record ColumnRename(String sourceName, String targetName) {}

  protected record ColumnMapping(List<ColumnRename> renames, List<String> streamNames) {}
}
