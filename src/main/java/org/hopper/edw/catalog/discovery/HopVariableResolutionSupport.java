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
package org.hopper.edw.catalog.discovery;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/** Helpers for resolving Hop variables and detecting unresolved placeholders. */
public final class HopVariableResolutionSupport {

  private static final Class<?> PKG = HopVariableResolutionSupport.class;

  private static final Pattern UNRESOLVED_VARIABLE = Pattern.compile("\\$\\{([^}]+)\\}");

  private HopVariableResolutionSupport() {}

  public static String resolve(IVariables variables, String value) {
    return variables != null ? variables.resolve(value) : value;
  }

  public static boolean containsUnresolvedVariables(String value) {
    return value != null && UNRESOLVED_VARIABLE.matcher(value).find();
  }

  public static void requireResolved(IVariables variables, String value, String settingLabel)
      throws HopException {
    String resolved = resolve(variables, value);
    if (containsUnresolvedVariables(resolved)) {
      Matcher matcher = UNRESOLVED_VARIABLE.matcher(resolved);
      String variableName = matcher.find() ? matcher.group(1) : resolved;
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "HopVariableResolutionSupport.Error.UnresolvedVariable",
              settingLabel,
              variableName));
    }
  }
}
