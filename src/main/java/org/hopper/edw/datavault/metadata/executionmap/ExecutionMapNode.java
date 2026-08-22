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
package org.hopper.edw.datavault.metadata.executionmap;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.Point;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** A vertex in a Hop execution map graph. */
@Getter
@Setter
public class ExecutionMapNode implements IGuiPosition {

  @HopMetadataProperty private String id;

  @HopMetadataProperty(storeWithCode = true)
  private ExecutionMapNodeType nodeType = ExecutionMapNodeType.WORKFLOW_ACTION;

  @HopMetadataProperty private String name;

  @HopMetadataProperty private String path;

  @HopMetadataProperty private String pluginId;

  @HopMetadataProperty private String parentNodeId;

  @HopMetadataProperty private String snapshotId;

  @HopMetadataProperty(inline = true)
  private Point location = new Point(0, 0);

  @HopMetadataProperty private List<ExecutionMapProperty> properties = new ArrayList<>();

  private boolean selected;

  @Override
  public boolean isSelected() {
    return selected;
  }

  @Override
  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  @Override
  public Point getLocation() {
    if (location == null) {
      location = new Point(0, 0);
    }
    return location;
  }

  @Override
  public void setLocation(Point location) {
    this.location = location != null ? location : new Point(0, 0);
  }

  @Override
  public void setLocation(int x, int y) {
    setLocation(new Point(Math.max(0, x), Math.max(0, y)));
  }

  public void setProperty(String key, String value) {
    if (properties == null) {
      properties = new ArrayList<>();
    }
    for (ExecutionMapProperty property : properties) {
      if (property != null && key != null && key.equals(property.getKey())) {
        property.setValue(value);
        return;
      }
    }
    ExecutionMapProperty property = new ExecutionMapProperty();
    property.setKey(key);
    property.setValue(value);
    properties.add(property);
  }

  public String getProperty(String key) {
    if (properties == null || key == null) {
      return null;
    }
    for (ExecutionMapProperty property : properties) {
      if (property != null && key.equals(property.getKey())) {
        return property.getValue();
      }
    }
    return null;
  }
}
