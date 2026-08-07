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
package org.apache.hop.datavault.metadata.sourcemodel.generate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DvSqlSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonField;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonParentKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.jsoninput.JsonInputField;
import org.apache.hop.pipeline.transforms.jsoninput.JsonInputMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;

/**
 * Builds a Hop pipeline that materialises a {@link SourceJson} extraction: parent source input +
 * JsonInput (in-field mode) + optional Select Values for final field order/names.
 */
public final class SourceJsonPipelineGenerator {

  private SourceJsonPipelineGenerator() {}

  public static PipelineMeta generate(
      SourceModel model,
      SourceJson jsonSource,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return generate(model, jsonSource, variables, metadataProvider, new HashSet<>());
  }

  private static PipelineMeta generate(
      SourceModel model,
      SourceJson jsonSource,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Set<String> stack)
      throws HopException {
    if (model == null || jsonSource == null) {
      throw new HopException("Source model and JSON source are required");
    }
    String name = jsonSource.getName();
    if (Utils.isEmpty(name)) {
      throw new HopException("Source JSON name is required");
    }
    if (!stack.add(name)) {
      throw new HopException("Cyclic Source JSON parent chain involving '" + name + "'");
    }

    PipelineMeta parentPipeline =
        generateParentPipeline(model, jsonSource, variables, metadataProvider, stack);
    stack.remove(name);

    // Append JsonInput after the last transform of the parent pipeline.
    List<TransformMeta> transforms = parentPipeline.getTransforms();
    if (transforms.isEmpty()) {
      throw new HopException("Parent pipeline for JSON source '" + name + "' has no transforms");
    }
    TransformMeta last = transforms.get(transforms.size() - 1);
    Point lastLoc = last.getLocation() != null ? last.getLocation() : new Point(100, 100);

    JsonInputMeta jsonInputMeta = buildJsonInputMeta(jsonSource);
    TransformMeta jsonTransform = new TransformMeta("JsonInput", "JSON " + name, jsonInputMeta);
    jsonTransform.setLocation(lastLoc.x + 200, lastLoc.y);
    parentPipeline.addTransform(jsonTransform);
    parentPipeline.addPipelineHop(new PipelineHopMeta(last, jsonTransform));

    // Final select/rename so output matches declared field order and aliases.
    SelectValuesMeta selectMeta = buildSelectValues(jsonSource);
    if (selectMeta != null) {
      TransformMeta selectTransform =
          new TransformMeta("SelectValues", "Select " + name, selectMeta);
      selectTransform.setLocation(lastLoc.x + 400, lastLoc.y);
      parentPipeline.addTransform(selectTransform);
      parentPipeline.addPipelineHop(new PipelineHopMeta(jsonTransform, selectTransform));
    }

    parentPipeline.setName(
        "source-json-" + (Utils.isEmpty(name) ? "unnamed" : name.replace(' ', '_')));
    parentPipeline.setMetadataProvider(metadataProvider);
    return parentPipeline;
  }

