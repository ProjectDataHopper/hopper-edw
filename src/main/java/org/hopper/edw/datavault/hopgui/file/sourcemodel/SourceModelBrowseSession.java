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
package org.hopper.edw.datavault.hopgui.file.sourcemodel;

import java.util.ArrayDeque;
import java.util.Deque;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;

/** Browse view state for one source-model canvas. It does not change saved coordinates. */
public final class SourceModelBrowseSession {

  public record Focus(SourceEndpointKind kind, String name) {}

  private boolean active;
  private SourceEndpointKind focusKind = SourceEndpointKind.TABLE;
  private String focusName;
  private int depth = 1;
  private final Deque<Focus> back = new ArrayDeque<>();
  private SourceModelBrowseLayout.Result view;

  public boolean isActive() {
    return active && view != null;
  }

  public SourceModelBrowseLayout.Result view() {
    return isActive() ? view : null;
  }

  public int getDepth() {
    return depth;
  }

  public boolean canGoBack() {
    return active && !back.isEmpty();
  }

  public Focus focus() {
    return new Focus(focusKind, focusName);
  }

  public void enter(SourceModel model, SourceEndpointKind kind, String name) {
    back.clear();
    depth = 1;
    focusKind = kind != null ? kind : SourceEndpointKind.TABLE;
    focusName = name;
    active = true;
    rebuild(model);
  }

  public void walk(SourceModel model, SourceEndpointKind kind, String name) {
    if (!active) {
      enter(model, kind, name);
      return;
    }
    SourceEndpointKind nextKind = kind != null ? kind : SourceEndpointKind.TABLE;
    if (nextKind == focusKind && name != null && name.equals(focusName)) {
      return;
    }
    back.push(new Focus(focusKind, focusName));
    focusKind = nextKind;
    focusName = name;
    rebuild(model);
  }

  public boolean back(SourceModel model) {
    if (!active || back.isEmpty()) {
      return false;
    }
    Focus previous = back.pop();
    focusKind = previous.kind();
    focusName = previous.name();
    rebuild(model);
    return true;
  }

  public void setDepth(SourceModel model, int depth) {
    this.depth = depth >= 2 ? 2 : 1;
    if (active) {
      rebuild(model);
    }
  }

  public void exit() {
    active = false;
    view = null;
    back.clear();
  }

  public void rebuild(SourceModel model) {
    view = SourceModelBrowseLayout.layout(model, focusKind, focusName, depth);
  }
}
