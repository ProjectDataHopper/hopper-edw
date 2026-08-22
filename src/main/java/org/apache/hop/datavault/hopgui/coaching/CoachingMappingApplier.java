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
package org.apache.hop.datavault.hopgui.coaching;

import java.util.ArrayList;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.coaching.CoachingSourceRef;
import org.apache.hop.datavault.metadata.coaching.CoachingSourceType;
import org.apache.hop.datavault.metadata.coaching.DvCoachingModelAdapter;
import org.apache.hop.datavault.metadata.coaching.ICoachingModelAdapter;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Seeds DV table metadata from a coaching source before opening the table editor. */
public final class CoachingMappingApplier {

  private CoachingMappingApplier() {}

  public static void apply(
      ICoachingModelAdapter adapter,
      CoachingSourceRef sourceRef,
      String tableName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (!(adapter instanceof DvCoachingModelAdapter dvAdapter)) {
      return;
    }
    if (sourceRef == null
        || sourceRef.getSourceType() != CoachingSourceType.RECORD_DEFINITION
        || Utils.isEmpty(tableName)) {
      return;
    }
    DataVaultModel model = dvAdapter.getModel();
    IDvTable table =
        model.getTables().stream()
            .filter(t -> t != null && tableName.equals(t.getName()))
            .findFirst()
            .orElse(null);
    if (table == null) {
      return;
    }
    String sourceName = variables.resolve(sourceRef.getRecordName());
    if (table instanceof DvHub hub) {
      applyHub(hub, sourceName);
    } else if (table instanceof DvSatellite satellite) {
      satellite.setRecordSourceName(sourceName);
    } else if (table instanceof DvLink link) {
      applyLink(link, sourceName);
    }
    DataVaultSource resolvedSource = null;
    if (metadataProvider != null) {
      resolvedSource =
          DvSourceCatalogService.resolveSource(sourceName, model, variables, metadataProvider);
    }
    if (table instanceof DvHub hub && resolvedSource != null) {
      CoachingHubBusinessKeySupport.populateBusinessKeysFromSource(
          hub, resolvedSource, sourceName, metadataProvider);
    }
  }

  private static void applyHub(DvHub hub, String sourceName) {
    if (hub.getRecordSources() == null) {
      hub.setRecordSources(new ArrayList<>());
    }
    if (!hub.getRecordSources().contains(sourceName)) {
      hub.getRecordSources().add(sourceName);
    }
  }

  private static void applyLink(DvLink link, String sourceName) {
    if (link.getLinkHubSources() == null) {
      link.setLinkHubSources(new ArrayList<>());
    }
    boolean exists =
        link.getLinkHubSources().stream()
            .anyMatch(item -> item != null && sourceName.equals(item.getSourceName()));
    if (!exists) {
      DvLink.DvLinkHubSource hubSource = new DvLink.DvLinkHubSource();
      hubSource.setSourceName(sourceName);
      link.getLinkHubSources().add(hubSource);
    }
  }
}
