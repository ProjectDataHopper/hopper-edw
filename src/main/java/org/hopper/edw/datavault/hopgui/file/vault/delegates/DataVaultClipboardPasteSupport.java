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
package org.hopper.edw.datavault.hopgui.file.vault.delegates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvNote;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.apache.hop.ui.core.PropsUi;

/** Helpers for renaming and offsetting objects pasted onto a Data Vault model canvas. */
final class DataVaultClipboardPasteSupport {

  private DataVaultClipboardPasteSupport() {}

  static void applyLocationOffset(List<IDvTable> tables, List<DvNote> notes, Point pasteLocation) {
    Point min = computeMinimumLocation(tables, notes);
    if (min == null) {
      return;
    }
    Point offset;
    if (pasteLocation != null) {
      offset = new Point(pasteLocation.x - min.x, pasteLocation.y - min.y);
    } else {
      offset = new Point(-min.x, -min.y);
    }
    for (IDvTable table : tables) {
      shiftPosition(table, offset);
    }
    for (DvNote note : notes) {
      shiftPosition(note, offset);
    }
  }

  static Map<String, String> assignUniqueTableNames(
      DataVaultModel model, List<IDvTable> pastedTables) {
    Map<String, String> nameMap = new HashMap<>();
    Set<String> reservedNames = new HashSet<>();
    for (IDvTable table : pastedTables) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      String oldName = table.getName();
      String newName = getUniqueTableName(model, oldName, reservedNames);
      reservedNames.add(newName);
      if (!oldName.equals(newName)) {
        nameMap.put(oldName, newName);
        table.setName(newName);
      }
    }
    return nameMap;
  }

  static void remapNamesInPastedTables(List<IDvTable> pastedTables, Map<String, String> nameMap) {
    if (nameMap == null || nameMap.isEmpty()) {
      return;
    }
    for (IDvTable table : pastedTables) {
      if (table == null || table.getTableType() == null) {
        continue;
      }
      if (table.getTableType() == DvTableType.SATELLITE) {
        remapSatellite((DvSatellite) table, nameMap);
      } else if (table.getTableType() == DvTableType.LINK) {
        remapLink((DvLink) table, nameMap);
      }
    }
  }

  private static void remapSatellite(DvSatellite satellite, Map<String, String> nameMap) {
    satellite.setHubName(remapName(satellite.getHubName(), nameMap));
    satellite.setLinkName(remapName(satellite.getLinkName(), nameMap));
  }

  private static void remapLink(DvLink link, Map<String, String> nameMap) {
    if (link.getHubNames() != null) {
      List<String> remapped = new ArrayList<>();
      for (String hubName : link.getHubNames()) {
        remapped.add(remapName(hubName, nameMap));
      }
      link.setHubNames(remapped);
    }
    if (link.getLinkSatelliteNames() != null) {
      List<String> remapped = new ArrayList<>();
      for (String satelliteName : link.getLinkSatelliteNames()) {
        remapped.add(remapName(satelliteName, nameMap));
      }
      link.setLinkSatelliteNames(remapped);
    }
    if (link.getLinkHubSources() != null) {
      for (DvLink.DvLinkHubSource linkSource : link.getLinkHubSources()) {
        if (linkSource == null || linkSource.getHubSourceKeyFields() == null) {
          continue;
        }
        for (DvLink.HubSourceKeyField field : linkSource.getHubSourceKeyFields()) {
          if (field != null) {
            field.setHubName(remapName(field.getHubName(), nameMap));
          }
        }
      }
    }
    if (link.getLinkSatelliteSources() != null) {
      for (DvLink.DvLinkSatelliteSource linkSource : link.getLinkSatelliteSources()) {
        if (linkSource == null || linkSource.getSatelliteSourceKeyFields() == null) {
          continue;
        }
        for (DvLink.SatelliteSourceKeyField field : linkSource.getSatelliteSourceKeyFields()) {
          if (field != null) {
            field.setSatelliteName(remapName(field.getSatelliteName(), nameMap));
          }
        }
      }
    }
  }

  private static String remapName(String name, Map<String, String> nameMap) {
    if (Utils.isEmpty(name)) {
      return name;
    }
    for (Map.Entry<String, String> entry : nameMap.entrySet()) {
      if (name.equalsIgnoreCase(entry.getKey())) {
        return entry.getValue();
      }
    }
    return name;
  }

  private static String getUniqueTableName(
      DataVaultModel model, String desiredName, Set<String> reservedNames) {
    if (!isNameUsed(model, desiredName, reservedNames)) {
      return desiredName;
    }
    int num = 1;
    String candidate;
    do {
      candidate = desiredName + " " + num;
      num++;
    } while (isNameUsed(model, candidate, reservedNames));
    return candidate;
  }

  private static boolean isNameUsed(DataVaultModel model, String name, Set<String> reservedNames) {
    if (Utils.isEmpty(name)) {
      return false;
    }
    if (reservedNames != null) {
      for (String reserved : reservedNames) {
        if (name.equalsIgnoreCase(reserved)) {
          return true;
        }
      }
    }
    if (model == null) {
      return false;
    }
    for (IDvTable table : model.getTables()) {
      if (table != null && table.getName() != null && table.getName().equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  private static Point computeMinimumLocation(List<IDvTable> tables, List<DvNote> notes) {
    Point min = null;
    if (tables != null) {
      for (IDvTable table : tables) {
        min = includePosition(min, table);
      }
    }
    if (notes != null) {
      for (DvNote note : notes) {
        min = includePosition(min, note);
      }
    }
    return min;
  }

  private static Point includePosition(Point min, IGuiPosition positionable) {
    if (positionable == null || positionable.getLocation() == null) {
      return min;
    }
    Point location = positionable.getLocation();
    if (min == null) {
      return new Point(location.x, location.y);
    }
    min.x = Math.min(min.x, location.x);
    min.y = Math.min(min.y, location.y);
    return min;
  }

  private static void shiftPosition(IGuiPosition positionable, Point offset) {
    if (positionable == null || positionable.getLocation() == null || offset == null) {
      return;
    }
    Point location = positionable.getLocation();
    PropsUi.setLocation(positionable, location.x + offset.x, location.y + offset.y);
  }
}
