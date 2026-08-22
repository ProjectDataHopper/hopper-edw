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
package org.hopper.edw.datavault.hopgui.file.vault;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.BusinessKeySource;

/**
 * Dialog helpers for multipartite vs composite hub business-key source field mapping
 * (comma-separated multi-part text, Y/N composite flag).
 */
public final class BusinessKeySourceFieldUiSupport {

  public static final String[] COMPOSITE_YES_NO = new String[] {"N", "Y"};

  private BusinessKeySourceFieldUiSupport() {}

  public static boolean isCompositeYes(String text) {
    if (Utils.isEmpty(text)) {
      return false;
    }
    String t = text.trim();
    return "Y".equalsIgnoreCase(t) || "YES".equalsIgnoreCase(t) || "TRUE".equalsIgnoreCase(t);
  }

  public static String formatCompositeFlag(boolean composite) {
    return composite ? "Y" : "N";
  }

  /** Display string for hub Keys table: single field or comma-separated parts. */
  public static String formatSourceFields(BusinessKey bk) {
    if (bk == null) {
      return "";
    }
    List<String> parts = bk.resolveSourceParts();
    if (parts.isEmpty()) {
      return "";
    }
    if (parts.size() == 1) {
      return parts.get(0);
    }
    return String.join(", ", parts);
  }

  public static String formatSourceFields(BusinessKeySource source) {
    if (source == null) {
      return "";
    }
    List<String> parts = source.resolveSourceParts();
    if (parts.isEmpty()) {
      return "";
    }
    if (parts.size() == 1) {
      return parts.get(0);
    }
    return String.join(", ", parts);
  }

  /** Parse a dialog cell into ordered source field names. Accepts comma or semicolon separators. */
  public static List<String> parseSourceFields(String text) {
    List<String> parts = new ArrayList<>();
    if (Utils.isEmpty(text)) {
      return parts;
    }
    for (String token : text.split("[,;]")) {
      if (token == null) {
        continue;
      }
      String trimmed = token.trim();
      if (!Utils.isEmpty(trimmed)) {
        parts.add(trimmed);
      }
    }
    return parts;
  }

  /** Apply composite flag + source field cell to a {@link BusinessKey}. */
  public static void applyToBusinessKey(
      BusinessKey bk, boolean composite, String sourceFieldsText) {
    if (bk == null) {
      return;
    }
    bk.setComposite(composite);
    List<String> parts = parseSourceFields(sourceFieldsText);
    if (composite) {
      bk.setSourceFieldNames(new ArrayList<>(parts));
      bk.setSourceFieldName(parts.isEmpty() ? null : parts.get(0));
    } else {
      bk.setSourceFieldNames(new ArrayList<>());
      bk.setSourceFieldName(parts.isEmpty() ? null : parts.get(0));
    }
  }

  /**
   * Apply source field cell(s) to a {@link BusinessKeySource}. Multiple parts become {@code
   * sourceFieldNames}; a single part uses legacy {@code sourceFieldName}.
   */
  public static void applyToBusinessKeySource(BusinessKeySource source, String sourceFieldsText) {
    if (source == null) {
      return;
    }
    List<String> parts = parseSourceFields(sourceFieldsText);
    if (parts.size() > 1) {
      source.setSourceFieldNames(new ArrayList<>(parts));
      source.setSourceFieldName(parts.get(0));
    } else if (parts.size() == 1) {
      source.setSourceFieldNames(new ArrayList<>());
      source.setSourceFieldName(parts.get(0));
    } else {
      source.setSourceFieldNames(new ArrayList<>());
      source.setSourceFieldName(null);
    }
  }
}
