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
package org.hopper.edw.datavault.metadata.datatypemapping;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DependentChildKey;
import org.hopper.edw.datavault.metadata.DrivingKeySource;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvLinkHubSourceKeyFieldSupport;
import org.hopper.edw.datavault.metadata.DvReferenceTable;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvSatelliteParentKeySupport;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;

/**
 * Stream field names produced by generated DV source SQL / file projections. Catalog data type
 * mappings must be restricted to this set: hubs and links do not read satellite attributes or
 * source audit columns such as {@code load_date}. Satellites do include parent-key hash inputs.
 */
public final class DvCatalogMappingStreamFieldsSupport {

  private DvCatalogMappingStreamFieldsSupport() {}

  public static List<String> expectedStreamFieldNames(
      IDvTable table,
      DataVaultSource recordSource,
      DataVaultModel model,
      IVariables variables,
      String targetRecordSourceField,
      DvLink.DvLinkHubSource linkHubSource) {
    List<String> names = new ArrayList<>();
    if (table instanceof DvHub hub) {
      addHubFields(names, hub, recordSource, variables);
      addField(names, targetRecordSourceField, variables);
    } else if (table instanceof DvLink link) {
      addLinkFields(names, link, recordSource, model, variables, linkHubSource);
      addField(names, targetRecordSourceField, variables);
    } else if (table instanceof DvSatellite satellite) {
      addSatelliteHashInputFields(names, satellite, recordSource, model, variables, linkHubSource);
      addSatelliteFields(names, satellite, variables);
      if (satellite.isStoreRecordSource()) {
        addField(names, targetRecordSourceField, variables);
      }
    } else if (table instanceof DvReferenceTable reference) {
      addReferenceFields(names, reference, recordSource, variables);
      addField(names, targetRecordSourceField, variables);
    }
    return names;
  }

  private static void addHubFields(
      List<String> names, DvHub hub, DataVaultSource recordSource, IVariables variables) {
    if (hub == null || recordSource == null) {
      return;
    }
    String sourceName = resolve(recordSource.getName(), variables);
    for (BusinessKey key : hub.getBusinessKeysForSource(sourceName, variables)) {
      addBusinessKeyFields(names, key, variables);
    }
  }

  private static void addLinkFields(
      List<String> names,
      DvLink link,
      DataVaultSource recordSource,
      DataVaultModel model,
      IVariables variables,
      DvLink.DvLinkHubSource linkHubSource) {
    if (link == null) {
      return;
    }
    DvLink.DvLinkHubSource hubSource = linkHubSource;
    if (hubSource == null) {
      hubSource = findLinkHubSource(link, recordSource, variables);
    }
    if (hubSource != null && link.getHubNames() != null) {
      for (String hubName : link.getHubNames()) {
        if (Utils.isEmpty(hubName)) {
          continue;
        }
        DvHub hub = model != null ? model.findHub(hubName) : null;
        DvLink.HubSourceKeyField keyField =
            DvLinkHubSourceKeyFieldSupport.findHubSourceKeyFieldOrNull(hubSource, hubName);
        if (hub != null) {
          for (DvLinkHubSourceKeyFieldSupport.ResolvedBusinessKeySource resolved :
              DvLinkHubSourceKeyFieldSupport.resolveBusinessKeySources(hub, keyField, variables)) {
            addField(names, resolved.getSourceFieldName(), variables);
          }
        }
        if (keyField != null && keyField.getDrivingKeySources() != null) {
          for (DrivingKeySource driving : keyField.getDrivingKeySources()) {
            if (driving != null) {
              addField(names, driving.getSourceField(), variables);
            }
          }
        }
      }
    }
    if (link.getDependentChildKeys() != null) {
      for (DependentChildKey childKey : link.getDependentChildKeys()) {
        if (childKey != null) {
          addField(names, childKey.resolveSourceFieldName(), variables);
        }
      }
    }
  }

  private static void addSatelliteFields(
      List<String> names, DvSatellite satellite, IVariables variables) {
    if (satellite.getAttributes() == null) {
      return;
    }
    for (SatelliteAttribute attribute : satellite.getAttributes()) {
      if (attribute != null) {
        addField(names, attribute.getName(), variables);
      }
    }
  }

  /**
   * Parent-key / link-key columns used as DvHashKey inputs. Catalog mappings must include these
   * (not only satellite attributes) so Integer business keys keep the same conversion as hub loads.
   */
  private static void addSatelliteHashInputFields(
      List<String> names,
      DvSatellite satellite,
      DataVaultSource recordSource,
      DataVaultModel model,
      IVariables variables,
      DvLink.DvLinkHubSource linkHubSource) {
    if (satellite == null || model == null) {
      return;
    }
    if (!Utils.isEmpty(satellite.getHubName())) {
      DvHub hub = model.findHub(satellite.getHubName());
      if (hub == null) {
        return;
      }
      try {
        for (DvSatelliteParentKeySupport.ParentKeyField field :
            DvSatelliteParentKeySupport.resolveParentKeyFields(hub, satellite, variables)) {
          addField(names, field.getBusinessKeyName(), variables);
        }
      } catch (Exception ignored) {
        // Best-effort stream filter; missing hub keys skip catalog mapping for those fields.
      }
      return;
    }
    if (!Utils.isEmpty(satellite.getLinkName())) {
      DvLink link = model.findLink(satellite.getLinkName());
      addLinkFields(names, link, recordSource, model, variables, linkHubSource);
    }
  }

  private static void addReferenceFields(
      List<String> names,
      DvReferenceTable reference,
      DataVaultSource recordSource,
      IVariables variables) {
    if (reference == null || recordSource == null) {
      return;
    }
    String sourceName = resolve(recordSource.getName(), variables);
    for (BusinessKey key : reference.getNaturalKeysForSource(sourceName, variables)) {
      addBusinessKeyFields(names, key, variables);
    }
    if (reference.getAttributes() != null) {
      for (SatelliteAttribute attribute : reference.getAttributes()) {
        if (attribute != null) {
          addField(names, attribute.getName(), variables);
        }
      }
    }
  }

  private static void addBusinessKeyFields(
      List<String> names, BusinessKey key, IVariables variables) {
    if (key == null) {
      return;
    }
    for (String part : key.resolveSourceParts()) {
      addField(names, part, variables);
    }
    addField(names, key.getName(), variables);
  }

  private static DvLink.DvLinkHubSource findLinkHubSource(
      DvLink link, DataVaultSource recordSource, IVariables variables) {
    if (link.getLinkHubSources() == null || recordSource == null) {
      return null;
    }
    String sourceName = resolve(recordSource.getName(), variables);
    if (Utils.isEmpty(sourceName)) {
      return null;
    }
    for (DvLink.DvLinkHubSource hubSource : link.getLinkHubSources()) {
      if (hubSource == null) {
        continue;
      }
      String mapped = resolve(hubSource.getSourceName(), variables);
      if (sourceName.equalsIgnoreCase(mapped)) {
        return hubSource;
      }
    }
    return null;
  }

  private static void addField(List<String> names, String fieldName, IVariables variables) {
    String resolved = resolve(fieldName, variables);
    if (Utils.isEmpty(resolved)) {
      return;
    }
    for (String existing : names) {
      if (resolved.equalsIgnoreCase(existing)) {
        return;
      }
    }
    names.add(resolved);
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
