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
package org.hopper.edw.datavault.metadata.coaching;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.metadata.DataVaultModel;

/** Coaching adapter for Data Vault models. */
public class DvCoachingModelAdapter implements ICoachingModelAdapter {

  public static final String MODELER_TYPE = "DATA_VAULT";

  @Getter private final DataVaultModel model;
  private final Consumer<String> tableEditorOpener;
  private final Consumer<String> tableHighlighter;

  public DvCoachingModelAdapter(
      DataVaultModel model, Consumer<String> tableEditorOpener, Consumer<String> tableHighlighter) {
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
    return DvSourceCatalogService.resolveCatalogConnection(model, variables, metadataProvider);
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
    return sources;
  }

  @Override
  public List<CoachingTargetUsage> resolveTargetsForSource(
      CoachingSourceRef sourceRef, IVariables variables, IHopMetadataProvider metadataProvider) {
    if (model == null) {
      return List.of();
    }
    return CoachingDvTargetIndex.build(model, variables).targetsFor(sourceRef, variables);
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

  public RecordDefinitionKey toRecordDefinitionKey(CoachingSourceRef ref, IVariables variables) {
    String namespace =
        Utils.isEmpty(ref.getRecordNamespace())
            ? DvCatalogNamespaces.projectSourcesNamespace(variables)
            : variables.resolve(ref.getRecordNamespace());
    return new RecordDefinitionKey(namespace, variables.resolve(ref.getRecordName()));
  }
}
