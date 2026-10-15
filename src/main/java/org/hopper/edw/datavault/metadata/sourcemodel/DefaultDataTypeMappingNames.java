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
package org.hopper.edw.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;

/** Comma- or semicolon-separated data type mapping profile names on a source model configuration. */
public final class DefaultDataTypeMappingNames {

  /** What the Edit button does for the names currently in the field. */
  public enum EditAction {
    /** The field names one profile. */
    EDIT_ONE,
    /** The field names more than one profile. Ask which one to open. */
    ASK,
    /** The field is empty. Create a profile and add its name. */
    CREATE
  }

  private DefaultDataTypeMappingNames() {}

  public static List<String> parse(String raw) {
    List<String> names = new ArrayList<>();
    if (Utils.isEmpty(raw)) {
      return names;
    }
    for (String part : raw.split("[,;]")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        names.add(trimmed);
      }
    }
    return names;
  }

  public static String format(List<String> names) {
    if (names == null || names.isEmpty()) {
      return "";
    }
    return String.join(", ", names);
  }

  public static EditAction editAction(List<String> selected) {
    if (selected == null || selected.isEmpty()) {
      return EditAction.CREATE;
    }
    if (selected.size() == 1) {
      return EditAction.EDIT_ONE;
    }
    return EditAction.ASK;
  }

  /** Append {@code name} when it is not already listed. */
  public static List<String> add(List<String> names, String name) {
    List<String> result = names == null ? new ArrayList<>() : new ArrayList<>(names);
    if (!Utils.isEmpty(name) && !result.contains(name)) {
      result.add(name);
    }
    return result;
  }

  /**
   * Replace {@code previous} with {@code updated}. A name that is not in the list is left alone, so
   * editing a profile that is not a default does not attach it.
   */
  public static List<String> rename(List<String> names, String previous, String updated) {
    if (names == null || names.isEmpty() || Utils.isEmpty(previous) || Utils.isEmpty(updated)) {
      return names == null ? new ArrayList<>() : new ArrayList<>(names);
    }
    List<String> result = new ArrayList<>();
    if (previous.equals(updated)) {
      result.addAll(names);
      return result;
    }
    boolean replaced = false;
    for (String name : names) {
      if (previous.equals(name)) {
        if (!replaced && !result.contains(updated)) {
          result.add(updated);
          replaced = true;
        }
      } else if (!result.contains(name)) {
        result.add(name);
      }
    }
    return result;
  }
}
