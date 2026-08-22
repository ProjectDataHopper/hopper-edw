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
package org.hopper.edw.datavault.lineageview;

import java.util.function.ToIntFunction;
import org.apache.hop.core.util.Utils;

/**
 * Fits a lineage card label into a pixel (or unit) width. The tail of the name is the meaningful
 * part ({@code d_product} in {@code dm/retail-f-order-lines/d_product}), so overflow keeps the end
 * and adds a leading ellipsis. When a {@code /} still fits, the cut snaps to that slash.
 */
public final class LineageViewLabelFit {

  static final String ELLIPSIS = "…";

  private LineageViewLabelFit() {}

  public static String fitTail(String text, int maxWidth, ToIntFunction<String> widthOf) {
    if (Utils.isEmpty(text) || maxWidth <= 0 || widthOf == null) {
      return Utils.isEmpty(text) ? "" : text;
    }
    if (widthOf.applyAsInt(text) <= maxWidth) {
      return text;
    }
    int ellipsisWidth = widthOf.applyAsInt(ELLIPSIS);
    if (ellipsisWidth >= maxWidth) {
      return ELLIPSIS;
    }
    int start = firstFittingStart(text, maxWidth, widthOf);
    if (start < 0) {
      return ELLIPSIS;
    }
    start = snapToSlash(text, start, maxWidth, widthOf);
    return ELLIPSIS + text.substring(start);
  }

  /** Smallest start index such that {@code … + text[start:]} still fits. */
  private static int firstFittingStart(String text, int maxWidth, ToIntFunction<String> widthOf) {
    int lo = 0;
    int hi = text.length();
    int found = -1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      String candidate = ELLIPSIS + text.substring(mid);
      if (widthOf.applyAsInt(candidate) <= maxWidth) {
        found = mid;
        hi = mid - 1;
      } else {
        lo = mid + 1;
      }
    }
    return found;
  }

  /**
   * If a {@code /} at or before {@code start} still fits, start there so the label reads {@code
   * …/retail-f-order-lines/d_product} instead of {@code …ail-f-order-lines/d_product}.
   */
  private static int snapToSlash(
      String text, int start, int maxWidth, ToIntFunction<String> widthOf) {
    int slash = text.lastIndexOf('/', start);
    if (slash < 0 || slash == start) {
      return start;
    }
    String candidate = ELLIPSIS + text.substring(slash);
    if (widthOf.applyAsInt(candidate) <= maxWidth) {
      return slash;
    }
    return start;
  }
}
