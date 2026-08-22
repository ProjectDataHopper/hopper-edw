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
package org.apache.hop.datavault.hopgui.perspective.journey;

import java.util.List;
import org.apache.hop.core.util.Utils;

/** Picks which resource definition group name the journey combo should show. */
public final class EdwJourneyGroupSelection {

  private EdwJourneyGroupSelection() {}

  /**
   * Preference: current combo text, then last-used, then the first name. Matches ignore case but
   * returns the actual metadata name.
   */
  public static String resolve(List<String> names, String current, String lastUsed) {
    if (names == null || names.isEmpty()) {
      return "";
    }
    String hit = match(names, current);
    if (hit != null) {
      return hit;
    }
    hit = match(names, lastUsed);
    if (hit != null) {
      return hit;
    }
    return names.get(0);
  }

  private static String match(List<String> names, String wanted) {
    if (Utils.isEmpty(wanted)) {
      return null;
    }
    for (String name : names) {
      if (wanted.equals(name)) {
        return name;
      }
    }
    for (String name : names) {
      if (name != null && wanted.equalsIgnoreCase(name)) {
        return name;
      }
    }
    return null;
  }
}
