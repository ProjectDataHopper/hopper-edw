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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvLinkedTable;
import org.apache.hop.datavault.metadata.DvReferenceLoadMode;
import org.apache.hop.datavault.metadata.DvReferenceTable;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableResolutionSupport;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;

/** Resolves Data Vault table display metadata for model graph painters. */
public final class DvTableDisplaySupport {

  private DvTableDisplaySupport() {}

  public static String getImagePath(DvTableType type) {
    if (type == null) {
      return "datavault-model.svg";
    }
    return switch (type) {
      case HUB -> "datavault-hub.svg";
      case LINK -> "datavault-link.svg";
      case SATELLITE -> "datavault-satellite.svg";
      case REFERENCE -> "datavault-reference.svg";
      case LINKED_TABLE, TABLE_REFERENCE -> "datavault-model.svg";
    };
  }

  /**
   * Secondary text under the table name: natural-key summary for reference tables, otherwise the
   * hash key field name when enabled.
   */
  public static String getSecondaryFieldLineForDisplay(
      IDvTable table,
      DataVaultModel model,
      Map<String, IDvTable> tableByName,
      IVariables variables,
      boolean showHashKeyFieldNames) {
    if (table instanceof DvReferenceTable referenceTable) {
      return getNaturalKeysSummary(referenceTable);
    }
    if (!showHashKeyFieldNames) {
      return null;
    }
    return getHashKeyFieldNameForDisplay(table, model, tableByName, variables);
  }

  /** Short load-mode badge for canvas cards (e.g. {@code full} / {@code Δ}). */
  public static String getReferenceLoadModeBadge(DvReferenceTable table) {
    if (table == null) {
      return null;
    }
    DvReferenceLoadMode mode =
        table.getLoadMode() != null ? table.getLoadMode() : DvReferenceLoadMode.FULL_REPLACE;
    return switch (mode) {
      case FULL_REPLACE -> "full";
      case DELETE_INSERT -> "Δ keys";
      case MERGE -> "merge";
    };
  }

  public static String getNaturalKeysSummary(DvReferenceTable table) {
    if (table == null || Utils.isEmpty(table.getNaturalKeys())) {
      return null;
    }
    String summary =
        table.getNaturalKeys().stream()
            .filter(k -> k != null && !Utils.isEmpty(k.getName()))
            .map(BusinessKey::getName)
            .collect(Collectors.joining(", "));
    return Utils.isEmpty(summary) ? null : summary;
  }

  public static String getImagePathForTable(IDvTable table) {
    if (table instanceof DvLinkedTable reference && reference.getReferencedTableType() != null) {
      return getImagePath(reference.getReferencedTableType());
    }
    return getImagePath(table != null ? table.getTableType() : null);
  }

  public static String getHashKeyFieldNameForDisplay(
      IDvTable table, DataVaultModel model, IVariables variables) {
    if (table == null) {
      return null;
    }
    Map<String, IDvTable> tableByName = buildTableIndex(model);
    return getHashKeyFieldNameForDisplay(table, model, tableByName, variables);
  }

  public static String getHashKeyFieldNameForDisplay(
      IDvTable table,
      DataVaultModel model,
      Map<String, IDvTable> tableByName,
      IVariables variables) {
    if (table == null || table.getTableType() == null) {
      return null;
    }

    String hashKeyFieldName = null;
    switch (table.getTableType()) {
      case HUB -> {
        DvHub hub = (DvHub) table;
        hashKeyFieldName = hub.getHashKeyFieldName();
        if (Utils.isEmpty(hashKeyFieldName) && !Utils.isEmpty(hub.getBusinessKeys())) {
          BusinessKey firstKey = hub.getBusinessKeys().get(0);
          if (firstKey != null && !Utils.isEmpty(firstKey.getName())) {
            hashKeyFieldName = firstKey.getName() + "_HK";
          }
        }
        if (Utils.isEmpty(hashKeyFieldName)) {
          hashKeyFieldName = "hashkey_HK";
        }
      }
      case LINK -> {
        DvLink link = (DvLink) table;
        hashKeyFieldName = link.resolveLinkHashKeyFieldName();
      }
      case SATELLITE -> {
        DvSatellite satellite = (DvSatellite) table;
        if (!Utils.isEmpty(satellite.getHubName()) && model != null) {
          DvHub linkedHub = model.findHub(satellite.getHubName());
          if (linkedHub != null) {
            hashKeyFieldName = linkedHub.getHashKeyFieldName();
            if (Utils.isEmpty(hashKeyFieldName) && !Utils.isEmpty(linkedHub.getBusinessKeys())) {
              BusinessKey firstKey = linkedHub.getBusinessKeys().get(0);
              if (firstKey != null && !Utils.isEmpty(firstKey.getName())) {
                hashKeyFieldName = firstKey.getName() + "_HK";
              }
            }
          }
        } else if (!Utils.isEmpty(satellite.getLinkName()) && tableByName != null) {
          IDvTable linkedTable = tableByName.get(satellite.getLinkName());
          if (linkedTable instanceof DvLink linkedLink) {
            hashKeyFieldName = linkedLink.resolveLinkHashKeyFieldName();
          }
        }
        if (Utils.isEmpty(hashKeyFieldName)) {
          hashKeyFieldName = "hashkey";
        }
      }
      case REFERENCE -> {
        // Reference tables have natural keys, not hash keys.
        return null;
      }
      case LINKED_TABLE, TABLE_REFERENCE -> {
        if (table instanceof DvLinkedTable reference) {
          // Role-playing hub aliases: show the role hash column used on links.
          if (reference.getReferencedTableType() == DvTableType.HUB
              && !Utils.isEmpty(reference.getHashKeyFieldName())) {
            hashKeyFieldName = reference.getHashKeyFieldName();
            break;
          }
          if (reference.getReferencedTableType() == DvTableType.HUB
              && !Utils.isEmpty(reference.getName())
              && !reference
                  .getName()
                  .equalsIgnoreCase(Const.NVL(reference.getReferencedTableName(), ""))) {
            hashKeyFieldName =
                DvTableResolutionSupport.deriveRoleHashKeyFieldName(reference.getName());
            break;
          }
          IDvTable target =
              DvTableResolutionSupport.resolveReferenceTarget(model, reference, variables, null);
          if (target != null) {
            return getHashKeyFieldNameForDisplay(target, model, tableByName, variables);
          }
        }
        return null;
      }
      default -> {
        return null;
      }
    }

    return variables != null ? variables.resolve(hashKeyFieldName) : hashKeyFieldName;
  }

  private static Map<String, IDvTable> buildTableIndex(DataVaultModel model) {
    Map<String, IDvTable> tableByName = new HashMap<>();
    if (model == null || model.getTables() == null) {
      return tableByName;
    }
    for (IDvTable indexedTable : model.getTables()) {
      if (indexedTable != null && !Utils.isEmpty(indexedTable.getName())) {
        tableByName.put(indexedTable.getName(), indexedTable);
      }
    }
    return tableByName;
  }
}
