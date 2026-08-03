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
package org.apache.hop.datavault.metadata.coaching;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmSourceConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DmSourceType;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Coaching adapter for dimensional models. */
public class DmCoachingModelAdapter implements ICoachingModelAdapter {

  public static final String MODELER_TYPE = "DIMENSIONAL";

  @Getter private final DimensionalModel model;
  private final Consumer<String> tableEditorOpener;
  private final Consumer<String> tableHighlighter;

  public DmCoachingModelAdapter(
      DimensionalModel model,
      Consumer<String> tableEditorOpener,
      Consumer<String> tableHighlighter) {
    this.model = model;
    this.tableEditorOpener = tableEditorOpener;
    this.tableHighlighter = tableHighlighter;
  }

  @Override
  public String getModelerType() {
    return MODELER_TYPE;
  }

  @Override
  public ModelCoachingConfiguration getCoachingConfiguration() {
    return model.getCoachingOrDefault();
  }

  @Override
  public void setCoachingConfiguration(ModelCoachingConfiguration coaching) {
    model.setCoaching(coaching);
  }

  @Override
  public String resolveCatalogConnectionName(
      IVariables variables, IHopMetadataProvider metadataProvider) throws HopException {
    String configured = model.getConfigurationOrDefault().getDataCatalogConnection();
    if (variables != null) {
      configured = variables.resolve(configured);
    }
    if (!Utils.isEmpty(configured)) {
      return configured;
    }
    return DvSourceCatalogService.resolvePreferredCatalogConnection(
        null, variables, metadataProvider);
  }

  @Override
  public void setCatalogConnectionName(String catalogConnectionName) {
    model.getConfigurationOrDefault().setDataCatalogConnection(catalogConnectionName);
  }

  @Override
  public List<CoachingSourceRef> resolveCoachingSources(
      IVariables variables, IHopMetadataProvider metadataProvider) {
    List<CoachingSourceRef> sources = new ArrayList<>();
    for (CoachingSourceRef ref : getCoachingConfiguration().getCoachingSourcesOrEmpty()) {
      if (ref != null && !ref.isDerived()) {
        sources.add(ref);
      }
    }
    sources.addAll(resolveDerivedTableSources());
    return sources;
  }

  @Override
  public List<CoachingTargetUsage> resolveTargetsForSource(
      CoachingSourceRef sourceRef, IVariables variables, IHopMetadataProvider metadataProvider) {
    if (model == null) {
      return List.of();
    }
    return CoachingDmTargetIndex.build(model).targetsFor(sourceRef);
  }

  @Override
  public List<CoachingInsight> resolveInsightsForSource(
      CoachingSourceRef sourceRef, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (sourceRef == null || model == null) {
      return List.of();
    }
    List<CoachingTargetUsage> targets =
        resolveTargetsForSource(sourceRef, variables, metadataProvider);
    List<ICheckResult> checks =
        CoachingModelCheckSupport.runModelCheck(this, variables, metadataProvider);
    return CoachingInsightSupport.buildInsights(this, sourceRef, targets, checks, variables);
  }

  @Override
  public void openTableEditor(String tableName) {
    if (tableEditorOpener != null) {
      tableEditorOpener.accept(tableName);
    }
  }

  @Override
  public void highlightTableOnCanvas(String tableName) {
    if (tableHighlighter != null) {
      tableHighlighter.accept(tableName);
    }
  }

  private List<CoachingSourceRef> resolveDerivedTableSources() {
    Set<String> seen = new LinkedHashSet<>();
    List<CoachingSourceRef> derived = new ArrayList<>();
    for (IDmTable table : model.getTables()) {
      if (!(table instanceof DmTableBase tableBase)) {
        continue;
      }
      DmSourceConfiguration source = tableBase.getSourceOrDefault();
      if (source == null || source.getSourceType() == null) {
        continue;
      }
      DmSourceType sourceType = source.getSourceType();
      if (sourceType != DmSourceType.SQL && sourceType != DmSourceType.PIPELINE) {
        continue;
      }
      String tableName = table.getTableName();
      String key = sourceType.name() + ":" + tableName;
      if (!seen.add(key)) {
        continue;
      }
      CoachingSourceRef ref = new CoachingSourceRef();
      ref.setDerived(true);
      ref.setDerivedFromTable(tableName);
      ref.setDisplayLabel(tableName + " (" + sourceType.name() + ")");
      if (sourceType == DmSourceType.SQL) {
        ref.setSourceType(CoachingSourceType.SQL);
      } else {
        ref.setSourceType(CoachingSourceType.PIPELINE);
      }
      derived.add(ref);
    }
    return derived;
  }
}
