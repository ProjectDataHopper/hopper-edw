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
package org.apache.hop.datavault.metadata.coaching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Performs one coaching analysis pass: a single model validation and a single target index build
 * shared across all coaching sources.
 */
public final class CoachingAnalysisSession {

  private final ICoachingModelAdapter adapter;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;

  public CoachingAnalysisSession(
      ICoachingModelAdapter adapter, IVariables variables, IHopMetadataProvider metadataProvider) {
    this.adapter = adapter;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
  }

  public List<CoachingSourceNode> resolve(boolean includeValidation) throws HopException {
    List<CoachingSourceRef> sources =
        dedupeSources(adapter.resolveCoachingSources(variables, metadataProvider));
    if (sources.isEmpty()) {
      return List.of();
    }

    CoachingTargetIndex targetIndex =
        includeValidation ? CoachingTargetIndex.forAdapter(adapter, variables) : null;
    List<ICheckResult> checks =
        includeValidation
            ? CoachingModelCheckSupport.runModelCheck(adapter, variables, metadataProvider)
            : List.of();

    List<CoachingSourceNode> nodes = new ArrayList<>(sources.size());
    for (CoachingSourceRef ref : sources) {
      List<CoachingTargetUsage> targets =
          includeValidation ? targetIndex.targetsFor(ref) : List.of();
      List<CoachingInsight> insights =
          includeValidation
              ? CoachingInsightSupport.buildInsights(adapter, ref, targets, checks, variables)
              : List.of();
      nodes.add(
          CoachingSourceNode.builder()
              .sourceRef(ref)
              .displayLabel(ref.resolvedDisplayLabel())
              .typeLabel(formatTypeLabel(ref))
              .targets(targets)
              .insights(insights)
              .build());
    }
    return nodes;
  }

  public List<CoachingSourceRef> listSources() throws HopException {
    return dedupeSources(adapter.resolveCoachingSources(variables, metadataProvider));
  }

  private static List<CoachingSourceRef> dedupeSources(List<CoachingSourceRef> sources) {
    Map<String, CoachingSourceRef> unique = new LinkedHashMap<>();
    if (sources != null) {
      for (CoachingSourceRef ref : sources) {
        if (ref != null) {
          unique.putIfAbsent(ref.identityKey(), ref);
        }
      }
    }
    return List.copyOf(unique.values());
  }

  private static String formatTypeLabel(CoachingSourceRef ref) {
    if (ref.isDerived()) {
      return ref.getSourceType().name() + " (derived)";
    }
    return ref.getSourceType().name();
  }
}
