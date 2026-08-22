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
package org.hopper.edw.datavault.hopgui.file.lineageview;

import org.eclipse.swt.widgets.Event;

/**
 * Widget-relative click coordinates that survive a background facet follow-up. RAP recycles {@link
 * Event} after the current request, so lineage view must not keep the live event.
 */
public record LineageViewClickPoint(int x, int y, int button) {

  public static LineageViewClickPoint of(Event event) {
    if (event == null) {
      return null;
    }
    return new LineageViewClickPoint(event.x, event.y, event.button);
  }

  public Event toEvent() {
    Event event = new Event();
    event.x = x;
    event.y = y;
    event.button = button;
    return event;
  }
}
