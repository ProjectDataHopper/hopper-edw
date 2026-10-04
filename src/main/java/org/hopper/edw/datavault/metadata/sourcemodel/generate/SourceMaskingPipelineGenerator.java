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
package org.hopper.edw.datavault.metadata.sourcemodel.generate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.maskfields.MaskField;
import org.apache.hop.pipeline.transforms.maskfields.MaskFieldsMeta;
import org.apache.hop.pipeline.transforms.metainject.MetaInjectMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.DvSqlSupport;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.pipeline.DvPipelineSource;
import org.hopper.edw.datavault.metadata.pipeline.DvPipelineSourceSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJson;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMasking;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMaskingField;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMaskingParentKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourcePipeline;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;

/**
 * Builds a Hop pipeline that materialises a {@link SourceMasking} card: parent feed, optional Mask
 * fields, then a select that keeps the stored field list.
 */
public final class SourceMaskingPipelineGenerator {

  private SourceMaskingPipelineGenerator() {}

  public static PipelineMeta generate(
      SourceModel model,
      SourceMasking masking,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return generate(model, masking, variables, metadataProvider, new HashSet<>());
  }

  private static PipelineMeta generate(
      SourceModel model,
      SourceMasking masking,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Set<String> stack)
      throws HopException {
    if (model == null || masking == null) {
      throw new HopException("Source model and masking source are required");
    }
    String name = masking.getName();
    if (Utils.isEmpty(name)) {
      throw new HopException("Source masking name is required");
    }
    if (!stack.add(name)) {
      throw new HopException("Cyclic source masking parent chain involving '" + name + "'");
    }

    PipelineMeta pipeline =
        generateParentPipeline(model, masking, variables, metadataProvider, stack);
    stack.remove(name);

    List<TransformMeta> transforms = pipeline.getTransforms();
    if (transforms.isEmpty()) {
      throw new HopException("Parent pipeline for masking source '" + name + "' has no transforms");
    }
    TransformMeta last = transforms.get(transforms.size() - 1);
    Point lastLoc = last.getLocation() != null ? last.getLocation() : new Point(100, 100);

    MaskFieldsMeta maskMeta = buildMaskMeta(masking);
    if (maskMeta != null) {
      TransformMeta maskTransform = new TransformMeta("MaskFields", "Mask " + name, maskMeta);
      maskTransform.setLocation(lastLoc.x + 200, lastLoc.y);
      pipeline.addTransform(maskTransform);
      pipeline.addPipelineHop(new PipelineHopMeta(last, maskTransform));
      last = maskTransform;
      lastLoc = maskTransform.getLocation();
    }

    SelectValuesMeta selectMeta = buildSelect(masking);
    if (selectMeta != null) {
      TransformMeta selectTransform = new TransformMeta("SelectValues", "Select " + name, selectMeta);
      selectTransform.setLocation(lastLoc.x + 200, lastLoc.y);
      pipeline.addTransform(selectTransform);
      pipeline.addPipelineHop(new PipelineHopMeta(last, selectTransform));
    }

    pipeline.setName("source-masking-" + name.replace(' ', '_'));
    pipeline.setMetadataProvider(metadataProvider);
    return pipeline;
  }

  private static PipelineMeta generateParentPipeline(
      SourceModel model,
      SourceMasking masking,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Set<String> stack)
      throws HopException {
    SourceMaskingParentKind kind = masking.resolveParentSourceKind();
    String parentName = masking.getParentSourceName();
    if (Utils.isEmpty(parentName)) {
      throw new HopException("Source masking '" + masking.getName() + "' has no parent source name");
    }
    return switch (kind) {
      case TABLE -> generateTableParent(model, parentName, variables, metadataProvider);
      case QUERY -> {
        SourceQuery query = model.findQuery(parentName);
        if (query == null) {
          throw new HopException("Parent query '" + parentName + "' not found");
        }
        yield SourceQueryPipelineGenerator.generate(model, query, variables, metadataProvider);
      }
      case JSON -> {
        SourceJson parentJson = model.findJsonSource(parentName);
        if (parentJson == null) {
          throw new HopException("Parent JSON source '" + parentName + "' not found");
        }
        yield SourceJsonPipelineGenerator.generate(
            model, parentJson, variables, metadataProvider);
      }
      case PIPELINE -> generatePipelineParent(model, parentName, variables, metadataProvider);
      case MASKING -> {
        SourceMasking parent = model.findMaskingSource(parentName);
        if (parent == null) {
          throw new HopException("Parent masking source '" + parentName + "' not found");
        }
        yield generate(model, parent, variables, metadataProvider, stack);
      }
    };
  }

