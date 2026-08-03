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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.BusinessKeySource;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.SatelliteAttribute;

/** Inverted index of DV table usage keyed by resolved record source name. */
public final class CoachingDvTargetIndex {

  private final Map<String, List<CoachingTargetUsage>> targetsByResolvedSourceName;

  private CoachingDvTargetIndex(
      Map<String, List<CoachingTargetUsage>> targetsByResolvedSourceName) {
    this.targetsByResolvedSourceName = targetsByResolvedSourceName;
  }

  public static CoachingDvTargetIndex build(DataVaultModel model, IVariables variables) {
    Map<String, List<CoachingTargetUsage>> index = new HashMap<>();
    if (model == null || model.getTables() == null) {
      return new CoachingDvTargetIndex(index);
    }
    for (IDvTable table : model.getTables()) {
      if (table instanceof DvHub hub) {
        indexHubTargets(index, hub, variables);
      } else if (table instanceof DvSatellite satellite) {
        indexSatelliteTarget(index, satellite, variables);
      } else if (table instanceof DvLink link) {
        indexLinkTargets(index, link, variables);
      }
    }
    return new CoachingDvTargetIndex(index);
  }

  public List<CoachingTargetUsage> targetsFor(CoachingSourceRef sourceRef, IVariables variables) {
    if (sourceRef == null || Utils.isEmpty(sourceRef.getRecordName())) {
      return List.of();
    }
    String resolvedSourceName = variables.resolve(sourceRef.getRecordName());
    return targetsByResolvedSourceName.getOrDefault(resolvedSourceName, List.of());
  }

  private static void indexHubTargets(
      Map<String, List<CoachingTargetUsage>> index, DvHub hub, IVariables variables) {
    if (hub.getRecordSources() == null) {
      return;
    }
    CoachingTargetUsage target = buildHubTarget(hub);
    for (String sourceName : hub.getRecordSources()) {
      if (!Utils.isEmpty(sourceName)) {
        appendTarget(index, variables.resolve(sourceName), target);
      }
    }
  }

  private static void indexSatelliteTarget(
      Map<String, List<CoachingTargetUsage>> index, DvSatellite satellite, IVariables variables) {
    String sourceName = satellite.getRecordSourceName();
    if (Utils.isEmpty(sourceName)) {
      return;
    }
    appendTarget(index, variables.resolve(sourceName), buildSatelliteTarget(satellite));
  }

  private static void indexLinkTargets(
      Map<String, List<CoachingTargetUsage>> index, DvLink link, IVariables variables) {
    if (link.getLinkHubSources() == null) {
      return;
    }
    for (DvLink.DvLinkHubSource hubSource : link.getLinkHubSources()) {
      if (hubSource == null || Utils.isEmpty(hubSource.getSourceName())) {
        continue;
      }
      appendTarget(
          index, variables.resolve(hubSource.getSourceName()), buildLinkTarget(link, hubSource));
    }
  }

  private static CoachingTargetUsage buildHubTarget(DvHub hub) {
    int mappedKeys = 0;
    int totalKeys = 0;
    Set<String> mappedFields = new LinkedHashSet<>();
    if (hub.getBusinessKeys() != null) {
      for (BusinessKey bk : hub.getBusinessKeys()) {
        if (bk == null || Utils.isEmpty(bk.getName())) {
          continue;
        }
        totalKeys++;
        if (!Utils.isEmpty(bk.getSourceFieldName())) {
          mappedKeys++;
          mappedFields.add(bk.getSourceFieldName());
        }
      }
    }
    return CoachingTargetUsage.builder()
        .tableName(hub.getName())
        .tableRole(DvTableType.HUB.name())
        .summary(mappedKeys + "/" + totalKeys + " keys mapped")
        .mappedFields(new ArrayList<>(mappedFields))
        .build();
  }

  private static CoachingTargetUsage buildSatelliteTarget(DvSatellite satellite) {
    int mappedAttrs = 0;
    Set<String> mappedFields = new LinkedHashSet<>();
    if (satellite.getAttributes() != null) {
      for (SatelliteAttribute attribute : satellite.getAttributes()) {
        if (attribute != null && !Utils.isEmpty(attribute.getName())) {
          mappedAttrs++;
          mappedFields.add(attribute.getName());
        }
      }
    }
    return CoachingTargetUsage.builder()
        .tableName(satellite.getName())
        .tableRole(DvTableType.SATELLITE.name())
        .summary(mappedAttrs + " attributes")
        .mappedFields(new ArrayList<>(mappedFields))
        .build();
  }

  private static CoachingTargetUsage buildLinkTarget(
      DvLink link, DvLink.DvLinkHubSource hubSource) {
    Set<String> mappedFields = new LinkedHashSet<>();
    if (hubSource.getHubSourceKeyFields() != null) {
      for (DvLink.HubSourceKeyField keyField : hubSource.getHubSourceKeyFields()) {
        if (keyField == null || keyField.getSourceBusinessKeyFields() == null) {
          continue;
        }
        for (BusinessKeySource source : keyField.getSourceBusinessKeyFields()) {
          if (source != null && !Utils.isEmpty(source.getSourceFieldName())) {
            mappedFields.add(source.getSourceFieldName());
          }
        }
      }
    }
    return CoachingTargetUsage.builder()
        .tableName(link.getName())
        .tableRole(DvTableType.LINK.name())
        .summary(mappedFields.size() + " hub key mappings")
        .mappedFields(new ArrayList<>(mappedFields))
        .build();
  }

  private static void appendTarget(
      Map<String, List<CoachingTargetUsage>> index, String sourceKey, CoachingTargetUsage target) {
    index.computeIfAbsent(sourceKey, key -> new ArrayList<>()).add(target);
  }
}
