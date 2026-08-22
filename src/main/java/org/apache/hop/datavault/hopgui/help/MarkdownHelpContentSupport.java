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
package org.apache.hop.datavault.hopgui.help;

import java.util.regex.Pattern;
import org.apache.hop.core.util.Utils;

/** Normalizes classpath markdown help topics for display. */
public final class MarkdownHelpContentSupport {

  /** Leading HTML license comment (i-Bridge Apache-2.0 or historical ASF boilerplate). */
  private static final Pattern LEADING_LICENSE_COMMENT =
      Pattern.compile("(?s)^\\s*<!--(?:(?!-->).)*?Apache License(?:(?!-->).)*?-->\\s*");

  private MarkdownHelpContentSupport() {}

  public static String stripLicenseHeader(String markdown) {
    if (Utils.isEmpty(markdown)) {
      return markdown;
    }
    return LEADING_LICENSE_COMMENT.matcher(markdown).replaceFirst("");
  }
}
