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

package org.apache.hop.datavault.lineage;

import java.util.Date;
import java.util.UUID;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvCatalogNamespaces;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAttribute;
import org.apache.hop.datavault.metadata.dimensional.DmFact;
import org.apache.hop.datavault.metadata.dimensional.DmFactDegenerateDimension;
import org.apache.hop.datavault.metadata.dimensional.DmFactDimensionRole;
import org.apache.hop.datavault.metadata.dimensional.DmFactMeasure;
import org.apache.hop.datavault.metadata.dimensional.DmNaturalKeyField;
import org.apache.hop.datavault.metadata.dimensional.DmSourceConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmTableType;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;

/** Builds a {@link LineageSnapshot} from a dimensional (Kimball) model. */
public final class DmModelLineageCollector {

  private DmModelLineageCollector() {}

  public static LineageSnapshot collect(DimensionalModel model, IVariables variables) {
    if (model == null) {
      throw new IllegalArgumentException("Dimensional model is required");
    }

    LineageSnapshot snapshot = new LineageSnapshot();
    snapshot.setId(UUID.randomUUID().toString());
    snapshot.setCapturedAt(new Date());
    snapshot.setProjectKey(DvCatalogNamespaces.resolveProjectKey(variables));
    snapshot.setModelLayer(LineageLayer.DM);
    snapshot.setModelName(model.getName());
    snapshot.setModelFilename(model.getFilename());

    DimensionalConfiguration config = model.getConfigurationOrDefault();
    String targetDb = config != null ? config.getTargetDatabase() : null;

    for (IDmTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      TableLineage tableLineage;
      if (table instanceof DmDimension dimension) {
        tableLineage = collectDimension(dimension, model, variables, targetDb);
      } else if (table instanceof DmFact fact) {
        tableLineage = collectFact(fact, model, variables, targetDb);
      } else {
        tableLineage = collectGeneric(table, model, variables, targetDb);
      }
      if (tableLineage != null) {
        snapshot.addTable(tableLineage);
      }
    }
    return snapshot;
  }

  private static TableLineage collectDimension(
      DmDimension dimension,
      DimensionalModel model,
      IVariables variables,
      String targetDb) {
    TableLineage lineage = baseTable(dimension, model, targetDb, DmTableType.DIMENSION.name());
    addNamingReasons(lineage, dimension);
    addSourceRef(lineage, dimension.getSourceOrDefault(), variables);

    for (DmNaturalKeyField nk : dimension.getNaturalKeysOrEmpty()) {
      if (nk == null || Utils.isEmpty(nk.getFieldName())) {
        continue;
      }
      String target = resolve(nk.getFieldName(), variables);
      FieldLineage field = new FieldLineage(target);
      field.setTechnical(false);
      FieldContribution contribution = sourceContribution(dimension, variables, target, target);
      contribution.addReason(
          LineageReasonFactory.dmRoleMapping(target, "natural key / staging", target));
      field.addContribution(contribution);
      lineage.addField(field);
    }

    for (DmDimensionAttribute attr : dimension.getAttributesOrEmpty()) {
      if (attr == null || Utils.isEmpty(attr.getFieldName())) {
        continue;
      }
      String target = resolve(attr.getFieldName(), variables);
      String sourceField =
          !Utils.isEmpty(attr.getSourceFieldName())
              ? resolve(attr.getSourceFieldName(), variables)
              : target;
      FieldLineage field = new FieldLineage(target);
      field.setTechnical(false);
      FieldContribution contribution =
          sourceContribution(dimension, variables, sourceField, target);
      contribution.setTransform(
          target.equals(sourceField) ? FieldTransform.IDENTITY : FieldTransform.RENAME);
      if (target.equals(sourceField)) {
        contribution.addReason(
            LineageReasonFactory.defaultSameAsSource(target, sourceLabel(dimension)));
      } else {
        contribution.addReason(
            LineageReasonFactory.dmRoleMapping(target, sourceLabel(dimension), sourceField));
      }
      field.addContribution(contribution);
      lineage.addField(field);
    }

    if (!Utils.isEmpty(dimension.getSurrogateKeyField())) {
      String sk = resolve(dimension.getSurrogateKeyField(), variables);
      FieldLineage field = new FieldLineage(sk);
      field.setTechnical(true);
      FieldContribution contribution = new FieldContribution();
      contribution.setSourceKind(TableSourceKind.CONFIG);
      contribution.setSourceName("surrogate key strategy");
      contribution.setTransform(FieldTransform.DERIVED);
      contribution.addReason(
          LineageReasonFactory.standardColumn(
              sk,
              "surrogateKeyField",
              dimension.getSurrogateKeyStrategy() != null
                  ? dimension.getSurrogateKeyStrategy().name()
                  : "surrogate"));
      field.addContribution(contribution);
      lineage.addField(field);
    }

    return lineage;
  }

