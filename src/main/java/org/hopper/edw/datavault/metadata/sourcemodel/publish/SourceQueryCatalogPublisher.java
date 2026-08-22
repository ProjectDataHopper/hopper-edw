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
package org.hopper.edw.datavault.metadata.sourcemodel.publish;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.hopper.edw.catalog.discovery.RecordDefinitionCatalogWriter;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.CatalogModelRegistrySupport;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorOptions;
import org.hopper.edw.datavault.catalog.RecordSourceIndicatorSupport;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvSourceDeliveryType;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.composite.DvCompositeSource;
import org.hopper.edw.datavault.metadata.datatypemapping.PhysicalSourceField;
import org.hopper.edw.datavault.metadata.datatypemapping.SourceDataTypeMappingPublishSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQuery;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryGenerationMode;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceQueryGenerationSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceQuerySqlGenerator;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlEngine;
import org.hopper.edw.datavault.virtualization.sql.SourceModelSqlPlan;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Publishes a {@link SourceQuery} as a catalog {@code DV_SOURCE} of type {@code COMPOSITE}. */
public final class SourceQueryCatalogPublisher {

  private static final Class<?> PKG = SourceQueryCatalogPublisher.class;

  private SourceQueryCatalogPublisher() {}

  public record PublishResult(String catalogName, boolean usedSqlCache, String message) {}

