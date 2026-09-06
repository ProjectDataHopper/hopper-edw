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
package org.hopper.edw.datavault.documentation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Sidebar / index link. {@code href} is site-root relative (no leading slash). {@code treePath} is
 * the folder path under the object type (project folders, parent model, metadata virtual path).
 */
public record NavItem(
    DocObjectKind kind, String title, String href, String description, List<String> treePath) {

  public NavItem {
    if (treePath == null || treePath.isEmpty()) {
      treePath = List.of();
    } else {
      List<String> cleaned = new ArrayList<>(treePath.size());
      for (String segment : treePath) {
        if (segment != null && !segment.isEmpty()) {
          cleaned.add(segment);
        }
      }
      treePath = List.copyOf(cleaned);
    }
  }

  public NavItem(DocObjectKind kind, String title, String href) {
    this(kind, title, href, null, List.of());
  }

  public NavItem(DocObjectKind kind, String title, String href, String description) {
    this(kind, title, href, description, List.of());
  }

  /** Title including folder segments, for flat listings such as the overview page. */
  public String pathTitle() {
    if (treePath.isEmpty()) {
      return title == null ? "" : title;
    }
    StringBuilder out = new StringBuilder();
    for (String segment : treePath) {
      if (out.length() > 0) {
        out.append(" / ");
      }
      out.append(segment);
    }
    if (title != null && !title.isEmpty()) {
      out.append(" / ").append(title);
    }
    return out.toString();
  }
}