  private static TableLineage collectFact(
      DmFact fact, DimensionalModel model, IVariables variables, String targetDb) {
    TableLineage lineage = baseTable(fact, model, targetDb, DmTableType.FACT.name());
    addNamingReasons(lineage, fact);
    addSourceRef(lineage, fact.getSourceOrDefault(), variables);

    for (DmFactDimensionRole role : fact.getDimensionRolesOrEmpty()) {
      if (role == null || Utils.isEmpty(role.getForeignKeyColumn())) {
        continue;
      }
      String fk = resolve(role.getForeignKeyColumn(), variables);
      String dim = resolve(role.getDimensionTableName(), variables);
      String sourceField =
          !Utils.isEmpty(role.getSourceFieldName())
              ? resolve(role.getSourceFieldName(), variables)
              : fk;
      if (!Utils.isEmpty(dim)) {
        lineage.addSource(
            new TableSourceRef(TableSourceKind.DM_TABLE, dim, TableSourceRole.OTHER));
      }
      FieldLineage field = new FieldLineage(fk);
      field.setTechnical(false);
      FieldContribution contribution = sourceContribution(fact, variables, sourceField, fk);
      contribution.setTransform(
          fk.equals(sourceField) ? FieldTransform.IDENTITY : FieldTransform.RENAME);
      contribution.addReason(
          LineageReasonFactory.dmRoleMapping(
              fk, "dimension role " + nvl(dim), sourceField));
      field.addContribution(contribution);
      lineage.addField(field);
    }

    for (DmFactMeasure measure : fact.getMeasuresOrEmpty()) {
      if (measure == null || Utils.isEmpty(measure.getFieldName())) {
        continue;
      }
      String target = resolve(measure.getFieldName(), variables);
      FieldLineage field = new FieldLineage(target);
      field.setTechnical(false);
      FieldContribution contribution = sourceContribution(fact, variables, target, target);
      contribution.addReason(
          LineageReasonFactory.dmRoleMapping(target, "fact measure / staging", target));
      field.addContribution(contribution);
      lineage.addField(field);
    }

    for (DmFactDegenerateDimension deg : fact.getDegenerateDimensionsOrEmpty()) {
      if (deg == null || Utils.isEmpty(deg.getFieldName())) {
        continue;
      }
      String target = resolve(deg.getFieldName(), variables);
      FieldLineage field = new FieldLineage(target);
      field.setTechnical(false);
      FieldContribution contribution = sourceContribution(fact, variables, target, target);
      contribution.addReason(
          LineageReasonFactory.dmRoleMapping(target, "degenerate dimension", target));
      field.addContribution(contribution);
      lineage.addField(field);
    }

    return lineage;
  }

  private static TableLineage collectGeneric(
      IDmTable table, DimensionalModel model, IVariables variables, String targetDb) {
    String type = table.getTableType() != null ? table.getTableType().name() : "DM";
    TableLineage lineage = baseTable(table, model, targetDb, type);
    addNamingReasons(lineage, table);
    if (table instanceof org.apache.hop.datavault.metadata.dimensional.DmTableBase base) {
      addSourceRef(lineage, base.getSourceOrDefault(), variables);
    }
    return lineage;
  }

