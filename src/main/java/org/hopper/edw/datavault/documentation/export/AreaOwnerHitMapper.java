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
package org.hopper.edw.datavault.documentation.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.Rectangle;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.hopper.edw.datavault.documentation.model.SvgHit;
import org.hopper.edw.datavault.documentation.render.DocPaths;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;

/** Maps painter {@link AreaOwner} regions to documentation SVG hits. */
public final class AreaOwnerHitMapper {

  private AreaOwnerHitMapper() {}

  public static List<SvgHit> toHits(List<AreaOwner> areaOwners) {
    return toHits(areaOwners, null);
  }

  public static List<SvgHit> toHits(
      List<AreaOwner> areaOwners, Function<String, String> tableHrefResolver) {
    Map<String, SvgHit> unique = new LinkedHashMap<>();
    if (areaOwners == null) {
      return new ArrayList<>();
    }
    for (AreaOwner owner : areaOwners) {
      SvgHit hit = toHit(owner, tableHrefResolver);
      if (hit == null) {
        continue;
      }
      unique.putIfAbsent(hit.type() + ":" + hit.name(), hit);
    }
    return new ArrayList<>(unique.values());
  }

  private static SvgHit toHit(AreaOwner owner, Function<String, String> tableHrefResolver) {
    if (owner == null || owner.getAreaType() == null || owner.getArea() == null) {
      return null;
    }
    AreaType type = owner.getAreaType();
    Rectangle area = owner.getArea();
    Object subject = owner.getOwner() != null ? owner.getOwner() : owner.getParent();
    if (type == AreaType.TRANSFORM_ICON || type == AreaType.TRANSFORM_NAME) {
      String name = nameOf(subject);
      if (Utils.isEmpty(name) && subject instanceof TransformMeta transformMeta) {
        name = transformMeta.getName();
      }
      if (Utils.isEmpty(name)) {
        return null;
      }
      return hit("transform", name, area, "#" + DocPaths.fragmentId("transform", name));
    }
    if (type == AreaType.ACTION_ICON || type == AreaType.ACTION_NAME) {
      String name = nameOf(subject);
      if (Utils.isEmpty(name) && subject instanceof ActionMeta actionMeta) {
        name = actionMeta.getName();
      }
      if (Utils.isEmpty(name)) {
        return null;
      }
      return hit("action", name, area, "#" + DocPaths.fragmentId("action", name));
    }
    if (type == AreaType.CUSTOM || type == AreaType.TRANSFORM_ICON) {
      if (subject instanceof ExecutionMapNode node && !Utils.isEmpty(node.getName())) {
        return hit("node", node.getName(), area, "#" + DocPaths.fragmentId("node", node.getName()));
      }
    }
    String tableName = nameOf(subject);
    if (!Utils.isEmpty(tableName)
        && (type == AreaType.TRANSFORM_ICON
            || type == AreaType.TRANSFORM_NAME
            || type == AreaType.CUSTOM)) {
      String href =
          tableHrefResolver != null
              ? tableHrefResolver.apply(tableName)
              : "#" + DocPaths.fragmentId("table", tableName);
      if (Utils.isEmpty(href)) {
        href = "#" + DocPaths.fragmentId("table", tableName);
      }
      return hit("table", tableName, area, href);
    }
    return null;
  }

  private static SvgHit hit(String type, String name, Rectangle area, String href) {
    return new SvgHit(type, name, area.x, area.y, area.width, area.height, href);
  }

  private static String nameOf(Object subject) {
    if (subject instanceof IHasName named && !Utils.isEmpty(named.getName())) {
      return named.getName();
    }
    if (subject instanceof TransformMeta transformMeta) {
      return transformMeta.getName();
    }
    if (subject instanceof ActionMeta actionMeta) {
      return actionMeta.getName();
    }
    return null;
  }
}
