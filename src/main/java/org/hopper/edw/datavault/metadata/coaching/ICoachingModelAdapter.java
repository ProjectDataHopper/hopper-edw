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

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Modeler-specific coaching behaviour for the coach panel. */
public interface ICoachingModelAdapter {

  String getModelerType();

  ModelCoachingConfiguration getCoachingConfiguration();

  void setCoachingConfiguration(ModelCoachingConfiguration coaching);

  String resolveCatalogConnectionName(IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException;

  void setCatalogConnectionName(String catalogConnectionName);

  List<CoachingSourceRef> resolveCoachingSources(
      IVariables variables, IHopMetadataProvider metadataProvider) throws HopException;

  List<CoachingTargetUsage> resolveTargetsForSource(
      CoachingSourceRef sourceRef, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException;

  List<CoachingInsight> resolveInsightsForSource(
      CoachingSourceRef sourceRef, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException;

  void openTableEditor(String tableName);

  void highlightTableOnCanvas(String tableName);
}