  private static void addSourceRef(
      TableLineage lineage, DmSourceConfiguration source, IVariables variables) {
    if (source == null) {
      return;
    }
    String label = sourceLabelFromConfig(source, variables);
    if (Utils.isEmpty(label)) {
      return;
    }
    TableSourceKind kind = TableSourceKind.CONFIG;
    if (source.isRecordDefinitionSource()) {
      kind = TableSourceKind.DV_SOURCE;
    } else if (source.isFactTableSource()) {
      kind = TableSourceKind.DM_TABLE;
    }
    TableSourceRef ref = new TableSourceRef(kind, label, TableSourceRole.RECORD_SOURCE);
    if (source.isRecordDefinitionSource()
        && !Utils.isEmpty(source.getSourceRecordNamespace())
        && !Utils.isEmpty(source.getSourceRecordName())) {
      ref.setCatalogKey(
          resolve(source.getSourceRecordNamespace(), variables)
              + "/"
              + resolve(source.getSourceRecordName(), variables));
    }
    lineage.addSource(ref);
    lineage.addReason(LineageReasonFactory.feedAttached(label, "dimensional staging source"));
  }

  private static FieldContribution sourceContribution(
      IDmTable table, IVariables variables, String sourceField, String targetField) {
    FieldContribution contribution = new FieldContribution();
    contribution.setSourceKind(TableSourceKind.CONFIG);
    contribution.setSourceName(sourceLabel(table));
    contribution.setSourceFieldName(sourceField);
    contribution.setTransform(
        targetField != null && targetField.equals(sourceField)
            ? FieldTransform.IDENTITY
            : FieldTransform.RENAME);
    if (table instanceof org.apache.hop.datavault.metadata.dimensional.DmTableBase base) {
      DmSourceConfiguration src = base.getSourceOrDefault();
      if (src != null && src.isRecordDefinitionSource()) {
        contribution.setSourceKind(TableSourceKind.DV_SOURCE);
        contribution.setSourceCatalogKey(
            catalogKey(src.getSourceRecordNamespace(), src.getSourceRecordName(), variables));
      }
    }
    return contribution;
  }

  private static String sourceLabel(IDmTable table) {
    if (table instanceof org.apache.hop.datavault.metadata.dimensional.DmTableBase base) {
      return sourceLabelFromConfig(base.getSourceOrDefault(), null);
    }
    return table != null ? table.getName() : "";
  }

  private static String sourceLabelFromConfig(DmSourceConfiguration source, IVariables variables) {
    if (source == null) {
      return "";
    }
    if (source.isSqlSource() && !Utils.isEmpty(source.getSourceSql())) {
      return "SQL staging";
    }
    if (source.isPipelineSource() && !Utils.isEmpty(source.getSourcePipelineFile())) {
      return resolve(source.getSourcePipelineFile(), variables);
    }
    if (source.isRecordDefinitionSource() && !Utils.isEmpty(source.getSourceRecordName())) {
      return resolve(source.getSourceRecordName(), variables);
    }
    if (source.isFactTableSource() && !Utils.isEmpty(source.getSourceFactTableName())) {
      return resolve(source.getSourceFactTableName(), variables);
    }
    return source.resolveSourceType() != null ? source.resolveSourceType().name() : "staging";
  }

  private static String catalogKey(String namespace, String name, IVariables variables) {
    if (Utils.isEmpty(name)) {
      return null;
    }
    String ns = resolve(namespace, variables);
    String n = resolve(name, variables);
    if (Utils.isEmpty(ns)) {
      return n;
    }
    return ns + "/" + n;
  }

  private static TableLineage baseTable(
      IDmTable table, DimensionalModel model, String targetDb, String tableType) {
    TableLineage lineage = new TableLineage();
    lineage.setLayer(LineageLayer.DM);
    lineage.setLogicalName(table.getName());
    String physical =
        !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
    lineage.setPhysicalTableName(physical);
    lineage.setTableType(tableType);
    lineage.setModelName(model.getName());
    lineage.setModelFilename(model.getFilename());
    lineage.setTargetDatabaseMetaName(targetDb);
    lineage.setDescription(table.getDescription());
    return lineage;
  }

  private static void addNamingReasons(TableLineage lineage, IDmTable table) {
    lineage.addReason(
        LineageReasonFactory.userExplicitName(table.getName(), lineage.getPhysicalTableName()));
    if (table.getTableType() != null) {
      lineage.addReason(
          LineageReasonFactory.tableTypeRole(table.getTableType().name(), table.getName()));
    }
  }

  private static String resolve(String value, IVariables variables) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static String nvl(String value) {
    return value != null ? value : "";
  }
}