  public static PublishResult publish(
      SourceModel model,
      SourceQuery query,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null || query == null) {
      throw new HopException("Source model and query are required to publish");
    }
    if (Utils.isEmpty(query.getName())) {
      throw new HopException("Source query name is required to publish");
    }
    boolean freeSql = query.resolveGenerationMode() == SourceQueryGenerationMode.FREE_SQL;
    if (!freeSql && query.getColumns().isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "SourceQueryCatalogPublisher.Error.EmptyProjection", query.getName()));
    }
    if (freeSql && Utils.isEmpty(query.getFreeSql())) {
      throw new HopException(
          "Source query '" + query.getName() + "' is Free SQL mode but free SQL text is empty");
    }

    String catalogConnection = resolveCatalogConnection(model, catalogConnectionName, variables);
    if (Utils.isEmpty(catalogConnection)) {
      throw new HopException(
          BaseMessages.getString(PKG, "SourceQueryCatalogPublisher.Error.NoCatalogConnection"));
    }

    String feedName =
        !Utils.isEmpty(query.getPublishedCatalogName())
            ? query.getPublishedCatalogName().trim()
            : query.getName().trim();

    List<SourceField> fields;
    if (freeSql) {
      fields = buildFieldsFromFreeSql(model, query, variables, metadataProvider);
      fields = applyDataTypeMappings(query, fields, metadataProvider);
    } else {
      fields = buildFieldsFromProjection(model, query, metadataProvider);
    }
    RecordSourceIndicatorOptions indicatorOptions =
        RecordSourceIndicatorSupport.resolveForTable(null, fields, feedName);

    DvCompositeSource composite = new DvCompositeSource();
    composite.setDescription(
        !Utils.isEmpty(query.getDescription())
            ? query.getDescription()
            : "Source query "
                + query.getName()
                + " from "
                + Const.NVL(model.getName(), "source model"));
    composite.setFields(fields);
    String modelFilename = model.getFilename();
    if (Utils.isEmpty(modelFilename)) {
      modelFilename = model.getName();
    }
    composite.setSourceModelFilename(
        CatalogModelRegistrySupport.portableModelPath(modelFilename, variables));
    composite.setSourceQueryName(query.getName());

    // Cache SQL when single-connection generation is available.
    if (freeSql) {
      try {
        var plan =
            org.hopper.edw.datavault.virtualization.sql.SourceModelSqlEngine.plan(
                model, query.getFreeSql(), variables, metadataProvider);
        if (plan.fullPushdown()
            && plan.pushdownSqlFragments() != null
            && !plan.pushdownSqlFragments().isEmpty()) {
          composite.setGeneratedSql(plan.pushdownSqlFragments().get(0));
        }
      } catch (Exception ignored) {
        // Cache is optional; live .hsm is preferred at load time.
      }
    } else if (SourceQueryGenerationSupport.canGenerateSingleConnectionSql(model, query)) {
      String dbName = SourceQueryGenerationSupport.resolveSharedDatabaseName(model, query);
      DatabaseMeta databaseMeta =
          metadataProvider
              .getSerializer(DatabaseMeta.class)
              .load(variables != null ? variables.resolve(dbName) : dbName);
      if (databaseMeta != null) {
        composite.setGeneratedSql(
            SourceQuerySqlGenerator.generate(model, query, databaseMeta, variables));
      }
    }

    DataVaultSource dataVaultSource = new DataVaultSource(feedName);
    dataVaultSource.setSource(composite);
    dataVaultSource.setSourceIndicator(indicatorOptions.getStaticValue());
    dataVaultSource.setSourceIndicatorField(indicatorOptions.getFieldName());
    dataVaultSource.setDeliveryType(DvSourceDeliveryType.CHANGES_ONLY);

    RecordDefinitionCatalogWriter.upsertDataVaultSource(
        dataVaultSource, catalogConnection, null, variables, metadataProvider, null, null, null);

    query.setPublishedCatalogName(feedName);
    return new PublishResult(
        feedName,
        !Utils.isEmpty(composite.getGeneratedSql()),
        "Published composite feed '" + feedName + "'");
  }

  public static List<PublishResult> publishAll(
      SourceModel model,
      String catalogConnectionName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    List<PublishResult> results = new ArrayList<>();
    if (model == null) {
      return results;
    }
    for (SourceQuery query : model.getQueries()) {
      if (query == null || Utils.isEmpty(query.getName())) {
        continue;
      }
      boolean freeSql = query.resolveGenerationMode() == SourceQueryGenerationMode.FREE_SQL;
      if (!freeSql && query.getColumns().isEmpty()) {
        continue;
      }
      if (freeSql && Utils.isEmpty(query.getFreeSql())) {
        continue;
      }
      results.add(publish(model, query, catalogConnectionName, variables, metadataProvider));
    }
    return results;
  }

  public static List<SourceField> buildFieldsFromFreeSql(
      SourceModel model,
      SourceQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    SourceModelSqlPlan plan =
        SourceModelSqlEngine.plan(model, query.getFreeSql(), variables, metadataProvider);
    List<SourceField> fields = new ArrayList<>();
    if (plan.outputRowMeta() == null) {
      return fields;
    }
    for (int i = 0; i < plan.outputRowMeta().size(); i++) {
      var valueMeta = plan.outputRowMeta().getValueMeta(i);
      SourceField field = new SourceField(valueMeta.getName());
      field.setHopType(valueMeta.getType());
      field.setLength(valueMeta.getLength() > 0 ? Integer.toString(valueMeta.getLength()) : null);
      field.setPrecision(
          valueMeta.getPrecision() >= 0 ? Integer.toString(valueMeta.getPrecision()) : null);
      fields.add(field);
    }
    // Prefer explicit projection key positions when the modeler filled columns as metadata.
    if (!query.getColumns().isEmpty()) {
      Map<String, Integer> keys = new HashMap<>();
      for (SourceQueryColumn column : query.getColumns()) {
        if (column != null && column.isPrimaryKey()) {
          keys.put(column.resolveAlias().toLowerCase(Locale.ROOT), column.getPrimaryKeyPosition());
        }
      }
      for (SourceField field : fields) {
        Integer pos = keys.get(field.getName().toLowerCase(Locale.ROOT));
        if (pos != null) {
          field.setPrimaryKeyPosition(pos);
        }
      }
    }
    return fields;
  }

  public static List<SourceField> buildFieldsFromProjection(SourceModel model, SourceQuery query) {
    try {
      return buildFieldsFromProjection(model, query, null);
    } catch (HopException e) {
      return new ArrayList<>();
    }
  }

  public static List<SourceField> buildFieldsFromProjection(
      SourceModel model, SourceQuery query, IHopMetadataProvider metadataProvider)
      throws HopException {
    SourceTable driving =
        model != null && query != null && !Utils.isEmpty(query.getDrivingTableName())
            ? model.findTable(query.getDrivingTableName())
            : null;
    // Enrich physical fields from all participant tables, not only driving.
    List<PhysicalSourceField> physical = physicalFieldsFromQuery(model, query);
    return SourceDataTypeMappingPublishSupport.toEffectiveSourceFields(
        query, physical, metadataProvider);
  }

  private static List<PhysicalSourceField> physicalFieldsFromQuery(
      SourceModel model, SourceQuery query) {
    List<PhysicalSourceField> fields = new ArrayList<>();
    if (query == null) {
      return fields;
    }
    for (SourceQueryColumn column : query.getColumns()) {
      if (column == null || Utils.isEmpty(column.getColumnName())) {
        continue;
      }
      PhysicalSourceField physical = new PhysicalSourceField();
      physical.setName(column.resolveAlias());
      SourceTable table =
          model != null && !Utils.isEmpty(column.getTableName())
              ? model.findTable(column.getTableName())
              : null;
      SourceColumn sourceColumn = table != null ? table.findColumn(column.getColumnName()) : null;
      if (sourceColumn != null) {
        physical.setDescription(sourceColumn.getDescription());
        physical.setSourceDataType(sourceColumn.getSourceDataType());
        physical.setLength(sourceColumn.getLength());
        physical.setPrecision(sourceColumn.getPrecision());
        physical.setHopType(sourceColumn.getHopType());
      } else {
        physical.setHopType(2);
      }
      if (column.isPrimaryKey()) {
        physical.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
      } else if (sourceColumn != null
          && sourceColumn.isPrimaryKey()
          && column.resolveAlias().equalsIgnoreCase(sourceColumn.getName())) {
        physical.setPrimaryKeyPosition(sourceColumn.getPrimaryKeyPosition());
      }
      fields.add(physical);
    }
    return fields;
  }

  private static List<SourceField> applyDataTypeMappings(
      SourceQuery query, List<SourceField> baseFields, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (query == null || baseFields == null || baseFields.isEmpty()) {
      return baseFields != null ? baseFields : List.of();
    }
    List<PhysicalSourceField> physical = new ArrayList<>();
    for (SourceField field : baseFields) {
      PhysicalSourceField p = PhysicalSourceField.from(field);
      if (p != null) {
        physical.add(p);
      }
    }
    return SourceDataTypeMappingPublishSupport.toEffectiveSourceFields(
        query, physical, metadataProvider);
  }

  private static String resolveCatalogConnection(
      SourceModel model, String override, IVariables variables) {
    String catalogConnection = Const.NVL(override, "");
    if (variables != null) {
      catalogConnection = variables.resolve(catalogConnection);
    }
    if (Utils.isEmpty(catalogConnection) && model != null) {
      catalogConnection = Const.NVL(model.getConfigurationOrDefault().getCatalogConnection(), "");
      if (variables != null) {
        catalogConnection = variables.resolve(catalogConnection);
      }
    }
    return catalogConnection;
  }
}
