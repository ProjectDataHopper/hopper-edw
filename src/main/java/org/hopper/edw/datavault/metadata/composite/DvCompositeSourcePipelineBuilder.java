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
package org.hopper.edw.datavault.metadata.composite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvSourceFieldMappingSupport;
import org.hopper.edw.datavault.metadata.DvSourcePipelineBuilder;
import org.hopper.edw.datavault.metadata.DvSqlSupport;
import org.hopper.edw.datavault.metadata.IDvSource;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceQueryGenerationMode;
import org.hopper.edw.datavault.metadata.sourcemodel.generate.SourceQueryPipelineGenerator;
import org.jspecify.annotations.NonNull;

/**
 * Base pipeline builder for {@link DvCompositeSource}: SQL Table Input (preferred) or inject Merge
 * Join pipeline when the query cannot be a single-connection SQL.
 */
@Getter
@Setter
public abstract class DvCompositeSourcePipelineBuilder extends DvSourcePipelineBuilder {

  protected DvCompositeSource compositeSource;
  protected DatabaseMeta sourceDbMeta;

  protected DvCompositeSourcePipelineBuilder(
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
    compositeSource = (DvCompositeSource) dvSource;
  }

  @Override
  public void build() throws HopException {
    DvCompositeSourceResolver.ResolvedComposite resolved =
        DvCompositeSourceResolver.resolve(compositeSource, variables, metadataProvider);

    if (resolved.effectiveMode() == SourceQueryGenerationMode.SQL
        || (!Utils.isEmpty(resolved.sql())
            && resolved.effectiveMode() != SourceQueryGenerationMode.PIPELINE)) {
      buildFromSql(resolved);
      return;
    }
    buildFromMergeJoinPipeline(resolved);
  }

  private void buildFromSql(DvCompositeSourceResolver.ResolvedComposite resolved)
      throws HopException {
    String innerSql = resolved.sql();
    if (Utils.isEmpty(innerSql)) {
      throw new HopException(
          "Composite source '" + recordSource.getName() + "' has no SQL (live query or cache)");
    }

    sourceDbMeta = resolved.databaseMeta();
    if (sourceDbMeta == null && !Utils.isEmpty(resolved.sharedDatabaseName())) {
      sourceDbMeta =
          loadDatabaseMeta(
              variables != null
                  ? variables.resolve(resolved.sharedDatabaseName())
                  : resolved.sharedDatabaseName());
    }
    if (sourceDbMeta == null) {
      // Cached SQL without connection name — try fields alone is not enough for Table Input.
      throw new HopException(
          "Composite source '"
              + recordSource.getName()
              + "' SQL mode requires a database connection (open the .hsm or re-publish the query)");
    }

    String querySql = getSql(innerSql);
    Point location = new Point(startPoint.x, startPoint.y);
    String transformName =
        "composite " + ConstNvl(compositeSource.getSourceQueryName(), recordSource.getName());
    TransformMeta sourceTransform =
        createTableInput(transformName, sourceDbMeta, querySql, location);
    pipelineMeta.addTransform(sourceTransform);
    resultTransform = sourceTransform;
  }

  private void buildFromMergeJoinPipeline(DvCompositeSourceResolver.ResolvedComposite resolved)
      throws HopException {
    if (resolved.model() == null || resolved.query() == null) {
      throw new HopException(
          "Composite pipeline generation requires a live .hsm source model for query '"
              + compositeSource.getSourceQueryName()
              + "'");
    }
    PipelineMeta generated =
        SourceQueryPipelineGenerator.generate(
            resolved.model(), resolved.query(), variables, metadataProvider);
    mergeGeneratedPipeline(generated);
  }