  private static PipelineMeta generateParentPipeline(
      SourceModel model,
      SourceJson jsonSource,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Set<String> stack)
      throws HopException {
    SourceJsonParentKind kind = jsonSource.resolveParentSourceKind();
    String parentName = jsonSource.getParentSourceName();
    if (Utils.isEmpty(parentName)) {
      throw new HopException(
          "Source JSON '" + jsonSource.getName() + "' has no parent source name");
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
        yield generate(model, parentJson, variables, metadataProvider, stack);
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
    org.apache.hop.datavault.metadata.DvSourceType physicalType =
        table.getPhysicalType() != null
            ? table.getPhysicalType()
            : org.apache.hop.datavault.metadata.DvSourceType.DATABASE;
    if (physicalType != org.apache.hop.datavault.metadata.DvSourceType.DATABASE) {
      // SourceTable does not yet carry CSV/Parquet/Iceberg location properties (file types later
      // on the source modeler). File payloads should land in a database table/query first, or the
      // catalog feed should be used directly outside Source JSON.
      throw new HopException(
          "Parent table '"
              + tableName
              + "' has physical type "
              + physicalType
              + ". JSON extraction currently supports database SourceTable parents (and Source"
              + " Query / Source JSON parents). Stage CSV/Parquet/Iceberg/Kafka payloads into a"
              + " database landing table or compose a Source Query over database tables.");
    }
    String connectionName =
        !Utils.isEmpty(table.getDatabaseName())
            ? table.getDatabaseName()
            : model.getConfigurationOrDefault().getDefaultDatabase();
    if (Utils.isEmpty(connectionName)) {
      throw new HopException(
          "No database connection for parent table '" + tableName + "' (JSON parent)");
    }
    DatabaseMeta databaseMeta =
        metadataProvider
            .getSerializer(DatabaseMeta.class)
            .load(variables != null ? variables.resolve(connectionName) : connectionName);
    if (databaseMeta == null) {
      throw new HopException("Database connection '" + connectionName + "' not found");
    }

    String schema = ConstNvl(table.getSchemaName());
    String physicalTable = !Utils.isEmpty(table.getTableName()) ? table.getTableName() : tableName;
    String qualified =
        Utils.isEmpty(schema)
            ? databaseMeta.quoteField(physicalTable)
            : databaseMeta.getQuotedSchemaTableCombination(variables, schema, physicalTable);
    String sql = "SELECT * FROM " + qualified;

    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("source-json-parent-" + tableName);
    pipelineMeta.setMetadataProvider(metadataProvider);

    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(databaseMeta.getName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, sql);
    TransformMeta source = new TransformMeta("TableInput", "Parent " + tableName, tableInputMeta);
    source.setLocation(100, 100);
    pipelineMeta.addTransform(source);
    return pipelineMeta;
  }

  private static JsonInputMeta buildJsonInputMeta(SourceJson jsonSource) {
    JsonInputMeta meta = new JsonInputMeta();
    meta.setInFields(true);
    meta.setFieldValue(jsonSource.getJsonFieldName());
    meta.setIgnoringMissingPath(jsonSource.isIgnoreMissingPath());
    meta.setDefaultPathLeafToNull(jsonSource.isDefaultPathLeafToNull());
    // Keep parent fields so pass-through columns remain available.
    meta.setRemoveSourceField(false);

    List<JsonInputField> fields = new ArrayList<>();
    for (SourceJsonField field : jsonSource.extractedFields()) {
      if (field == null || Utils.isEmpty(field.getPath())) {
        continue;
      }
      JsonInputField jif = new JsonInputField(field.resolveName());
      jif.setPath(field.getPath());
      jif.setType(field.getHopType());
      jif.setLength(field.getLength());
      jif.setPrecision(field.getPrecision());
      jif.setFormat(field.getFormat());
      jif.setDecimalSymbol(field.getDecimalSymbol());
      jif.setGroupSymbol(field.getGroupSymbol());
      jif.setCurrencySymbol(field.getCurrencySymbol());
      jif.setTrimType(field.getTrimType());
      jif.setRepeated(field.isRepeated());
      fields.add(jif);
    }
    meta.setInputFields(fields);
    return meta;
  }

  private static SelectValuesMeta buildSelectValues(SourceJson jsonSource) {
    List<SourceJsonField> all = jsonSource.getFields();
    if (all.isEmpty()) {
      return null;
    }
    SelectValuesMeta select = new SelectValuesMeta();
    List<SelectField> selectFields = new ArrayList<>();
    for (SourceJsonField field : all) {
      if (field == null) {
        continue;
      }
      String outName = field.resolveName();
      if (Utils.isEmpty(outName)) {
        continue;
      }
      SelectField sf = new SelectField();
      if (field.isPassThrough()) {
        String parent =
            !Utils.isEmpty(field.getParentFieldName()) ? field.getParentFieldName() : outName;
        sf.setName(parent);
        if (!parent.equals(outName)) {
          sf.setRename(outName);
        }
      } else {
        sf.setName(outName);
      }
      selectFields.add(sf);
    }
    if (selectFields.isEmpty()) {
      return null;
    }
    select.getSelectOption().setSelectFields(selectFields);
    select.getSelectOption().setSelectingAndSortingUnspecifiedFields(false);
    return select;
  }

  private static String ConstNvl(String value) {
    return value == null ? "" : value;
  }
}