  private static PipelineMeta generateTableParent(
      SourceModel model,
      String tableName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    SourceTable table = model.findTable(tableName);
    if (table == null) {
      throw new HopException("Parent table '" + tableName + "' not found");
    }
    DvSourceType physicalType =
        table.getPhysicalType() != null ? table.getPhysicalType() : DvSourceType.DATABASE;
    if (physicalType != DvSourceType.DATABASE) {
      throw new HopException(
          "Parent table '"
              + tableName
              + "' has physical type "
              + physicalType
              + ". Masking currently supports database SourceTable parents.");
    }
    String connectionName =
        !Utils.isEmpty(table.getDatabaseName())
            ? table.getDatabaseName()
            : model.getConfigurationOrDefault().getDefaultDatabase();
    if (Utils.isEmpty(connectionName)) {
      throw new HopException("No database connection for parent table '" + tableName + "'");
    }
    if (metadataProvider == null) {
      throw new HopException("Metadata provider is required to read parent table '" + tableName + "'");
    }
    DatabaseMeta databaseMeta =
        metadataProvider
            .getSerializer(DatabaseMeta.class)
            .load(variables != null ? variables.resolve(connectionName) : connectionName);
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' not found");
    }

    String schema = table.getSchemaName() == null ? "" : table.getSchemaName();
    String physicalTable = !Utils.isEmpty(table.getTableName()) ? table.getTableName() : tableName;
    String qualified =
        Utils.isEmpty(schema)
            ? databaseMeta.quoteField(physicalTable)
            : databaseMeta.getQuotedSchemaTableCombination(variables, schema, physicalTable);
    String sql = "SELECT * FROM " + qualified;

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("source-masking-parent-" + tableName);
    pipelineMeta.setMetadataProvider(metadataProvider);

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(databaseMeta.getName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, sql);
    TransformMeta source = new TransformMeta("TableInput", "Parent " + tableName, tableInputMeta);
    source.setLocation(100, 100);
    pipelineMeta.addTransform(source);
    return pipelineMeta;
  }

  private static PipelineMeta generatePipelineParent(
      SourceModel model,
      String pipelineName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    SourcePipeline sourcePipeline = model.findPipelineSource(pipelineName);
    if (sourcePipeline == null) {
      throw new HopException("Parent pipeline source '" + pipelineName + "' not found");
    }
    DvPipelineSource dvSource = new DvPipelineSource();
    dvSource.setPipelineFilename(sourcePipeline.getPipelineFilename());
    dvSource.setOutputTransformName(sourcePipeline.getOutputTransformName());
    dvSource.setPipelineRunConfiguration(sourcePipeline.getPipelineRunConfiguration());
    List<SourceField> fields = new ArrayList<>();
    for (SourceColumn column : sourcePipeline.getFields()) {
      if (column == null || Utils.isEmpty(column.getName())) {
        continue;
      }
      SourceField field = new SourceField(column.getName());
      field.setHopType(column.getHopType());
      field.setLength(column.getLength());
      field.setPrecision(column.getPrecision());
      fields.add(field);
    }
    dvSource.setFields(fields);

    MetaInjectMeta metaInjectMeta =
        DvPipelineSourceSupport.buildMetaInjectMeta(dvSource, variables, metadataProvider);
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("source-masking-parent-" + pipelineName);
    pipelineMeta.setMetadataProvider(metadataProvider);
    TransformMeta transform =
        new TransformMeta("MetaInject", "Pipeline " + pipelineName, metaInjectMeta);
    transform.setLocation(100, 100);
    pipelineMeta.addTransform(transform);
    return pipelineMeta;
  }

  private static MaskFieldsMeta buildMaskMeta(SourceMasking masking) {
    List<MaskField> masked = new ArrayList<>();
    for (SourceMaskingField field : masking.getFields()) {
      if (field == null || Utils.isEmpty(field.resolveName()) || !field.hasPattern()) {
        continue;
      }
      masked.add(new MaskField(field.resolveName(), field.getPatternName().trim()));
    }
    if (masked.isEmpty()) {
      return null;
    }
    MaskFieldsMeta meta = new MaskFieldsMeta();
    meta.setFields(masked);
    return meta;
  }

  private static SelectValuesMeta buildSelect(SourceMasking masking) {
    List<SelectField> selectFields = new ArrayList<>();
    for (SourceMaskingField field : masking.getFields()) {
      if (field == null || Utils.isEmpty(field.resolveName())) {
        continue;
      }
      SelectField selectField = new SelectField();
      selectField.setName(field.resolveName());
      selectFields.add(selectField);
    }
    if (selectFields.isEmpty()) {
      return null;
    }
    SelectValuesMeta select = new SelectValuesMeta();
    select.getSelectOption().setSelectFields(selectFields);
    select.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    return select;
  }
}
