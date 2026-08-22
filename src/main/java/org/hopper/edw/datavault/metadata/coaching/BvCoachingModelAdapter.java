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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvDvTableReference;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Coaching adapter for Business Vault models (DV derivatives only for auto sources). */
public class BvCoachingModelAdapter implements ICoachingModelAdapter {

  public static final String MODELER_TYPE = "BUSINESS_VAULT";

  @Getter private final BusinessVaultModel model;
  private final Consumer<String> tableEditorOpener;
  private final Consumer<String> tableHighlighter;

  public BvCoachingModelAdapter(
      BusinessVaultModel model,
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
    // Prefer BV model configuration catalog; fall back to effective DV models from canvas refs.
    String fromBv = model.getConfigurationOrDefault().getDataCatalogConnection();
    if (!org.apache.hop.core.util.Utils.isEmpty(fromBv)) {
      return fromBv;
    }
    DataVaultModel dvModel =
        BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
            model, variables, metadataProvider);
    if (dvModel == null || dvModel.getTables().isEmpty()) {
      return null;
    }
    return DvSourceCatalogService.resolveCatalogConnection(dvModel, variables, metadataProvider);
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
    sources.addAll(resolveDerivedDvSources());
    return sources;
  }

  @Override
  public List<CoachingTargetUsage> resolveTargetsForSource(
      CoachingSourceRef sourceRef, IVariables variables, IHopMetadataProvider metadataProvider) {
    if (model == null) {
      return List.of();
    }
    return CoachingBvTargetIndex.build(model).targetsFor(sourceRef);
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

  private List<CoachingSourceRef> resolveDerivedDvSources() {
    Set<String> seen = new LinkedHashSet<>();
    List<CoachingSourceRef> derived = new ArrayList<>();
    for (IBvTable table : model.getTables()) {
      if (table == null || table.getDerivatives() == null) {
        continue;
      }
      for (BvDerivativeRef derivative : table.getDerivatives()) {
        addDerivativeRef(derived, seen, derivative);
      }
    }
    for (BvDvTableReference reference : model.getDvReferences()) {
      if (reference == null || Utils.isEmpty(reference.getDvTableName())) {
        continue;
      }
      String key = reference.getDvTableName() + ":" + dvTableTypeName(reference.getDvTableType());
      if (seen.add(key)) {
        derived.add(buildDerivativeRef(reference.getDvTableName(), reference.getDvTableType()));
      }
    }
    return derived;
  }

  private void addDerivativeRef(
      List<CoachingSourceRef> derived, Set<String> seen, BvDerivativeRef derivative) {
    if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
      return;
    }
    String key = derivative.getDvTableName() + ":" + dvTableTypeName(derivative.getDvTableType());
    if (seen.add(key)) {
      derived.add(buildDerivativeRef(derivative.getDvTableName(), derivative.getDvTableType()));
    }
  }

  private CoachingSourceRef buildDerivativeRef(String dvTableName, DvTableType dvTableType) {
    CoachingSourceRef ref = new CoachingSourceRef();
    ref.setSourceType(CoachingSourceType.DV_DERIVATIVE);
    ref.setDerived(true);
    ref.setDerivedDvTableName(dvTableName);
    ref.setDerivedDvTableType(dvTableTypeName(dvTableType));
    ref.setDisplayLabel(dvTableName);
    return ref;
  }

  private static String dvTableTypeName(DvTableType dvTableType) {
    return dvTableType == null ? null : dvTableType.name();
  }
}