  private void mergeGeneratedPipeline(PipelineMeta generated) throws HopException {
    if (generated.getTransforms().isEmpty()) {
      throw new HopException("Generated composite pipeline has no transforms");
    }
    Map<String, String> nameMap = new HashMap<>();
    int index = 0;
    for (TransformMeta transform : generated.getTransforms()) {
      String original = transform.getName();
      String unique = uniqueTransformName(original);
      nameMap.put(original, unique);
      TransformMeta copy = (TransformMeta) transform.clone();
      copy.setName(unique);
      copy.setLocation(startPoint.x + index * TRANSFORM_SPACING_X, startPoint.y);
      pipelineMeta.addTransform(copy);
      index++;
    }
    for (PipelineHopMeta hop : generated.getPipelineHops()) {
      if (hop == null || hop.getFromTransform() == null || hop.getToTransform() == null) {
        continue;
      }
      String from = nameMap.get(hop.getFromTransform().getName());
      String to = nameMap.get(hop.getToTransform().getName());
      if (from == null || to == null) {
        continue;
      }
      TransformMeta fromMeta = pipelineMeta.findTransform(from);
      TransformMeta toMeta = pipelineMeta.findTransform(to);
      if (fromMeta != null && toMeta != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(fromMeta, toMeta));
      }
    }
    // Result = last transform in generated graph that has no outgoing hops (prefer SelectValues).
    resultTransform = findTerminalTransform(generated, nameMap);
    if (resultTransform == null) {
      resultTransform =
          pipelineMeta.findTransform(
              nameMap.get(
                  generated.getTransforms().get(generated.getTransforms().size() - 1).getName()));
    }
  }

  private TransformMeta findTerminalTransform(PipelineMeta generated, Map<String, String> nameMap) {
    for (int i = generated.getTransforms().size() - 1; i >= 0; i--) {
      TransformMeta t = generated.getTransforms().get(i);
      boolean hasOut = false;
      for (PipelineHopMeta hop : generated.getPipelineHops()) {
        if (hop != null
            && hop.getFromTransform() != null
            && t.getName().equals(hop.getFromTransform().getName())) {
          hasOut = true;
          break;
        }
      }
      if (!hasOut) {
        return pipelineMeta.findTransform(nameMap.get(t.getName()));
      }
    }
    return null;
  }

  private String uniqueTransformName(String base) {
    String candidate = base;
    int i = 2;
    while (pipelineMeta.findTransform(candidate) != null) {
      candidate = base + " " + i;
      i++;
    }
    return candidate;
  }

  /**
   * Builds the outer SELECT used by Table Input, wrapping the composite join SQL as a subquery.
   *
   * @param innerSql the multi-table SELECT from {@code SourceQuerySqlGenerator}
   */
  protected abstract String getSql(String innerSql) throws HopException;

  protected void appendComma(StringBuilder sql) {
    sql.append(", ");
  }

  protected void appendFields(StringBuilder sql, List<String> quotedFields) {
    sql.append(String.join(", ", quotedFields));
  }

  protected @NonNull List<String> getQuotedPkFields(DvHub hub, DatabaseMeta databaseMeta)
      throws HopException {
    if (hub.getBusinessKeys().isEmpty()) {
      throw new HopException("Please specify at least one business key in Hub " + hub.getName());
    }
    List<String> pkQuotedFields = new ArrayList<>();
    String sourceName = variables.resolve(recordSource.getName());
    for (BusinessKey key : hub.getBusinessKeysForSource(sourceName, variables)) {
      if (StringUtils.isNotEmpty(key.getSourceFieldName())) {
        pkQuotedFields.add(databaseMeta.quoteField(variables.resolve(key.getSourceFieldName())));
      }
    }
    if (pkQuotedFields.isEmpty()) {
      throw new HopException(
          "Please specify at least one business key mapped to record source "
              + sourceName
              + " in Hub "
              + hub.getName());
    }
    return pkQuotedFields;
  }

  protected void appendSourceField(IDvTable table, StringBuilder sql, DatabaseMeta databaseMeta)
      throws HopException {
    String targetSourceFieldName =
        DvSourceFieldMappingSupport.findTargetSourceFieldName(configuration, null, table);
    String sourceFieldName = variables.resolve(recordSource.getSourceIndicatorField());
    if (StringUtils.isNotEmpty(sourceFieldName)) {
      sql.append(databaseMeta.quoteField(sourceFieldName));
    } else {
      String sourceIndicator = recordSource.getSourceIndicator();
      if (StringUtils.isEmpty(sourceIndicator)) {
        throw new HopException(
            "Please specify a static source indicator or a field in source "
                + recordSource.getName());
      }
      sql.append("'").append(sourceIndicator).append("'");
    }
    sql.append(" AS ").append(databaseMeta.quoteField(targetSourceFieldName));
  }

  protected void appendFromSubquery(StringBuilder sql, String innerSql) {
    sql.append(" FROM (").append(innerSql).append(") composite_src");
  }

  protected TransformMeta createTableInput(
      String sourceTransformName, DatabaseMeta sourceDbMeta, String querySql, Point location) {
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(sourceDbMeta.getName());
    DvSqlSupport.assignDisplaySql(tableInputMeta, querySql);
    TransformMeta transformMeta =
        new TransformMeta("TableInput", sourceTransformName, tableInputMeta);
    transformMeta.setLocation(location.x, location.y);
    return transformMeta;
  }

  private static String ConstNvl(String value, String fallback) {
    return Utils.isEmpty(value) ? fallback : value;
  }
}
