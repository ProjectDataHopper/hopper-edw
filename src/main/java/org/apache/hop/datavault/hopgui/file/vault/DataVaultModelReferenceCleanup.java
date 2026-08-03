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
package org.apache.hop.datavault.hopgui.file.vault;

import java.util.Iterator;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvLink;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;

/** Clears name-based cross-references when a table is removed from a {@link DataVaultModel}. */
final class DataVaultModelReferenceCleanup {

  private DataVaultModelReferenceCleanup() {}

  static void cleanupAfterTableRemoved(DataVaultModel model, IDvTable removedTable) {
    if (model == null || removedTable == null || Utils.isEmpty(removedTable.getName())) {
      return;
    }
    String removedName = removedTable.getName();
    DvTableType removedType = removedTable.getTableType();
    if (removedType == null) {
      return;
    }
    switch (removedType) {
      case HUB -> cleanupReferencesToHub(model, removedName);
      case LINK -> cleanupReferencesToLink(model, removedName);
      case SATELLITE -> cleanupReferencesToSatellite(model, removedName);
      default -> {
        // nothing
      }
    }
  }

  private static void cleanupReferencesToHub(DataVaultModel model, String hubName) {
    forEachSatellite(
        model,
        satellite -> {
          if (namesEqual(hubName, satellite.getHubName())) {
            satellite.setHubName(null);
            satellite.setChanged();
          }
        });
    forEachLink(
        model,
        link -> {
          boolean changed = removeNameFromList(link.getHubNames(), hubName);
          changed |= removeHubSourceKeyFields(link, hubName);
          if (changed) {
            link.setChanged();
          }
        });
  }

  private static void cleanupReferencesToLink(DataVaultModel model, String linkName) {
    forEachSatellite(
        model,
        satellite -> {
          if (namesEqual(linkName, satellite.getLinkName())) {
            satellite.setLinkName(null);
            satellite.setChanged();
          }
        });
  }

  private static void cleanupReferencesToSatellite(DataVaultModel model, String satelliteName) {
    forEachLink(
        model,
        link -> {
          boolean changed = removeNameFromList(link.getLinkSatelliteNames(), satelliteName);
          changed |= removeSatelliteSourceKeyFields(link, satelliteName);
          if (changed) {
            link.setChanged();
          }
        });
  }

  private static boolean removeHubSourceKeyFields(DvLink link, String hubName) {
    if (link.getLinkHubSources() == null) {
      return false;
    }
    boolean changed = false;
    for (DvLink.DvLinkHubSource linkSource : link.getLinkHubSources()) {
      if (linkSource == null || linkSource.getHubSourceKeyFields() == null) {
        continue;
      }
      Iterator<DvLink.HubSourceKeyField> iterator = linkSource.getHubSourceKeyFields().iterator();
      while (iterator.hasNext()) {
        DvLink.HubSourceKeyField field = iterator.next();
        if (field != null && namesEqual(hubName, field.getHubName())) {
          iterator.remove();
          changed = true;
        }
      }
    }
    return changed;
  }

  private static boolean removeSatelliteSourceKeyFields(DvLink link, String satelliteName) {
    if (link.getLinkSatelliteSources() == null) {
      return false;
    }
    boolean changed = false;
    for (DvLink.DvLinkSatelliteSource linkSource : link.getLinkSatelliteSources()) {
      if (linkSource == null || linkSource.getSatelliteSourceKeyFields() == null) {
        continue;
      }
      Iterator<DvLink.SatelliteSourceKeyField> iterator =
          linkSource.getSatelliteSourceKeyFields().iterator();
      while (iterator.hasNext()) {
        DvLink.SatelliteSourceKeyField field = iterator.next();
        if (field != null && namesEqual(satelliteName, field.getSatelliteName())) {
          iterator.remove();
          changed = true;
        }
      }
    }
    return changed;
  }

  private static boolean removeNameFromList(List<String> names, String nameToRemove) {
    if (names == null || names.isEmpty()) {
      return false;
    }
    return names.removeIf(name -> namesEqual(nameToRemove, name));
  }

  private static void forEachSatellite(
      DataVaultModel model, java.util.function.Consumer<DvSatellite> action) {
    for (IDvTable table : model.getTables()) {
      if (table != null && table.getTableType() == DvTableType.SATELLITE) {
        action.accept((DvSatellite) table);
      }
    }
  }

  private static void forEachLink(
      DataVaultModel model, java.util.function.Consumer<DvLink> action) {
    for (IDvTable table : model.getTables()) {
      if (table != null && table.getTableType() == DvTableType.LINK) {
        action.accept((DvLink) table);
      }
    }
  }

  private static boolean namesEqual(String left, String right) {
    return left != null && right != null && left.equalsIgnoreCase(right);
  }
}
