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
package org.hopper.edw.datavault.naming;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.core.widget.NamingSchemeWidgetSupport;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Naming-scheme N indicator + shortcut for hand-built SWT {@link Text} name fields. */
public final class EdwNamingWidgetSupport {

  private EdwNamingWidgetSupport() {}

  /**
   * Enable CTRL-SHIFT-N and place the N indicator at the right edge of {@code fdText}. {@code text}
   * stays a sibling of other dialog controls.
   */
  public static Label enableAndLayout(
      Text text, IVariables variables, String namingSchemeType, FormData fdText) {
    Label indicator = NamingSchemeWidgetSupport.enableOnText(text, variables, namingSchemeType);
    NamingSchemeWidgetSupport.layoutWithIndicator(text, indicator, fdText);
    return indicator;
  }
}
