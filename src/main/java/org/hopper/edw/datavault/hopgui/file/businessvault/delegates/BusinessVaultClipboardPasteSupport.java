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
package org.hopper.edw.datavault.hopgui.file.businessvault.delegates;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.ui.core.PropsUi;
import org.hopper.edw.datavault.metadata.DvNote;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;

/** Helpers for renaming and offsetting objects pasted onto a Business Vault model canvas. */
final class BusinessVaultClipboardPasteSupport {

  private BusinessVaultClipboardPasteSupport() {}

  static void applyLocationOffset(List<IBvTable> tables, List<DvNote> notes, Point pasteLocation) {
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
    for (IBvTable table : tables) {
      shiftPosition(table, offset);
    }
    for (DvNote note : notes) {
      shiftPosition(note, offset);
    }
  }

  static Map<String, String> assignUniqueTableNames(
      BusinessVaultModel model, List<IBvTable> pastedTables) {
    Map<String, String> nameMap = new HashMap<>();
    Set<String> reservedNames = new HashSet<>();
    for (IBvTable table : pastedTables) {
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

  private static String getUniqueTableName(
      BusinessVaultModel model, String desiredName, Set<String> reservedNames) {
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

  private static boolean isNameUsed(
      BusinessVaultModel model, String name, Set<String> reservedNames) {
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
    return model.findTable(name) != null;
  }

  private static Point computeMinimumLocation(List<IBvTable> tables, List<DvNote> notes) {
    Point min = null;
    if (tables != null) {
      for (IBvTable table : tables) {
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
