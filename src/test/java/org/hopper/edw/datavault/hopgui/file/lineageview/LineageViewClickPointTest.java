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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.Test;

class LineageViewClickPointTest {

  @Test
  void copiesCoordinatesOffTheLiveEvent() {
    Event event = new Event();
    event.x = 40;
    event.y = 80;
    event.button = 1;
    LineageViewClickPoint click = LineageViewClickPoint.of(event);
    event.x = 1;
    event.y = 2;
    event.button = 3;
    assertEquals(40, click.x());
    assertEquals(80, click.y());
    assertEquals(1, click.button());
    Event copy = click.toEvent();
    assertNotSame(event, copy);
    assertEquals(40, copy.x);
    assertEquals(80, copy.y);
    assertEquals(1, copy.button);
  }

  @Test
  void ofNullIsNull() {
    assertNull(LineageViewClickPoint.of(null));
  }
}
