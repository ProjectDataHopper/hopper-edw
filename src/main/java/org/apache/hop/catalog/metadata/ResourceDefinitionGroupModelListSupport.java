/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.catalog.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.util.Utils;

/**
 * Pure helpers for resource definition group model path lists: filter matching and merging a
 * filtered table edit back into the full authoritative list.
 */
public final class ResourceDefinitionGroupModelListSupport {

  private ResourceDefinitionGroupModelListSupport() {}

  public static boolean matchesFilter(String path, String filter) {
    if (Utils.isEmpty(filter)) {
      return true;
    }
    if (Utils.isEmpty(path)) {
      return false;
    }
    return path.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT).trim());
  }

  public static List<String> filterPaths(List<String> paths, String filter) {
    List<String> result = new ArrayList<>();
    if (paths == null) {
      return result;
    }
    if (Utils.isEmpty(filter)) {
      result.addAll(paths);
      return result;
    }
    for (String path : paths) {
      if (matchesFilter(path, filter)) {
        result.add(path);
      }
    }
    return result;
  }

  /**
   * Merges table content after a filtered edit back into the full list.
   *
   * <p>When {@code filter} is empty, returns {@code tableContent} as the new full list. When a
   * filter is active, non-matching paths keep their relative order; matching paths are replaced as
   * a block by {@code tableContent} at the position of the first previous match.
   */
  public static List<String> mergeFilteredTableEdit(
      List<String> fullList, String filter, List<String> tableContent) {
    List<String> table = tableContent != null ? tableContent : List.of();
    if (Utils.isEmpty(filter)) {
      return new ArrayList<>(table);
    }
    List<String> full = fullList != null ? fullList : List.of();
    List<String> result = new ArrayList<>();
    boolean inserted = false;
    for (String path : full) {
      if (matchesFilter(path, filter)) {
        if (!inserted) {
          result.addAll(table);
          inserted = true;
        }
      } else {
        result.add(path);
      }
    }
    if (!inserted) {
      result.addAll(table);
    }
    return result;
  }
}
