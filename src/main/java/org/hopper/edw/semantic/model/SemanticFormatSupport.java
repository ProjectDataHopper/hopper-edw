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
package org.hopper.edw.semantic.model;

import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;

/** Default Hop conversion masks for semantic attributes and measures. */
public final class SemanticFormatSupport {

  public static final String NUMBER_MASK = "#,##0.00";
  public static final String INTEGER_MASK = "#,##0";
  public static final String DATE_MASK = "yyyy-MM-dd";

  private SemanticFormatSupport() {}

  public static String defaultMask(int hopType) {
    return switch (hopType) {
      case IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_BIGNUMBER -> NUMBER_MASK;
      case IValueMeta.TYPE_INTEGER -> INTEGER_MASK;
      case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> DATE_MASK;
      default -> "";
    };
  }

  public static String resolveMask(String explicit, int hopType) {
    if (!Utils.isEmpty(explicit)) {
      return explicit;
    }
    return defaultMask(hopType);
  }
}
