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
package org.apache.hop.datavault.command.svg;

import org.apache.hop.core.gui.Point;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BvBvTableReference;
import org.apache.hop.datavault.metadata.businessvault.BvDvTableReference;
import org.apache.hop.datavault.metadata.businessvault.BvTableBase;
import org.apache.hop.datavault.metadata.businessvault.IBvTable;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.dimensional.DmTableBase;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;

/** Computes canvas bounds for DV/BV/dimensional models, optionally excluding notes. */
public final class ModelBoundsSupport {

  private ModelBoundsSupport() {}

  public static Point getMaximum(DataVaultModel model, boolean includeNotes) {
    if (model == null) {
      return new Point(100, 100);
    }
    if (includeNotes) {
      return model.getMaximum();
    }
    int maxx = 0;
    int maxy = 0;
    for (IDvTable table : model.getTables()) {
      Point loc = table.getLocation();
      if (loc == null) {
        continue;
      }
      if (loc.x > maxx) {
        maxx = loc.x;
      }
      if (loc.y > maxy) {
        maxy = loc.y;
      }
    }
    return new Point(maxx + 100, maxy + 100);
  }

  public static Point getMaximum(BusinessVaultModel model, boolean includeNotes) {
    if (model == null) {
      return new Point(200, 200);
    }
    if (includeNotes) {
      return model.getMaximum();
    }
    int maxx = 0;
    int maxy = 0;
    for (IBvTable table : model.getTables()) {
      if (table instanceof BvTableBase base) {
        Point loc = base.getLocation();
        if (loc == null) {
          continue;
        }
        if (loc.x > maxx) {
          maxx = loc.x;
        }
        if (loc.y > maxy) {
          maxy = loc.y;
        }
      }
    }
    for (BvDvTableReference reference : model.getDvReferences()) {
      if (reference == null) {
        continue;
      }
      Point loc = reference.getLocation();
      if (loc == null) {
        continue;
      }
      int refMaxX = loc.x + Math.max(140, reference.getDrawnBoxWidth());
      int refMaxY = loc.y + Math.max(70, reference.getDrawnBoxHeight());
      if (refMaxX > maxx) {
        maxx = refMaxX;
      }
      if (refMaxY > maxy) {
        maxy = refMaxY;
      }
    }
    for (BvBvTableReference reference : model.getBvReferences()) {
      if (reference == null) {
        continue;
      }
      Point loc = reference.getLocation();
      if (loc == null) {
        continue;
      }
      int refMaxX = loc.x + Math.max(140, reference.getDrawnBoxWidth());
      int refMaxY = loc.y + Math.max(70, reference.getDrawnBoxHeight());
      if (refMaxX > maxx) {
        maxx = refMaxX;
      }
      if (refMaxY > maxy) {
        maxy = refMaxY;
      }
    }
    return new Point(maxx + 200, maxy + 200);
  }

  public static Point getMaximum(DimensionalModel model, boolean includeNotes) {
    if (model == null) {
      return new Point(100, 100);
    }
    if (includeNotes) {
      return model.getMaximum();
    }
    int maxx = 0;
    int maxy = 0;
    for (IDmTable table : model.getTables()) {
      if (table instanceof DmTableBase base) {
        Point loc = base.getLocation();
        if (loc == null) {
          continue;
        }
        int boxW = Math.max(140, base.getDrawnBoxWidth());
        int boxH = Math.max(70, base.getDrawnBoxHeight());
        if (loc.x + boxW > maxx) {
          maxx = loc.x + boxW;
        }
        if (loc.y + boxH > maxy) {
          maxy = loc.y + boxH;
        }
      }
    }
    return new Point(maxx + 100, maxy + 100);
  }
}
